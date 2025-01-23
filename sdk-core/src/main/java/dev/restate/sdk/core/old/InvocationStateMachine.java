// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.old;

import static dev.restate.sdk.core.ExceptionUtils.durationMin;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.*;
import dev.restate.sdk.core.statemachine.InvocationInput;
import dev.restate.sdk.core.statemachine.InvocationState;
import dev.restate.sdk.core.statemachine.MessageType;
import dev.restate.sdk.core.statemachine.UserStateStore;
import dev.restate.sdk.definition.AsyncResult;
import dev.restate.sdk.types.*;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

class InvocationStateMachine implements Flow.Processor<InvocationInput, MessageLite> {

  private static final Logger LOG = LogManager.getLogger(InvocationStateMachine.class);

  private final String serviceName;
  private final String fullyQualifiedHandlerName;
  private final Span span;
  private final EndpointRequestHandler.LoggingContextSetter loggingContextSetter;
  private final Protocol.ServiceProtocolVersion negotiatedProtocolVersion;

  private volatile InvocationState invocationState = InvocationState.WAITING_START;

  // Used for the side effect guard
  private Long sideEffectStart;
  private volatile boolean insideSideEffect = false;

  // Obtained after WAITING_START
  private ByteString id;
  private String debugId;
  private String key;
  private int entriesToReplay;
  private UserStateStore userStateStore;

  // Used inside syscalls.shouldRetry, which doesn't run on the syscalls executor sometimes
  private Duration startMessageDurationSinceLastStoredEntry;
  private int startMessageRetryCountSinceLastStoredEntry;

  // Those values track the progress in the journal
  private int currentJournalEntryIndex = -1;
  private String currentJournalEntryName = null;
  private MessageType currentJournalEntryType = null;

  // Buffering of messages and completions
  private final IncomingEntriesStateMachine incomingEntriesStateMachine;
  private final AckStateMachine ackStateMachine;
  private final ReadyResultStateMachine readyResultStateMachine;

  // Flow sub/pub
  private Flow.Subscriber<? super MessageLite> outputSubscriber;
  private Flow.Subscription inputSubscription;
  private final CallbackHandle<SyscallCallback<Request>> afterStartCallback;

  InvocationStateMachine(
      String serviceName,
      String fullyQualifiedHandlerName,
      Span span,
      EndpointRequestHandler.LoggingContextSetter loggingContextSetter,
      Protocol.ServiceProtocolVersion negotiatedProtocolVersion) {
    this.serviceName = serviceName;
    this.fullyQualifiedHandlerName = fullyQualifiedHandlerName;
    this.span = span;
    this.loggingContextSetter = loggingContextSetter;
    this.negotiatedProtocolVersion = negotiatedProtocolVersion;

    this.incomingEntriesStateMachine = new IncomingEntriesStateMachine();
    this.readyResultStateMachine = new ReadyResultStateMachine();
    this.ackStateMachine = new AckStateMachine();

    this.afterStartCallback = new CallbackHandle<>();
  }

  // --- Getters

  public String getServiceName() {
    return serviceName;
  }

  public ByteString id() {
    return id;
  }

  public String objectKey() {
    return key;
  }

  public InvocationState getInvocationState() {
    return this.invocationState;
  }

  public boolean isInsideSideEffect() {
    return this.insideSideEffect;
  }

  public String getFullyQualifiedHandlerName() {
    return this.fullyQualifiedHandlerName;
  }

  // --- Output Publisher impl

  @Override
  public void subscribe(Flow.Subscriber<? super MessageLite> subscriber) {
    this.outputSubscriber = subscriber;
    this.outputSubscriber.onSubscribe(
        new Flow.Subscription() {
          @Override
          public void request(long l) {}

          @Override
          public void cancel() {
            end();
          }
        });
  }

