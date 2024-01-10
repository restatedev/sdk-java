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
import com.google.rpc.Code;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.InvocationId;
import dev.restate.sdk.common.syscalls.DeferredResult;
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class InvocationStateMachine implements InvocationFlow.InvocationProcessor {

  private static final Logger LOG = LogManager.getLogger(InvocationStateMachine.class);

  private final String serviceName;
  private final String fullyQualifiedMethodName;
  private final Span span;
  private final Consumer<InvocationState> transitionStateObserver;

  private volatile InvocationState invocationState = InvocationState.WAITING_START;

  // Used for the side effect guard
  private volatile boolean insideSideEffect = false;

  // Obtained after WAITING_START
  private ByteString id;
  private String debugId;
  private int entriesToReplay;
  private UserStateStore userStateStore;

  // Index tracking progress in the journal
  private int currentJournalIndex;

  // Buffering of messages and completions
  private final IncomingEntriesStateMachine incomingEntriesStateMachine;
  private final SideEffectAckStateMachine sideEffectAckStateMachine;
  private final ReadyResultStateMachine readyResultStateMachine;

  // Flow sub/pub
  private Flow.Subscriber<? super MessageLite> outputSubscriber;
  private Flow.Subscription inputSubscription;
  private final CallbackHandle<Consumer<InvocationId>> afterStartCallback;

  InvocationStateMachine(
      String serviceName,
      String fullyQualifiedMethodName,
      Span span,
      Consumer<InvocationState> transitionStateObserver) {
    this.serviceName = serviceName;
    this.fullyQualifiedMethodName = fullyQualifiedMethodName;
    this.span = span;
    this.transitionStateObserver = transitionStateObserver;

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

  public String debugId() {
    return debugId;
  }

  public InvocationState getInvocationState() {
    return this.invocationState;
  }

  public boolean isInsideSideEffect() {
    return this.insideSideEffect;
  }

  public String getFullyQualifiedMethodName() {
    return this.fullyQualifiedMethodName;
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
      MessageHeader.checkProtocolVersion(invocationInput.header());
      this.onStart(msg);
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

  void start(Consumer<InvocationId> afterStartCallback) {
    this.afterStartCallback.set(afterStartCallback);
    this.inputSubscription.request(1);
  }

  void onStart(MessageLite msg) {
    if (!(msg instanceof Protocol.StartMessage)) {
      this.fail(ProtocolException.unexpectedMessage(Protocol.StartMessage.class, msg));
      return;
    }

    // Unpack the StartMessage
    Protocol.StartMessage startMessage = (Protocol.StartMessage) msg;
    this.id = startMessage.getId();
    this.debugId = startMessage.getDebugId();
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

    if (this.span.isRecording()) {
      span.addEvent("Start", Attributes.of(Tracing.RESTATE_INVOCATION_ID, this.debugId));
    }

    // Execute state transition
    this.transitionState(InvocationState.REPLAYING);
    if (this.entriesToReplay == 0) {
      this.transitionState(InvocationState.PROCESSING);
    }

    this.inputSubscription.request(Long.MAX_VALUE);

    // Now execute the callback after start
    this.afterStartCallback.consume(cb -> cb.accept(new InvocationIdImpl(this.debugId)));
  }

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
    Protocol.ErrorMessage msg;
    if (cause instanceof ProtocolException) {
      msg = ((ProtocolException) cause).toErrorMessage();
    } else {
      msg =
          Protocol.ErrorMessage.newBuilder()
              .setCode(Code.UNKNOWN_VALUE)
              .setMessage(cause.toString())
              .build();
    }
    this.closeWithMessage(msg, cause);
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
      SyscallCallback<DeferredResult<T>> callback) {
    checkInsideSideEffectGuard();
    if (this.invocationState == InvocationState.CLOSED) {
      callback.onCancel(AbortedExecutionException.INSTANCE);
    } else if (this.invocationState == InvocationState.REPLAYING) {
      // Retrieve the entry
      this.readEntry(
          (entryIndex, actualEntryMessage) -> {
            journalEntry.checkEntryHeader(expectedEntryMessage, actualEntryMessage);

            if (journalEntry.hasResult((E) actualEntryMessage)) {
              // Entry is already completed
              journalEntry.updateUserStateStoreWithEntry(
                  (E) actualEntryMessage, this.userStateStore);
              ReadyResults.ReadyResultInternal<T> readyResultInternal =
                  journalEntry.parseEntryResult((E) actualEntryMessage);
              callback.onSuccess(DeferredResults.completedSingle(entryIndex, readyResultInternal));
            } else {
              // Entry is not completed yet
              this.readyResultStateMachine.offerCompletionParser(
                  entryIndex,
                  completionMessage -> {
                    journalEntry.updateUserStateStorageWithCompletion(
                        (E) actualEntryMessage, completionMessage, this.userStateStore);
                    return journalEntry.parseCompletionResult(completionMessage);
                  });
              callback.onSuccess(DeferredResults.single(entryIndex));
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

      // Retrieve the index
      int entryIndex = this.currentJournalIndex;

      // Write out the input entry
      this.writeEntry(entryToWrite);

      if (journalEntry.hasResult(entryToWrite)) {
        // Complete it with the result, as we already have it
        callback.onSuccess(
            DeferredResults.completedSingle(
                entryIndex, journalEntry.parseEntryResult(entryToWrite)));
      } else {
        // Register the completion parser
        this.readyResultStateMachine.offerCompletionParser(
            entryIndex,
            completionMessage -> {
              journalEntry.updateUserStateStorageWithCompletion(
                  entryToWrite, completionMessage, this.userStateStore);
              return journalEntry.parseCompletionResult(completionMessage);
            });

        // Call the onSuccess
        callback.onSuccess(DeferredResults.single(entryIndex));
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
    if (this.invocationState == InvocationState.CLOSED) {
      callback.onCancel(AbortedExecutionException.INSTANCE);
    } else if (this.invocationState == InvocationState.REPLAYING) {
      // Retrieve the entry
      this.readEntry(
          (entryIndex, actualEntryMessage) -> {
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

  void enterSideEffectBlock(EnterSideEffectSyscallCallback callback) {
    checkInsideSideEffectGuard();
    if (this.invocationState == InvocationState.CLOSED) {
      callback.onCancel(AbortedExecutionException.INSTANCE);
    } else if (this.invocationState == InvocationState.REPLAYING) {
      // Retrieve the entry
      this.readEntry(
          (entryIndex, msg) -> {
            Util.assertEntryClass(Java.SideEffectEntryMessage.class, msg);

            // We have a result already, complete the callback
            completeSideEffectCallbackWithEntry((Java.SideEffectEntryMessage) msg, callback);
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
      Java.SideEffectEntryMessage sideEffectEntry, ExitSideEffectSyscallCallback callback) {
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

      // Write new entry
      this.sideEffectAckStateMachine.registerExecutedSideEffect(this.currentJournalIndex);
      this.writeEntry(sideEffectEntry);

      // Wait for entry to be acked
      this.sideEffectAckStateMachine.waitLastSideEffectAck(
          new SideEffectAckStateMachine.SideEffectAckCallback() {
            @Override
            public void onLastSideEffectAck() {
              completeSideEffectCallbackWithEntry(sideEffectEntry, callback);
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
      Java.SideEffectEntryMessage sideEffectEntry, ExitSideEffectSyscallCallback callback) {
    if (sideEffectEntry.hasFailure()) {
      callback.onFailure(Util.toRestateException(sideEffectEntry.getFailure()));
    } else {
      callback.onSuccess(sideEffectEntry.getValue());
    }
  }

  // --- Deferred

  <T> void resolveDeferred(DeferredResult<T> deferredToResolve, SyscallCallback<Void> callback) {
    if (deferredToResolve.isCompleted()) {
      callback.onSuccess(null);
      return;
    }

    if (deferredToResolve instanceof DeferredResults.ResolvableSingleDeferredResult) {
      this.resolveSingleDeferred(
          (DeferredResults.ResolvableSingleDeferredResult<T>) deferredToResolve, callback);
      return;
    }

    if (deferredToResolve instanceof DeferredResults.CombinatorDeferredResult) {
      this.resolveCombinatorDeferred(
          (DeferredResults.CombinatorDeferredResult<T>) deferredToResolve, callback);
      return;
    }

    throw new IllegalArgumentException("Unexpected deferred class " + deferredToResolve.getClass());
  }

  <T> void resolveSingleDeferred(
      DeferredResults.ResolvableSingleDeferredResult<T> deferred, SyscallCallback<Void> callback) {
    this.readyResultStateMachine.onNewReadyResult(
        new ReadyResultStateMachine.OnNewReadyResultCallback() {
          @SuppressWarnings("unchecked")
          @Override
          public boolean onNewReadyResult(
              Map<Integer, ReadyResults.ReadyResultInternal<?>> resultMap) {
            ReadyResults.ReadyResultInternal<T> resolved =
                (ReadyResults.ReadyResultInternal<T>) resultMap.remove(deferred.entryIndex());
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
   * DeferredResults.ResolvableSingleDeferredResult}, created as result of completable syscalls.
   *
   * <p>The idea of the algorithm is the following: {@code rootDeferred} is the root of this tree,
   * and has internal state that can be mutated through {@link
   * DeferredResults.CombinatorDeferredResult#tryResolve(int)} to flag the tree as resolved. Every
   * time a new leaf is resolved through {@link
   * DeferredResults.ResolvableSingleDeferredResult#resolve(ReadyResults.ReadyResultInternal)}, we
   * try to resolve the tree again. We start by checking if we have enough resolved leafs in the
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
   *   <li>In case there are no {@link DeferredResults.SingleDeferredResultInternal
   *       SingleDeferredResultInternals}, it means every leaf has been resolved beforehand. In this
   *       case, we must be able to flag this combinator tree as resolved as well.
   * </ul>
   */
  private void resolveCombinatorDeferred(
      DeferredResults.CombinatorDeferredResult<?> rootDeferred, SyscallCallback<Void> callback) {
    // Calling .await() on a combinator deferred within a side effect is not allowed
    //  as resolving it creates or read a journal entry.
    checkInsideSideEffectGuard();
    if (Objects.equals(this.invocationState, InvocationState.REPLAYING)) {
      // Retrieve the CombinatorAwaitableEntryMessage
      this.readEntry(
          (entryIndex, actualMsg) -> {
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
      Map<Integer, DeferredResults.ResolvableSingleDeferredResult<?>> resolvableSingles =
          new HashMap<>();

      Set<DeferredResults.SingleDeferredResultInternal<?>> unprocessedLeafs =
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
      for (DeferredResults.SingleDeferredResultInternal<?> singleDeferred : unprocessedLeafs) {
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
              entryIndex, (DeferredResults.ResolvableSingleDeferredResult<?>) singleDeferred);
        }
      }

      // Not completed yet, we need to wait on the ReadyResultPublisher
      this.readyResultStateMachine.onNewReadyResult(
          new ReadyResultStateMachine.OnNewReadyResultCallback() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public boolean onNewReadyResult(
                Map<Integer, ReadyResults.ReadyResultInternal<?>> resultMap) {
              Iterator<Map.Entry<Integer, DeferredResults.ResolvableSingleDeferredResult<?>>> it =
                  resolvableSingles.entrySet().iterator();
              while (it.hasNext()) {
                Map.Entry<Integer, DeferredResults.ResolvableSingleDeferredResult<?>> entry =
                    it.next();
                int entryIndex = entry.getKey();

                ReadyResults.ReadyResultInternal<?> result = resultMap.remove(entryIndex);
                if (result != null) {
                  resolvedOrder.add(entryIndex);
                  entry.getValue().resolve((ReadyResults.ReadyResultInternal) result);
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
    LOG.debug("Transitioning {} to {}", this, newInvocationState);
    this.invocationState = newInvocationState;
    this.transitionStateObserver.accept(newInvocationState);
  }

  private void incrementCurrentIndex() {
    this.currentJournalIndex++;

    if (currentJournalIndex >= entriesToReplay
        && this.invocationState == InvocationState.REPLAYING) {
      if (!this.incomingEntriesStateMachine.isEmpty()) {
        throw new IllegalStateException("Entries queue should be empty at this point");
      }
      this.transitionState(InvocationState.PROCESSING);
    }
  }

  private void checkInsideSideEffectGuard() {
    if (this.insideSideEffect) {
      throw ProtocolException.invalidSideEffectCall();
    }
  }

  void readEntry(BiConsumer<Integer, MessageLite> msgCallback, Consumer<Throwable> errorCallback) {
    this.incomingEntriesStateMachine.read(
        new IncomingEntriesStateMachine.OnEntryCallback() {
          @Override
          public void onEntry(MessageLite msg) {
            incrementCurrentIndex();
            msgCallback.accept(currentJournalIndex - 1, msg);
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
    this.incrementCurrentIndex();
  }

  @Override
  public String toString() {
    return "InvocationStateMachine{"
        + "serviceName='"
        + serviceName
        + '\''
        + ", invocationState="
        + invocationState
        + ", id="
        + debugId
        + '}';
  }
}
