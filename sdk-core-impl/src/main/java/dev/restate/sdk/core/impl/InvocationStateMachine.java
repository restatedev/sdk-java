package dev.restate.sdk.core.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.ReadyResult;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InvocationStateMachine implements InvocationFlow.InvocationProcessor {

  private enum State {
    WAITING_START,
    REPLAYING,
    PROCESSING,
    CLOSED;

    boolean canWriteOut() {
      return this == PROCESSING;
    }

    boolean canReadIn() {
      return this == REPLAYING || this == PROCESSING;
    }
  }

  private static final Logger LOG = LogManager.getLogger(InvocationStateMachine.class);

  private final String serviceName;
  private final Span span;

  private volatile State state;

  // Obtained after WAITING_START
  private ByteString instanceKey;
  private ByteString invocationId;
  private int entriesToReplay;

  // Index tracking progress in the journal
  private int currentJournalIndex;

  // Last not acked side effect, used to figure out when to wait for another side effect to complete
  // -1 means no side effect waiting to be acked.
  private int lastNotAckedSideEffect = -1;

  // Buffering of messages and completions
  private final MessageQueue messageQueue;
  private final CompletionStore completionStore;

  // Flow sub/pub
  private Flow.Subscriber<? super MessageLite> outputSubscriber;
  private Flow.Subscription inputSubscription;

  public InvocationStateMachine(String serviceName, Span span) {
    this.serviceName = serviceName;
    this.span = span;

    this.messageQueue = new MessageQueue();
    this.completionStore = new CompletionStore();
  }

  // --- Getters

  public int getCurrentJournalIndex() {
    return currentJournalIndex;
  }

  public String getServiceName() {
    return serviceName;
  }

  public ByteString getInstanceKey() {
    return instanceKey;
  }

  public ByteString getInvocationId() {
    return invocationId;
  }

  // This might be called concurrently (see InvocationStateMachineGrpcBridge#isCancelled)
  public boolean isClosed() {
    return Objects.equals(this.state, State.CLOSED);
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
            close();
          }
        });
  }

  // --- Input Subscriber impl

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.inputSubscription = subscription;
    this.inputSubscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(MessageLite msg) {
    LOG.trace("Enqueuing input message {} {}", msg.getClass(), msg);
    this.messageQueue.offer(msg);
  }

  @Override
  public void onError(Throwable throwable) {
    LOG.trace("Got failure from input publisher", throwable);
    failRead(throwable);
  }

  @Override
  public void onComplete() {
    LOG.trace("Input publisher closed");
    this.messageQueue.closeInput(SuspendedException.INSTANCE);
  }

  // --- Init routine to wait for the start message

  void start(Runnable afterStartCallback) {
    this.transitionState(State.WAITING_START);

    this.messageQueue.read(
        msg -> {
          if (!(msg instanceof Protocol.StartMessage)) {
            this.fail(ProtocolException.unexpectedMessage(Protocol.StartMessage.class, msg));
            return;
          }

          // Unpack the StartMessage
          Protocol.StartMessage startMessage = (Protocol.StartMessage) msg;
          this.instanceKey = startMessage.getInstanceKey();
          this.invocationId = startMessage.getInvocationId();
          this.entriesToReplay = startMessage.getKnownEntries();

          if (this.span.isRecording()) {
            span.addEvent(
                "Start",
                Attributes.of(Tracing.RESTATE_INVOCATION_ID, this.invocationId.toStringUtf8()));
          }

          // Execute state transition
          this.transitionState(State.REPLAYING);
          if (this.entriesToReplay == 0) {
            this.transitionState(State.PROCESSING);
          }

          // Now execute the callback after start
          afterStartCallback.run();
        },
        this::failRead);
  }

  void close() {
    if (this.state != State.CLOSED) {
      this.transitionState(State.CLOSED);
      if (inputSubscription != null) {
        this.inputSubscription.cancel();
      }
      if (this.outputSubscriber != null) {
        this.outputSubscriber.onComplete();
      }
      this.messageQueue.closeInput(SuspendedException.INSTANCE);
    }
  }

  void fail(ProtocolException cause) {
    if (this.state != State.CLOSED) {
      this.transitionState(State.CLOSED);
      LOG.debug("Close cause", cause);
      if (inputSubscription != null) {
        this.inputSubscription.cancel();
      }
      if (this.outputSubscriber != null) {
        this.outputSubscriber.onError(cause);
      }
      this.messageQueue.closeInput(cause);
    }
  }

  // --- Methods to implement syscalls

  @SuppressWarnings("unchecked")
  <T extends MessageLite, R> void processCompletableJournalEntry(
      T inputEntry,
      Predicate<T> waitForCompletion,
      Consumer<Span> traceFn,
      Function<T, ProtocolException> checkEntryHeader,
      Function<T, ReadyResult<R>> entryParser,
      Function<Protocol.CompletionMessage, ReadyResult<R>> completionParser,
      Consumer<DeferredResult<R>> deferredCallback,
      Consumer<Throwable> failureCallback) {
    if (Objects.equals(this.state, State.CLOSED)) {
      failureCallback.accept(SuspendedException.INSTANCE);
    } else if (Objects.equals(this.state, State.REPLAYING)) {
      // Retrieve the entry
      this.getCurrentEntry(
          actualMsg -> {
            int entryIndex = this.currentJournalIndex;
            this.incrementCurrentIndex();

            if (!inputEntry.getClass().equals(actualMsg.getClass())) {
              failureCallback.accept(
                  ProtocolException.unexpectedMessage(inputEntry.getClass(), actualMsg));
              return;
            }
            T actualEntry = (T) actualMsg;
            ProtocolException e = checkEntryHeader.apply(actualEntry);

            if (e != null) {
              failureCallback.accept(e);
            } else if (waitForCompletion.test(actualEntry)) {
              this.completionStore.wantCompletion(entryIndex);
              deferredCallback.accept(
                  ResultTreeNodes.waiting(entryIndex, completionParser, inputEntry.getClass()));
            } else {
              deferredCallback.accept(entryParser.apply(actualEntry));
            }
          },
          failureCallback);
    } else if (this.state.canWriteOut()) {
      if (span.isRecording()) {
        traceFn.accept(span);
      }

      // Write new entry
      this.write(inputEntry);
      int entryIndex = this.currentJournalIndex;
      this.incrementCurrentIndex();

      // Invoke the deferred callback
      this.completionStore.wantCompletion(entryIndex);
      deferredCallback.accept(
          ResultTreeNodes.waiting(entryIndex, completionParser, inputEntry.getClass()));
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  @SuppressWarnings("unchecked")
  <T extends MessageLite> void processJournalEntryWithoutWaitingAck(
      T inputEntry,
      Consumer<Span> traceFn,
      Function<T, ProtocolException> checkEntryHeader,
      Runnable okCallback,
      Consumer<Throwable> failureCallback) {
    if (Objects.equals(this.state, State.CLOSED)) {
      failureCallback.accept(SuspendedException.INSTANCE);
    } else if (Objects.equals(this.state, State.REPLAYING)) {
      // Retrieve the entry
      this.getCurrentEntry(
          actualMsg -> {
            this.incrementCurrentIndex();
            if (!inputEntry.getClass().equals(actualMsg.getClass())) {
              failureCallback.accept(
                  ProtocolException.unexpectedMessage(inputEntry.getClass(), actualMsg));
              return;
            }
            ProtocolException e = checkEntryHeader.apply((T) actualMsg);
            if (e != null) {
              failureCallback.accept(e);
            } else {
              okCallback.run();
            }
          },
          failureCallback);
    } else if (this.state.canWriteOut()) {
      if (span.isRecording()) {
        traceFn.accept(span);
      }

      // Write new entry
      this.write(inputEntry);
      this.incrementCurrentIndex();

      // Invoke the ok callback
      okCallback.run();
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  void processSideEffectJournalEntry(
      Consumer<Consumer<Protocol.SideEffectEntryMessage>> executeSideEffect,
      Consumer<Span> traceFn,
      Consumer<Protocol.SideEffectEntryMessage> entryCallback,
      Consumer<Throwable> failureCallback) {
    if (Objects.equals(this.state, State.CLOSED)) {
      failureCallback.accept(SuspendedException.INSTANCE);
    } else if (Objects.equals(this.state, State.REPLAYING)) {
      // Retrieve the entry
      this.getCurrentEntry(
          msg -> {
            this.incrementCurrentIndex();
            if (!Protocol.SideEffectEntryMessage.class.equals(msg.getClass())) {
              failureCallback.accept(
                  ProtocolException.unexpectedMessage(Protocol.SideEffectEntryMessage.class, msg));
            } else {
              entryCallback.accept((Protocol.SideEffectEntryMessage) msg);
            }
          },
          failureCallback);
    } else if (this.state.canWriteOut()) {
      if (this.lastNotAckedSideEffect != -1) {
        this.getCompletion(
            this.lastNotAckedSideEffect,
            completionMessage -> {
              // Reset the ack to wait
              this.lastNotAckedSideEffect = -1;

              executeSideEffectThenWrite(executeSideEffect, traceFn, entryCallback);
            },
            failureCallback);
      } else {

        executeSideEffectThenWrite(executeSideEffect, traceFn, entryCallback);
      }
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  <T> void resolveDeferred(
      DeferredResult<T> deferredToResolve,
      Consumer<ReadyResult<T>> resultCallback,
      Consumer<Throwable> failureCallback) {
    if (deferredToResolve instanceof ReadyResult) {
      resultCallback.accept((ReadyResult<T>) deferredToResolve);
      return;
    }

    if (deferredToResolve instanceof ResultTreeNodes.Waiting) {
      ResultTreeNodes.Waiting<T> waiting = (ResultTreeNodes.Waiting<T>) deferredToResolve;
      this.getCompletion(
          waiting.getJournalIndex(),
          completionMsg -> {
            ProtocolException e = Util.checkCompletion(waiting.getInputEntryClass(), completionMsg);
            if (e != null) {
              failureCallback.accept(e);
            } else {
              resultCallback.accept(waiting.getCompletionParser().apply(completionMsg));
            }
          },
          failureCallback);
      return;
    }

    throw new IllegalArgumentException("Unexpected deferred class " + deferredToResolve.getClass());
  }

  // --- Internal callback

  private void transitionState(State newState) {
    if (this.state == State.CLOSED) {
      // Cannot move out of the closed state
      return;
    }
    LOG.debug("Transitioning {} to {}", this, newState);
    this.state = newState;
  }

  private void incrementCurrentIndex() {
    this.currentJournalIndex++;
    if (currentJournalIndex >= entriesToReplay) {
      if (this.state == State.REPLAYING) {
        this.transitionState(State.PROCESSING);
      }
    }
  }

  private void readEntryOrCompletion(
      Consumer<MessageLite> entryCallback,
      Consumer<Protocol.CompletionMessage> completionCallback,
      Consumer<Throwable> failureCallback) {
    this.messageQueue.read(
        msg -> {
          if (msg instanceof Protocol.CompletionMessage) {
            completionCallback.accept((Protocol.CompletionMessage) msg);
          } else if (Util.isEntry(msg)) {
            entryCallback.accept(msg);
          } else {
            failureCallback.accept(ProtocolException.unexpectedMessage("entry or completion", msg));
          }
        },
        failureCallback);
  }

  private void getCurrentEntry(
      Consumer<MessageLite> entryCallback, Consumer<Throwable> failureCallback) {
    assert this.state == State.REPLAYING;
    // Because we're replaying, this read HAS to be a journal entry. If not, then there is a
    // protocol violation
    this.messageQueue.read(entryCallback, failureCallback);
  }

  private void write(MessageLite message) {
    LOG.trace("Writing to output message {} {}", message.getClass(), message);
    Objects.requireNonNull(this.outputSubscriber).onNext(message);
  }

  private void getCompletion(
      int journalIndex,
      Consumer<Protocol.CompletionMessage> entryCallback,
      Consumer<Throwable> failureCallback) {
    Protocol.CompletionMessage resolvedCompletion =
        this.completionStore.getCompletion(journalIndex);
    if (resolvedCompletion != null) {
      entryCallback.accept(resolvedCompletion);
      return;
    }

    if (!this.state.canReadIn()) {
      failureCallback.accept(SuspendedException.INSTANCE);
      return;
    }

    this.readEntryOrCompletion(
        // This might happen in case I'm replaying and I need a completion, before waiting the end
        // of the replay phase
        this.messageQueue::offer,
        completion -> {
          if (completion.getEntryIndex() < this.currentJournalIndex) {
            this.completionStore.handlePastCompletion(completion);
          } else {
            this.completionStore.handleFutureCompletion(completion);
          }

          // TODO perhaps add a max recursion limit,
          //  equal to the max buffering we tolerate for completions
          getCompletion(journalIndex, entryCallback, failureCallback);
        },
        failureCallback);
  }

  private void executeSideEffectThenWrite(
      Consumer<Consumer<Protocol.SideEffectEntryMessage>> executeSideEffect,
      Consumer<Span> traceFn,
      Consumer<Protocol.SideEffectEntryMessage> entryCallback) {
    executeSideEffect.accept(
        sideEffectEntryMessage -> {
          if (span.isRecording()) {
            traceFn.accept(span);
          }

          // Write new entry
          this.write(sideEffectEntryMessage);
          this.lastNotAckedSideEffect = this.currentJournalIndex;
          this.completionStore.wantCompletion(this.currentJournalIndex);
          this.incrementCurrentIndex();

          entryCallback.accept(sideEffectEntryMessage);
        });
  }

  private void failRead(Throwable cause) {
    fail(ProtocolException.inputPublisherError(cause));
  }

  @Override
  public String toString() {
    return "InvocationStateMachine{"
        + "serviceName='"
        + serviceName
        + '\''
        + ", state="
        + state
        + ", instanceKey="
        + instanceKey
        + ", invocationId="
        + invocationId
        + '}';
  }

  @FunctionalInterface
  static interface EntryOrCompletionDeferred<T extends MessageLite> {

    static <T extends MessageLite> EntryOrCompletionDeferred<T> entry(T e) {
      return (entryCallback, completionCallback, failureCallback) -> entryCallback.accept(e);
    }

    static <T extends MessageLite> EntryOrCompletionDeferred<T> failed(Throwable e) {
      return (entryCallback, completionCallback, failureCallback) -> failureCallback.accept(e);
    }

    void start(
        Consumer<T> entryCallback,
        Consumer<Protocol.CompletionMessage> completionCallback,
        Consumer<Throwable> failureCallback);
  }
}
