// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.InvocationId;
import dev.restate.sdk.common.Request;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.*;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class InvocationStateMachine implements InvocationFlow.InvocationProcessor {

  private static final Logger LOG = LogManager.getLogger(InvocationStateMachine.class);

  private final String serviceName;
  private final String fullyQualifiedHandlerName;
  private final Span span;
  private final RestateEndpoint.LoggingContextSetter loggingContextSetter;

  private volatile InvocationState invocationState = InvocationState.WAITING_START;

  // Used for the side effect guard
  private volatile boolean insideSideEffect = false;

  // Obtained after WAITING_START
  private ByteString id;
  private String debugId;
  private String key;
  private int entriesToReplay;
  private UserStateStore userStateStore;

  // Those values track the progress in the journal
  private int currentJournalEntryIndex = -1;
  private String currentJournalEntryName = null;
  private MessageType currentJournalEntryType = null;

  // Buffering of messages and completions
  private final IncomingEntriesStateMachine incomingEntriesStateMachine;
  private final SideEffectAckStateMachine sideEffectAckStateMachine;
  private final ReadyResultStateMachine readyResultStateMachine;

  // Flow sub/pub
  private Flow.Subscriber<? super MessageLite> outputSubscriber;
  private Flow.Subscription inputSubscription;
  private final CallbackHandle<SyscallCallback<Request>> afterStartCallback;

  InvocationStateMachine(
      String serviceName,
      String fullyQualifiedHandlerName,
      Span span,
      RestateEndpoint.LoggingContextSetter loggingContextSetter) {
    this.serviceName = serviceName;
    this.fullyQualifiedHandlerName = fullyQualifiedHandlerName;
    this.span = span;
    this.loggingContextSetter = loggingContextSetter;

    this.incomingEntriesStateMachine = new IncomingEntriesStateMachine();
    this.readyResultStateMachine = new ReadyResultStateMachine();
    this.sideEffectAckStateMachine = new SideEffectAckStateMachine();

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
  public void onNext(InvocationFlow.InvocationInput invocationInput) {
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
      this.sideEffectAckStateMachine.tryHandleSideEffectAck(
          ((Protocol.EntryAckMessage) msg).getEntryIndex());
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
    this.sideEffectAckStateMachine.abort(AbortedExecutionException.INSTANCE);
  }

  // --- Init routine to wait for the start message

  void startAndConsumeInput(SyscallCallback<Request> afterStartCallback) {
    this.afterStartCallback.set(afterStartCallback);
    this.inputSubscription.request(1);
  }

  void onStartMessage(MessageLite msg) {
    if (!(msg instanceof Protocol.StartMessage)) {
      this.fail(ProtocolException.unexpectedMessage(Protocol.StartMessage.class, msg));
      return;
    }

    // Unpack the StartMessage
    Protocol.StartMessage startMessage = (Protocol.StartMessage) msg;
    this.id = startMessage.getId();
    this.debugId = startMessage.getDebugId();
    InvocationId invocationId = new InvocationIdImpl(startMessage.getDebugId());
    this.key = startMessage.getKey();
    this.entriesToReplay = startMessage.getKnownEntries();

    // Set up the state cache
    this.userStateStore =
        new UserStateStore(
            startMessage.getPartialState(),
            startMessage.getStateMapList().stream()
                .collect(
                    Collectors.toMap(
                        Protocol.StartMessage.StateEntry::getKey,
                        Protocol.StartMessage.StateEntry::getValue)));

    // Tracing and logging setup
    this.loggingContextSetter.set(
        RestateEndpoint.LoggingContextSetter.INVOCATION_ID_KEY, startMessage.getDebugId());
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
          if (!(inputMsg instanceof Protocol.InputEntryMessage)) {
            throw ProtocolException.unexpectedMessage(Protocol.InputEntryMessage.class, inputMsg);
          }
          Protocol.InputEntryMessage inputEntry = (Protocol.InputEntryMessage) inputMsg;

          Request request =
              new Request(
                  invocationId,
                  Context.root().with(span),
                  inputEntry.getValue(),
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
    LOG.info("End invocation");
    this.closeWithMessage(Protocol.EndMessage.getDefaultInstance(), ProtocolException.CLOSED);
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
    LOG.warn("Invocation failed", cause);
    this.closeWithMessage(
        Util.toErrorMessage(
            cause,
            this.currentJournalEntryIndex,
            this.currentJournalEntryName,
            this.currentJournalEntryType),
        cause);
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
      this.sideEffectAckStateMachine.abort(cause);
      this.incomingEntriesStateMachine.abort(cause);
      this.span.end();
    }
  }

  // --- Methods to implement Syscalls

  @SuppressWarnings("unchecked")
  <E extends MessageLite, T> void processCompletableJournalEntry(
      E expectedEntryMessage,
      Entries.CompletableJournalEntry<E, T> journalEntry,
      SyscallCallback<Deferred<T>> callback) {
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
                  DeferredResults.completedSingle(
                      this.currentJournalEntryIndex, readyResultInternal));
            } else {
              // Entry is not completed yet
              this.readyResultStateMachine.offerCompletionParser(
                  this.currentJournalEntryIndex,
                  completionMessage -> {
                    journalEntry.updateUserStateStorageWithCompletion(
                        (E) actualEntryMessage, completionMessage, this.userStateStore);
                    return journalEntry.parseCompletionResult(completionMessage);
                  });
              callback.onSuccess(DeferredResults.single(this.currentJournalEntryIndex));
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
            DeferredResults.completedSingle(
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
        callback.onSuccess(DeferredResults.single(this.currentJournalEntryIndex));
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
            Util.assertEntryClass(Protocol.RunEntryMessage.class, msg);

            // We have a result already, complete the callback
            completeSideEffectCallbackWithEntry((Protocol.RunEntryMessage) msg, callback);
          },
          callback::onCancel);
    } else if (this.invocationState == InvocationState.PROCESSING) {
      insideSideEffect = true;
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
      this.sideEffectAckStateMachine.registerExecutedSideEffect(this.currentJournalEntryIndex);
      this.writeEntry(sideEffectEntry);

      // Wait for entry to be acked
      Protocol.RunEntryMessage finalSideEffectEntry = sideEffectEntry;
      this.sideEffectAckStateMachine.waitLastSideEffectAck(
          new SideEffectAckStateMachine.SideEffectAckCallback() {
            @Override
            public void onLastSideEffectAck() {
              completeSideEffectCallbackWithEntry(finalSideEffectEntry, callback);
            }

            @Override
            public void onSuspend() {
              suspend(List.of(sideEffectAckStateMachine.getLastExecutedSideEffect()));
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

  void completeSideEffectCallbackWithEntry(
      Protocol.RunEntryMessage sideEffectEntry, ExitSideEffectSyscallCallback callback) {
    if (sideEffectEntry.hasFailure()) {
      callback.onFailure(Util.toRestateException(sideEffectEntry.getFailure()));
    } else {
      callback.onSuccess(sideEffectEntry.getValue());
    }
  }

  // --- Deferred

  <T> void resolveDeferred(Deferred<T> deferredToResolve, SyscallCallback<Void> callback) {
    if (deferredToResolve.isCompleted()) {
      callback.onSuccess(null);
      return;
    }

    if (deferredToResolve instanceof DeferredResults.ResolvableSingleDeferred) {
      this.resolveSingleDeferred(
          (DeferredResults.ResolvableSingleDeferred<T>) deferredToResolve, callback);
      return;
    }

    if (deferredToResolve instanceof DeferredResults.CombinatorDeferred) {
      this.resolveCombinatorDeferred(
          (DeferredResults.CombinatorDeferred<T>) deferredToResolve, callback);
      return;
    }

    throw new IllegalArgumentException("Unexpected deferred class " + deferredToResolve.getClass());
  }

  <T> void resolveSingleDeferred(
      DeferredResults.ResolvableSingleDeferred<T> deferred, SyscallCallback<Void> callback) {
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
   * DeferredResults.ResolvableSingleDeferred}, created as result of completable syscalls.
   *
   * <p>The idea of the algorithm is the following: {@code rootDeferred} is the root of this tree,
   * and has internal state that can be mutated through {@link
   * DeferredResults.CombinatorDeferred#tryResolve(int)} to flag the tree as resolved. Every time a
   * new leaf is resolved through {@link DeferredResults.ResolvableSingleDeferred#resolve(Result)},
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
   *   <li>In case there are no {@link DeferredResults.SingleDeferredInternal
   *       SingleDeferredResultInternals}, it means every leaf has been resolved beforehand. In this
   *       case, we must be able to flag this combinator tree as resolved as well.
   * </ul>
   */
  private void resolveCombinatorDeferred(
      DeferredResults.CombinatorDeferred<?> rootDeferred, SyscallCallback<Void> callback) {
    // Calling .await() on a combinator deferred within a side effect is not allowed
    //  as resolving it creates or read a journal entry.
    checkInsideSideEffectGuard();
    this.nextJournalEntry(null, MessageType.CombinatorAwaitableEntryMessage);

    if (Objects.equals(this.invocationState, InvocationState.REPLAYING)) {
      // Retrieve the CombinatorAwaitableEntryMessage
      this.readEntry(
          actualMsg -> {
            Util.assertEntryClass(Java.CombinatorAwaitableEntryMessage.class, actualMsg);

            if (!rootDeferred.tryResolve(
                ((Java.CombinatorAwaitableEntryMessage) actualMsg).getEntryIndexList())) {
              throw new IllegalStateException("Combinator message cannot be resolved.");
            }
            callback.onSuccess(null);
          },
          callback::onCancel);
    } else if (this.invocationState == InvocationState.PROCESSING) {
      // Create map of singles to resolve
      Map<Integer, DeferredResults.ResolvableSingleDeferred<?>> resolvableSingles = new HashMap<>();

      Set<DeferredResults.SingleDeferredInternal<?>> unprocessedLeafs =
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

        writeCombinatorEntry(Collections.emptyList());
        callback.onSuccess(null);
        return;
      }

      List<Integer> resolvedOrder = new ArrayList<>();

      // Walk the tree and populate the resolvable singles, and keep the already known ready results
      for (DeferredResults.SingleDeferredInternal<?> singleDeferred : unprocessedLeafs) {
        int entryIndex = singleDeferred.entryIndex();
        if (singleDeferred.isCompleted()) {
          resolvedOrder.add(entryIndex);

          // Try to resolve the combinator now
          if (rootDeferred.tryResolve(entryIndex)) {
            writeCombinatorEntry(resolvedOrder);
            callback.onSuccess(null);
            return;
          }
        } else {
          // If not completed, then it's a ResolvableSingleDeferredResult
          resolvableSingles.put(
              entryIndex, (DeferredResults.ResolvableSingleDeferred<?>) singleDeferred);
        }
      }

      // Not completed yet, we need to wait on the ReadyResultPublisher
      this.readyResultStateMachine.onNewReadyResult(
          new ReadyResultStateMachine.OnNewReadyResultCallback() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public boolean onNewResult(Map<Integer, Result<?>> resultMap) {
              Iterator<Map.Entry<Integer, DeferredResults.ResolvableSingleDeferred<?>>> it =
                  resolvableSingles.entrySet().iterator();
              while (it.hasNext()) {
                Map.Entry<Integer, DeferredResults.ResolvableSingleDeferred<?>> entry = it.next();
                int entryIndex = entry.getKey();

                Result<?> result = resultMap.remove(entryIndex);
                if (result != null) {
                  resolvedOrder.add(entryIndex);
                  entry.getValue().resolve((Result) result);
                  it.remove();

                  // Try to resolve the combinator now
                  if (rootDeferred.tryResolve(entryIndex)) {
                    writeCombinatorEntry(resolvedOrder);
                    callback.onSuccess(null);
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

  private void writeCombinatorEntry(List<Integer> resolvedList) {
    // Create and write the entry
    Java.CombinatorAwaitableEntryMessage entry =
        Java.CombinatorAwaitableEntryMessage.newBuilder().addAllEntryIndex(resolvedList).build();
    span.addEvent("Combinator");
    writeEntry(entry);
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
        RestateEndpoint.LoggingContextSetter.INVOCATION_STATUS_KEY, newInvocationState.toString());
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