  // --- Input Subscriber impl

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.inputSubscription = subscription;
  }

  @Override
  public void onNext(InvocationInput invocationInput) {
    MessageLite msg = invocationInput.message();
    LOG.trace("Received input message {} {}", msg.getClass(), msg);
    if (this.invocationState == InvocationState.WAITING_START) {
      this.onStartMessage(msg);
    } else if (msg instanceof Protocol.CompletionMessage) {
      // We check the instance rather than the state, because the user code might still be
      // replaying, but the network layer is already past it and is receiving completions from the
      // runtime.
      this.readyResultStateMachine.offerCompletion((Protocol.CompletionMessage) msg);
    } else if (msg instanceof Protocol.EntryAckMessage) {
      this.ackStateMachine.tryHandleAck(((Protocol.EntryAckMessage) msg).getEntryIndex());
    } else {
      this.incomingEntriesStateMachine.offer(msg);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    LOG.trace("Got failure from input publisher", throwable);
    this.fail(throwable);
  }

  @Override
  public void onComplete() {
    LOG.trace("Input publisher closed");
    this.readyResultStateMachine.abort(AbortedExecutionException.INSTANCE);
    this.ackStateMachine.abort(AbortedExecutionException.INSTANCE);
  }

  // --- Init routine to wait for the start message

  CompletableFuture<Request> waitForReady() {
    // TODO
    this.afterStartCallback.set(afterStartCallback);
    this.inputSubscription.request(1);
    return null;
  }

  void startAndConsumeInput(SyscallCallback<Request> afterStartCallback) {
    this.afterStartCallback.set(afterStartCallback);
    this.inputSubscription.request(1);
  }

  void onStartMessage(MessageLite msg) {
    if (!(msg instanceof Protocol.StartMessage startMessage)) {
      this.fail(ProtocolException.unexpectedMessage(Protocol.StartMessage.class, msg));
      return;
    }

    // Unpack the StartMessage
    this.id = startMessage.getId();
    this.debugId = startMessage.getDebugId();
    InvocationId invocationId = new InvocationIdImpl(startMessage.getDebugId());
    this.key = startMessage.getKey();
    this.entriesToReplay = startMessage.getKnownEntries();
    this.startMessageDurationSinceLastStoredEntry =
        Duration.ofMillis(startMessage.getDurationSinceLastStoredEntry());
    this.startMessageRetryCountSinceLastStoredEntry =
        startMessage.getRetryCountSinceLastStoredEntry();

    // Set up the state cache
    this.userStateStore = new UserStateStore(startMessage);

    // Tracing and logging setup
    this.loggingContextSetter.set(
        EndpointImpl.LoggingContextSetter.INVOCATION_ID_KEY, startMessage.getDebugId());
    if (this.span.isRecording()) {
      span.addEvent(
          "Start", Attributes.of(Tracing.RESTATE_INVOCATION_ID, startMessage.getDebugId()));
    }
    LOG.info("Start invocation");

    // Execute state transition
    this.transitionState(InvocationState.REPLAYING);
    if (this.entriesToReplay == 0) {
      this.fail(
          new ProtocolException(
              "Expected at least one entry with Input, got " + this.entriesToReplay + " entries",
              TerminalException.INTERNAL_SERVER_ERROR_CODE,
              null));
      return;
    }

    this.inputSubscription.request(Long.MAX_VALUE);

    // Now wait input entry
    this.nextJournalEntry(null, MessageType.InputEntryMessage);
    this.readEntry(
        inputMsg -> {
          if (!(inputMsg instanceof Protocol.InputEntryMessage inputEntry)) {
            throw ProtocolException.unexpectedMessage(Protocol.InputEntryMessage.class, inputMsg);
          }

          Request request =
              new Request(
                  invocationId,
                  Context.root().with(span),
                  inputEntry.getValue().asReadOnlyByteBuffer(),
                  inputEntry.getHeadersList().stream()
                      .collect(
                          Collectors.toUnmodifiableMap(
                              Protocol.Header::getKey, Protocol.Header::getValue)));

          this.afterStartCallback.consume(cb -> cb.onSuccess(request));
        },
        this::fail);
  }

  // --- Close state machine

  void end() {
    if (this.invocationState != InvocationState.CLOSED) {
      LOG.info("End invocation");
      this.closeWithMessage(Protocol.EndMessage.getDefaultInstance(), ProtocolException.CLOSED);
    }
  }

  void suspend(Collection<Integer> suspensionIndexes) {
    assert !suspensionIndexes.isEmpty()
        : "Suspension indexes MUST be a non-empty collection, per protocol specification";
    LOG.info("Suspend invocation");
    this.closeWithMessage(
        Protocol.SuspensionMessage.newBuilder().addAllEntryIndexes(suspensionIndexes).build(),
        ProtocolException.CLOSED);
  }

  void fail(Throwable cause) {
    if (this.invocationState != InvocationState.CLOSED) {
      LOG.warn("Invocation failed", cause);
      this.closeWithMessage(
          ExceptionUtils.toErrorMessage(
              cause,
              this.currentJournalEntryIndex,
              this.currentJournalEntryName,
              this.currentJournalEntryType),
          cause);
    }
  }

  void failWithNextRetryDelay(Throwable cause, Duration nextRetryDelay) {
    if (this.invocationState != InvocationState.CLOSED) {
      LOG.warn("Invocation failed, will retry in {}", nextRetryDelay, cause);
      this.closeWithMessage(
          ExceptionUtils.toErrorMessage(
                  cause,
                  this.currentJournalEntryIndex,
                  this.currentJournalEntryName,
                  this.currentJournalEntryType)
              .toBuilder()
              .setNextRetryDelay(nextRetryDelay.toMillis())
              .build(),
          cause);
    }
  }

  private void closeWithMessage(MessageLite closeMessage, Throwable cause) {
    if (this.invocationState != InvocationState.CLOSED) {
      this.transitionState(InvocationState.CLOSED);

      // Cancel inputSubscription and complete outputSubscriber
      if (inputSubscription != null) {
        this.inputSubscription.cancel();
      }
      if (this.outputSubscriber != null) {
        this.outputSubscriber.onNext(closeMessage);
        this.outputSubscriber.onComplete();
        this.outputSubscriber = null;
      }

      // Unblock any eventual waiting callbacks
      this.afterStartCallback.consume(cb -> cb.onCancel(cause));
      this.readyResultStateMachine.abort(cause);
      this.ackStateMachine.abort(cause);
      this.incomingEntriesStateMachine.abort(cause);
      this.span.end();
    }
  }

  // --- Methods to implement Syscalls

  @SuppressWarnings("unchecked")
  <E extends MessageLite, T> void processCompletableJournalEntry(
      E expectedEntryMessage,
      Entries.CompletableJournalEntry<E, T> journalEntry,
      SyscallCallback<AsyncResult<T>> callback) {
    checkInsideSideEffectGuard();
    this.nextJournalEntry(
        journalEntry.getName(expectedEntryMessage), MessageType.fromMessage(expectedEntryMessage));

    if (this.invocationState == InvocationState.CLOSED) {
      callback.onCancel(AbortedExecutionException.INSTANCE);
    } else if (this.invocationState == InvocationState.REPLAYING) {
      // Retrieve the entry
      this.readEntry(
          actualEntryMessage -> {
            journalEntry.checkEntryHeader(expectedEntryMessage, actualEntryMessage);

            if (journalEntry.hasResult((E) actualEntryMessage)) {
              // Entry is already completed
              journalEntry.updateUserStateStoreWithEntry(
                  (E) actualEntryMessage, this.userStateStore);
              Result<T> readyResultInternal = journalEntry.parseEntryResult((E) actualEntryMessage);
              callback.onSuccess(
                  AsyncResults.completedSingle(this.currentJournalEntryIndex, readyResultInternal));
            } else {
              // Entry is not completed yet
              this.readyResultStateMachine.offerCompletionParser(
                  this.currentJournalEntryIndex,
                  completionMessage -> {
                    journalEntry.updateUserStateStorageWithCompletion(
                        (E) actualEntryMessage, completionMessage, this.userStateStore);
                    return journalEntry.parseCompletionResult(completionMessage);
                  });
              callback.onSuccess(AsyncResults.single(this.currentJournalEntryIndex));
            }
          },
          callback::onCancel);
    } else if (this.invocationState == InvocationState.PROCESSING) {
      // Try complete with local storage
      E entryToWrite =
          journalEntry.tryCompleteWithUserStateStorage(expectedEntryMessage, userStateStore);

      if (span.isRecording()) {
        journalEntry.trace(entryToWrite, span);
      }

      // Write out the input entry
      this.writeEntry(entryToWrite);

      if (journalEntry.hasResult(entryToWrite)) {
        // Complete it with the result, as we already have it
        callback.onSuccess(
            AsyncResults.completedSingle(
                this.currentJournalEntryIndex, journalEntry.parseEntryResult(entryToWrite)));
      } else {
        // Register the completion parser
        this.readyResultStateMachine.offerCompletionParser(
            this.currentJournalEntryIndex,
            completionMessage -> {
              journalEntry.updateUserStateStorageWithCompletion(
                  entryToWrite, completionMessage, this.userStateStore);
              return journalEntry.parseCompletionResult(completionMessage);
            });

        // Call the onSuccess
        callback.onSuccess(AsyncResults.single(this.currentJournalEntryIndex));
      }
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  @SuppressWarnings("unchecked")
  <E extends MessageLite> void processJournalEntry(
      E expectedEntryMessage,
      Entries.JournalEntry<E> journalEntry,
      SyscallCallback<Void> callback) {
    checkInsideSideEffectGuard();
    this.nextJournalEntry(
        journalEntry.getName(expectedEntryMessage), MessageType.fromMessage(expectedEntryMessage));

    if (this.invocationState == InvocationState.CLOSED) {
      callback.onCancel(AbortedExecutionException.INSTANCE);
    } else if (this.invocationState == InvocationState.REPLAYING) {
      // Retrieve the entry
      this.readEntry(
          actualEntryMessage -> {
            journalEntry.checkEntryHeader(expectedEntryMessage, actualEntryMessage);
            journalEntry.updateUserStateStoreWithEntry((E) actualEntryMessage, this.userStateStore);
            callback.onSuccess(null);
          },
          callback::onCancel);
    } else if (this.invocationState == InvocationState.PROCESSING) {
      if (span.isRecording()) {
        journalEntry.trace(expectedEntryMessage, span);
      }

      // Write new entry
      this.writeEntry(expectedEntryMessage);

      // Update local storage
      journalEntry.updateUserStateStoreWithEntry(expectedEntryMessage, this.userStateStore);

      // Invoke the ok callback
      callback.onSuccess(null);
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  void enterSideEffectBlock(String name, EnterSideEffectSyscallCallback callback) {
    checkInsideSideEffectGuard();
    this.nextJournalEntry(name, MessageType.RunEntryMessage);

    if (this.invocationState == InvocationState.CLOSED) {
      callback.onCancel(AbortedExecutionException.INSTANCE);
    } else if (this.invocationState == InvocationState.REPLAYING) {
      // Retrieve the entry
      this.readEntry(
          msg -> {
            ExceptionUtils.assertEntryClass(Protocol.RunEntryMessage.class, msg);

            // We have a result already, complete the callback
            completeSideEffectCallbackWithEntry((Protocol.RunEntryMessage) msg, callback);
          },
          callback::onCancel);
    } else if (this.invocationState == InvocationState.PROCESSING) {
      insideSideEffect = true;
      sideEffectStart = System.currentTimeMillis();
      if (span.isRecording()) {
        span.addEvent("Enter SideEffect");
      }
      callback.onNotExecuted();
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  void exitSideEffectBlock(
      Protocol.RunEntryMessage sideEffectEntry, ExitSideEffectSyscallCallback callback) {
    this.insideSideEffect = false;
    this.sideEffectStart = null;
    if (this.invocationState == InvocationState.CLOSED) {
      callback.onCancel(AbortedExecutionException.INSTANCE);
    } else if (this.invocationState == InvocationState.REPLAYING) {
      throw new IllegalStateException(
          "exitSideEffect has been invoked when the state machine is in replaying mode. "
              + "This is probably an SDK bug and might be caused by a missing enterSideEffectBlock invocation before exitSideEffectBlock.");
    } else if (this.invocationState == InvocationState.PROCESSING) {
      if (span.isRecording()) {
        span.addEvent("Exit SideEffect");
      }

      // For side effects, let's write out the name too, if available
      if (this.currentJournalEntryName != null) {
        sideEffectEntry = sideEffectEntry.toBuilder().setName(this.currentJournalEntryName).build();
      }

      // Write new entry
      this.ackStateMachine.registerEntryToAck(this.currentJournalEntryIndex);
      this.writeEntry(sideEffectEntry);

      // Wait for entry to be acked
      Protocol.RunEntryMessage finalSideEffectEntry = sideEffectEntry;
      this.ackStateMachine.waitLastAck(
          new AckStateMachine.AckCallback() {
            @Override
            public void onAck() {
              completeSideEffectCallbackWithEntry(finalSideEffectEntry, callback);
            }

            @Override
            public void onSuspend() {
              suspend(List.of(ackStateMachine.getLastEntryToAck()));
              callback.onCancel(AbortedExecutionException.INSTANCE);
            }

            @Override
            public void onError(Throwable e) {
              callback.onCancel(e);
            }
          });
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  void exitSideEffectBlockWithThrowable(
      Throwable runException,
      @Nullable RetryPolicy retryPolicy,
      ExitSideEffectSyscallCallback callback)
      throws Throwable {
    TerminalException toWrite;
    if (runException instanceof TerminalException) {
      LOG.trace("The run completed with a terminal exception");
      toWrite = (TerminalException) runException;
    } else {
      toWrite = this.rethrowOrConvertToTerminal(retryPolicy, runException);
    }

    LOG.trace("exitSideEffectBlock with exception");
    this.exitSideEffectBlock(
        Protocol.RunEntryMessage.newBuilder()
            .setFailure(ExceptionUtils.toProtocolFailure(toWrite))
            .build(),
        callback);
  }

  private Duration getDurationSinceLastStoredEntry() {
    // We need to check if this is the first entry we try to commit after replay, and only in this
    // case we need to return the info we got from the start message
    //
    // Moreover, when the retry count is == 0, the durationSinceLastStoredEntry might not be zero.
    // In fact, in that case the duration is the interval between the previously stored entry and
    // the time to start/resume the invocation.
    // For the sake of entry retries though, we're not interested in that time elapsed, so we 0 it
    // here for simplicity of the downstream consumer (the retry policy).
    return this.currentJournalEntryIndex == this.entriesToReplay
            && startMessageRetryCountSinceLastStoredEntry > 0
        ? this.startMessageDurationSinceLastStoredEntry
        : Duration.ZERO;
  }

  private int getRetryCountSinceLastStoredEntry() {
    // We need to check if this is the first entry we try to commit after replay, and only in this
    // case we need to return the info we got from the start message
    return this.currentJournalEntryIndex == this.entriesToReplay
        ? this.startMessageRetryCountSinceLastStoredEntry
        : 0;
  }

  // This function rethrows the exception if a retry needs to happen.
  private TerminalException rethrowOrConvertToTerminal(
      @Nullable RetryPolicy retryPolicy, Throwable t) throws Throwable {
    if (retryPolicy != null
        && this.negotiatedProtocolVersion.getNumber()
            < Protocol.ServiceProtocolVersion.V2.getNumber()) {
      throw ProtocolException.unsupportedFeature(
          this.negotiatedProtocolVersion, "run retry policy");
    }

    if (retryPolicy == null) {
      LOG.trace("The run completed with an exception and no retry policy was provided");
      // Default behavior is always retry
      throw t;
    }

    Duration retryLoopDuration =
        this.getDurationSinceLastStoredEntry()
            .plus(Duration.between(Instant.ofEpochMilli(this.sideEffectStart), Instant.now()));
    int retryCount = this.getRetryCountSinceLastStoredEntry() + 1;

    if ((retryPolicy.getMaxAttempts() != null && retryPolicy.getMaxAttempts() <= retryCount)
        || (retryPolicy.getMaxDuration() != null
            && retryPolicy.getMaxDuration().compareTo(retryLoopDuration) <= 0)) {
      LOG.trace("The run completed with a retryable exception, but all attempts were exhausted");
      // We need to convert it to TerminalException
      return new TerminalException(t.toString());
    }

    // Compute next retry delay and throw it!
    Duration nextComputedDelay =
        retryPolicy
            .getInitialDelay()
            .multipliedBy((long) Math.pow(retryPolicy.getExponentiationFactor(), retryCount));
    Duration nextRetryDelay =
        retryPolicy.getMaxDelay() != null
            ? durationMin(retryPolicy.getMaxDelay(), nextComputedDelay)
            : nextComputedDelay;

    this.failWithNextRetryDelay(t, nextRetryDelay);
    throw t;
  }

  void completeSideEffectCallbackWithEntry(
      Protocol.RunEntryMessage sideEffectEntry, ExitSideEffectSyscallCallback callback) {
    if (sideEffectEntry.hasFailure()) {
      callback.onFailure(ExceptionUtils.toRestateException(sideEffectEntry.getFailure()));
    } else {
      callback.onSuccess(sideEffectEntry.getValue().asReadOnlyByteBuffer());
    }
  }

  // --- Deferred

  <T> void resolveDeferred(AsyncResult<T> asyncResultToResolve, SyscallCallback<Void> callback) {
    if (asyncResultToResolve.isCompleted()) {
      callback.onSuccess(null);
      return;
    }

    if (asyncResultToResolve instanceof AsyncResults.ResolvableSingleAsyncResult) {
      this.resolveSingleDeferred(
          (AsyncResults.ResolvableSingleAsyncResult<T>) asyncResultToResolve, callback);
      return;
    }

    if (asyncResultToResolve instanceof AsyncResults.CombinatorAsyncResult) {
      this.resolveCombinatorDeferred(
          (AsyncResults.CombinatorAsyncResult<T>) asyncResultToResolve, callback);
      return;
    }

    throw new IllegalArgumentException(
        "Unexpected deferred class " + asyncResultToResolve.getClass());
  }

  <T> void resolveSingleDeferred(
      AsyncResults.ResolvableSingleAsyncResult<T> deferred, SyscallCallback<Void> callback) {
    this.readyResultStateMachine.onNewReadyResult(
        new ReadyResultStateMachine.OnNewReadyResultCallback() {
          @SuppressWarnings("unchecked")
          @Override
          public boolean onNewResult(Map<Integer, Result<?>> resultMap) {
            Result<T> resolved = (Result<T>) resultMap.remove(deferred.entryIndex());
            if (resolved != null) {
              deferred.resolve(resolved);
              callback.onSuccess(null);
              return true;
            }
            return false;
          }

          @Override
          public void onSuspend() {
            suspend(List.of(deferred.entryIndex()));
            callback.onCancel(AbortedExecutionException.INSTANCE);
          }

          @Override
          public void onError(Throwable e) {
            callback.onCancel(e);
          }
        });
  }

  /**
   * This method implements the algorithm to resolve deferred combinator trees, where inner nodes of
   * the tree are ANY or ALL combinators, and leafs are {@link
   * AsyncResults.ResolvableSingleAsyncResult}, created as result of completable syscalls.
   *
   * <p>The idea of the algorithm is the following: {@code rootDeferred} is the root of this tree,
   * and has internal state that can be mutated through {@link
   * AsyncResults.CombinatorAsyncResult#tryResolve(int)} to flag the tree as resolved. Every time a
   * new leaf is resolved through {@link AsyncResults.ResolvableSingleAsyncResult#resolve(Result)},
   * we try to resolve the tree again. We start by checking if we have enough resolved leafs in the
   * combinator tree to resolve it. If not, we register a callback to the {@link
   * ReadyResultStateMachine} to wait on future completions. As soon as the tree is resolved, we
   * record in the journal the order of the leafs we've seen so far, and we finish by calling the
   * {@code callback}, giving back control to user code.
   *
   * <p>An important property of this algorithm is that we don't write multiple {@link
   * Java.CombinatorAwaitableEntryMessage} per combinator nodes composing the tree, but we write one
   * of them for the whole tree. Moreover, we write only when we resolve the combinator tree, and
   * not beforehand when the user creates the combinator tree. The main reason for this property is
   * that the Restate protocol doesn't allow the SDK to mutate Journal Entries after they're sent to
   * the runtime, and the index of entries is enforced by their send order, meaning you cannot send
   * entry 2 and then entry 1. The consequence of this property is that any algorithm recording
   * combinator nodes one-by-one would require non-trivial replay logic, in order to handle the
   * resolution order of the combinator nodes, and partially resolved trees (e.g. in case a
   * suspension happens while we have recorded only a part of the combinator nodes).
   *
   * <p>There are some special cases:
   *
   * <ul>
   *   <li>In case of replay, we don't need to wait for any leaf to be resolved, because we write
   *       the combinator journal entry only when there is a subset of resolved leafs which
   *       completes the combinator tree. Moreover, the leaf journal entries precede the combinator
   *       entry because they are created first.
   *   <li>In case there are no {@link AsyncResults.SingleAsyncResultInternal
   *       SingleDeferredResultInternals}, it means every leaf has been resolved beforehand. In this
   *       case, we must be able to flag this combinator tree as resolved as well.
   * </ul>
   */
  private void resolveCombinatorDeferred(
      AsyncResults.CombinatorAsyncResult<?> rootDeferred, SyscallCallback<Void> callback) {
    // Calling .await() on a combinator deferred within a side effect is not allowed
    //  as resolving it creates or read a journal entry.
    checkInsideSideEffectGuard();
    this.nextJournalEntry(null, MessageType.CombinatorAwaitableEntryMessage);

    if (Objects.equals(this.invocationState, InvocationState.REPLAYING)) {
      // Retrieve the CombinatorAwaitableEntryMessage
      this.readEntry(
          actualMsg -> {
            ExceptionUtils.assertEntryClass(Java.CombinatorAwaitableEntryMessage.class, actualMsg);

            if (!rootDeferred.tryResolve(
                ((Java.CombinatorAwaitableEntryMessage) actualMsg).getEntryIndexList())) {
              throw new IllegalStateException("Combinator message cannot be resolved.");
            }
            callback.onSuccess(null);
          },
          callback::onCancel);
    } else if (this.invocationState == InvocationState.PROCESSING) {
      // Create map of singles to resolve
      Map<Integer, AsyncResults.ResolvableSingleAsyncResult<?>> resolvableSingles = new HashMap<>();

      Set<AsyncResults.SingleAsyncResultInternal<?>> unprocessedLeafs =
          rootDeferred.unprocessedLeafs().collect(Collectors.toSet());

      // If there are no leafs, it means the combinator must be resolvable
      if (unprocessedLeafs.isEmpty()) {
        // We don't need to provide a valid entry index,
        // we just need to walk through the tree and mark all the combinators as completed.
        if (!rootDeferred.tryResolve(-1)) {
          throw new IllegalStateException(
              "Combinator cannot be resolved, but every children have been resolved already. "
                  + "This is a symptom of an SDK bug, please contact the developers.");
        }

        writeCombinatorEntry(Collections.emptyList(), callback);
        return;
      }

      List<Integer> resolvedOrder = new ArrayList<>();

      // Walk the tree and populate the resolvable singles, and keep the already known ready results
      for (AsyncResults.SingleAsyncResultInternal<?> singleDeferred : unprocessedLeafs) {
        int entryIndex = singleDeferred.entryIndex();
        if (singleDeferred.isCompleted()) {
          resolvedOrder.add(entryIndex);

          // Try to resolve the combinator now
          if (rootDeferred.tryResolve(entryIndex)) {
            writeCombinatorEntry(resolvedOrder, callback);
            return;
          }
        } else {
          // If not completed, then it's a ResolvableSingleDeferredResult
          resolvableSingles.put(
              entryIndex, (AsyncResults.ResolvableSingleAsyncResult<?>) singleDeferred);
        }
      }

      // Not completed yet, we need to wait on the ReadyResultPublisher
      this.readyResultStateMachine.onNewReadyResult(
          new ReadyResultStateMachine.OnNewReadyResultCallback() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public boolean onNewResult(Map<Integer, Result<?>> resultMap) {
              Iterator<Map.Entry<Integer, AsyncResults.ResolvableSingleAsyncResult<?>>> it =
                  resolvableSingles.entrySet().iterator();
              while (it.hasNext()) {
                Map.Entry<Integer, AsyncResults.ResolvableSingleAsyncResult<?>> entry = it.next();
                int entryIndex = entry.getKey();

                Result<?> result = resultMap.remove(entryIndex);
                if (result != null) {
                  resolvedOrder.add(entryIndex);
                  entry.getValue().resolve((Result) result);
                  it.remove();

                  // Try to resolve the combinator now
                  if (rootDeferred.tryResolve(entryIndex)) {
                    writeCombinatorEntry(resolvedOrder, callback);
                    return true;
                  }
                }
              }

              return false;
            }

            @Override
            public void onSuspend() {
              suspend(resolvableSingles.keySet());
              callback.onCancel(AbortedExecutionException.INSTANCE);
            }

            @Override
            public void onError(Throwable e) {
              callback.onCancel(e);
            }
          });
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  private void writeCombinatorEntry(List<Integer> resolvedList, SyscallCallback<Void> callback) {
    // Create and write the entry
    Java.CombinatorAwaitableEntryMessage entry =
        Java.CombinatorAwaitableEntryMessage.newBuilder().addAllEntryIndex(resolvedList).build();
    span.addEvent("Combinator");

    // We register the combinator entry to wait for an ack
    this.ackStateMachine.registerEntryToAck(this.currentJournalEntryIndex);
    writeEntry(entry);

    // Let's wait for the ack
    this.ackStateMachine.waitLastAck(
        new AckStateMachine.AckCallback() {
          @Override
          public void onAck() {
            callback.onSuccess(null);
          }

          @Override
          public void onSuspend() {
            suspend(List.of(ackStateMachine.getLastEntryToAck()));
            callback.onCancel(AbortedExecutionException.INSTANCE);
          }

          @Override
          public void onError(Throwable e) {
            callback.onCancel(e);
          }
        });
  }

  // --- Internal callback

  private void transitionState(InvocationState newInvocationState) {
    if (this.invocationState == InvocationState.CLOSED) {
      // Cannot move out of the closed state
      return;
    }
    LOG.debug("Transitioning state machine to {}", newInvocationState);
    this.invocationState = newInvocationState;
    this.loggingContextSetter.set(
        EndpointImpl.LoggingContextSetter.INVOCATION_STATUS_KEY, newInvocationState.toString());
  }

  private void tryTransitionProcessing() {
    if (currentJournalEntryIndex == entriesToReplay - 1
        && this.invocationState == InvocationState.REPLAYING) {
      if (!this.incomingEntriesStateMachine.isEmpty()) {
        throw new IllegalStateException("Entries queue should be empty at this point");
      }
      this.transitionState(InvocationState.PROCESSING);
    }
  }

  private void nextJournalEntry(String entryName, MessageType entryType) {
    this.currentJournalEntryIndex++;
    this.currentJournalEntryName = entryName;
    this.currentJournalEntryType = entryType;

    LOG.debug(
        "Current journal entry [{}]({}): {}",
        this.currentJournalEntryIndex,
        this.currentJournalEntryName,
        this.currentJournalEntryType);
  }

  private void checkInsideSideEffectGuard() {
    if (this.insideSideEffect) {
      throw ProtocolException.invalidSideEffectCall();
    }
  }

  void readEntry(Consumer<MessageLite> msgCallback, Consumer<Throwable> errorCallback) {
    this.incomingEntriesStateMachine.read(
        new IncomingEntriesStateMachine.OnEntryCallback() {
          @Override
          public void onEntry(MessageLite msg) {
            tryTransitionProcessing();
            msgCallback.accept(msg);
          }

          @Override
          public void onSuspend() {
            // This is not expected to happen, so we treat this case as closed
            errorCallback.accept(ProtocolException.CLOSED);
          }

          @Override
          public void onError(Throwable e) {
            errorCallback.accept(e);
          }
        });
  }

  private void writeEntry(MessageLite message) {
    LOG.trace("Writing to output message {} {}", message.getClass(), message);
    Objects.requireNonNull(this.outputSubscriber).onNext(message);
  }

  @Override
  public String toString() {
    return "InvocationStateMachine[" + debugId + ']';
  }
}
