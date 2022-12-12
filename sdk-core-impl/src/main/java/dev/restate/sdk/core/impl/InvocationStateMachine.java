package dev.restate.sdk.core.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.syscalls.DeferredResultCallback;
import dev.restate.sdk.core.syscalls.SyscallCallback;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class InvocationStateMachine implements InvocationFlow.InvocationProcessor {

  private static final Logger LOG = LogManager.getLogger(InvocationStateMachine.class);

  private enum State {
    WAITING_START,
    REPLAYING,
    PROCESSING,
    CLOSED;

    boolean canWriteOut() {
      return this == PROCESSING;
    }
  }

  private final String serviceName;
  private final Span span;

  private volatile State state = State.WAITING_START;

  // Obtained after WAITING_START
  private ByteString instanceKey;
  private ByteString invocationId;
  private int entriesToReplay;

  // Index tracking progress in the journal
  private int currentJournalIndex;

  // Buffering of messages and completions
  private final SPSCHandshakeQueue handshakeQueue;
  private final SideEffectCheckpoint sideEffectCheckpoint;
  private final SPSCEntriesQueue entriesQueue;
  private final ReadyResultPublisher readyResultPublisher;
  private final Map<Integer, Function<Protocol.CompletionMessage, ReadyResult<?>>>
      waitingCompletionsParsers;
  private final Map<Integer, Protocol.CompletionMessage> waitingCompletions;

  // Flow sub/pub
  private Flow.Subscriber<? super MessageLite> outputSubscriber;
  private Flow.Subscription inputSubscription;

  public InvocationStateMachine(String serviceName, Span span) {
    this.serviceName = serviceName;
    this.span = span;

    this.handshakeQueue = new SPSCHandshakeQueue();
    this.sideEffectCheckpoint = new SideEffectCheckpoint();
    this.entriesQueue = new SPSCEntriesQueue();
    this.readyResultPublisher =
        new ReadyResultPublisher(
            idx ->
                this.write(
                    Java.CompletionOrderEntryMessage.newBuilder().setEntryIndex(idx).build()));
    this.waitingCompletionsParsers = new HashMap<>();
    this.waitingCompletions = new HashMap<>();
  }

  // --- Getters

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
    LOG.trace("Received input message {} {}", msg.getClass(), msg);
    if (this.state == State.WAITING_START) {
      this.handshakeQueue.offer(msg);
    } else if (currentJournalIndex < entriesToReplay) {
      // We check the index rather than the state, because we might still be replaying
      if (msg instanceof Java.CompletionOrderEntryMessage) {
        this.readyResultPublisher.offerOrder(
            ((Java.CompletionOrderEntryMessage) msg).getEntryIndex());
      } else {
        this.entriesQueue.offer(this.currentJournalIndex, msg);
      }
      this.incrementCurrentIndex();
    } else {
      // From this point onward, this can only be a completion
      handleCompletion(msg);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    LOG.trace("Got failure from input publisher", throwable);
    failRead(throwable);
  }

  @Override
  public void onComplete() {
    LOG.trace("Input publisher closed");
    this.sideEffectCheckpoint.abort(null);
    this.readyResultPublisher.onInputChannelClosed(null);
  }

  // --- Init routine to wait for the start message

  void start(Runnable afterStartCallback) {
    this.handshakeQueue.read(
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
          this.handshakeQueue.drainAndClose().forEach(this::onNext);
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
      this.readyResultPublisher.onInputChannelClosed(null);
      this.sideEffectCheckpoint.abort(SuspendedException.INSTANCE);
      this.handshakeQueue.abort(SuspendedException.INSTANCE);
      this.entriesQueue.abort(SuspendedException.INSTANCE);
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
      this.readyResultPublisher.onInputChannelClosed(cause);
      this.sideEffectCheckpoint.abort(cause);
      this.handshakeQueue.abort(cause);
      this.entriesQueue.abort(cause);
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
      DeferredResultCallback<R> deferredResultCallback,
      SyscallCallback<Integer> syscallCallback) {
    maybeTransitionToProcessing();
    if (Objects.equals(this.state, State.CLOSED)) {
      syscallCallback.onCancel(null);
    } else if (Objects.equals(this.state, State.REPLAYING)) {
      // Retrieve the entry
      this.entriesQueue.read(
          (entryIndex, actualMsg) -> {
            ProtocolException e = checkEntry(actualMsg, inputEntry.getClass(), checkEntryHeader);

            if (e != null) {
              syscallCallback.onCancel(e);
            } else {
              if (waitForCompletion.test((T) actualMsg)) {
                this.waitingCompletionsParsers.put(
                    entryIndex, this.completionParser(inputEntry.getClass(), completionParser));
                if (waitingCompletions.containsKey(entryIndex)) {
                  handleCompletion(waitingCompletions.remove(entryIndex));
                }
                this.readyResultPublisher.subscribe(entryIndex, deferredResultCallback);
              } else {
                this.readyResultPublisher.offerResult(entryIndex, entryParser.apply((T) actualMsg));
                this.readyResultPublisher.subscribe(entryIndex, deferredResultCallback);
              }
              maybeTransitionToProcessing();
              syscallCallback.onSuccess(entryIndex);
            }
          },
          syscallCallback::onCancel);
    } else if (this.state.canWriteOut()) {
      if (span.isRecording()) {
        traceFn.accept(span);
      }

      // Retrieve the index
      int entryIndex = this.currentJournalIndex;

      // Write out the input entry
      this.write(inputEntry);
      this.incrementCurrentIndex();

      // Register the completion subscription
      this.waitingCompletionsParsers.put(
          entryIndex, this.completionParser(inputEntry.getClass(), completionParser));
      // TODO is this needed only in tests?
      if (waitingCompletions.containsKey(entryIndex)) {
        handleCompletion(waitingCompletions.remove(entryIndex));
      }
      this.readyResultPublisher.subscribe(entryIndex, deferredResultCallback);

      // Call the onSuccess
      syscallCallback.onSuccess(entryIndex);
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
      SyscallCallback<Void> callback) {
    maybeTransitionToProcessing();
    if (Objects.equals(this.state, State.CLOSED)) {
      callback.onCancel(null);
    } else if (Objects.equals(this.state, State.REPLAYING)) {
      // Retrieve the entry
      this.entriesQueue.read(
          (entryIndex, actualMsg) -> {
            ProtocolException e = checkEntry(actualMsg, inputEntry.getClass(), checkEntryHeader);

            if (e != null) {
              callback.onCancel(e);
            } else {
              callback.onSuccess(null);
            }
          },
          callback::onCancel);
    } else if (this.state.canWriteOut()) {
      if (span.isRecording()) {
        traceFn.accept(span);
      }

      // Write new entry
      this.write(inputEntry);
      this.incrementCurrentIndex();

      // Invoke the ok callback
      callback.onSuccess(null);
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  void enterSideEffectJournalEntry(
      Consumer<Span> traceFn,
      Consumer<Protocol.SideEffectEntryMessage> entryCallback,
      Runnable noEntryCallback,
      Consumer<Throwable> failureCallback) {
    maybeTransitionToProcessing();
    if (Objects.equals(this.state, State.CLOSED)) {
      failureCallback.accept(SuspendedException.INSTANCE);
    } else if (Objects.equals(this.state, State.REPLAYING)) {
      // Retrieve the entry
      this.entriesQueue.read(
          (entryIndex, msg) -> {
            ProtocolException e = checkEntry(msg, Protocol.SideEffectEntryMessage.class, v -> null);

            if (e != null) {
              failureCallback.accept(e);
            } else {
              entryCallback.accept((Protocol.SideEffectEntryMessage) msg);
            }
          },
          failureCallback);
    } else if (this.state.canWriteOut()) {
      this.sideEffectCheckpoint.executeEnterSideEffect(
          SyscallCallback.of(
              v -> {
                if (span.isRecording()) {
                  traceFn.accept(span);
                }
                noEntryCallback.run();
              },
              failureCallback));
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  void exitSideEffectBlock(
      Protocol.SideEffectEntryMessage sideEffectToWrite,
      Consumer<Span> traceFn,
      Consumer<Protocol.SideEffectEntryMessage> entryCallback,
      Consumer<Throwable> failureCallback) {
    if (Objects.equals(this.state, State.REPLAYING)) {
      throw new IllegalStateException(
          "exitSideEffect has been invoked when the state machine is in replaying mode. "
              + "This is probably an SDK bug and might be caused by a missing enterSideEffectBlock invocation before exitSideEffectBlock.");
      //    } else if (this.lastNotAckedSideEffect != -1) {
      //      throw new IllegalStateException(
      //          "exitSideEffect has been invoked when a side effect still needs to be flushed. "
      //              + "This is probably an SDK bug and might be caused by a missing
      // enterSideEffectBlock invocation before exitSideEffectBlock.");
    } else if (Objects.equals(this.state, State.CLOSED)) {
      failureCallback.accept(SuspendedException.INSTANCE);
    } else if (this.state.canWriteOut()) {
      if (span.isRecording()) {
        traceFn.accept(span);
      }

      // Write new entry
      this.sideEffectCheckpoint.registerExecutedSideEffect(this.currentJournalIndex);
      this.write(sideEffectToWrite);
      this.incrementCurrentIndex();

      entryCallback.accept(sideEffectToWrite);
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  // --- Internal callback

  private void transitionState(State newState) {
    if (this.state == State.CLOSED) {
      // Cannot move out of the closed state
      return;
    }
    // Update state
    LOG.debug("Transitioning {} to {}", this, newState);
    this.state = newState;

    // Make sure to propagate the processing state to readyResultQueue
    if (this.state == State.PROCESSING) {
      this.readyResultPublisher.onEndReplay();
    }
  }

  private void incrementCurrentIndex() {
    this.currentJournalIndex++;
    maybeTransitionToProcessing();
  }

  private void maybeTransitionToProcessing() {
    if (currentJournalIndex >= entriesToReplay
        && this.state == State.REPLAYING
        && this.entriesQueue.isEmpty()
        && this.readyResultPublisher.isOrderQueueEmpty()) {
      this.transitionState(State.PROCESSING);
    }
  }

  private void handleCompletion(MessageLite msg) {
    if (!(msg instanceof Protocol.CompletionMessage)) {
      this.fail(ProtocolException.unexpectedMessage("completion", msg));
      return;
    }
    Protocol.CompletionMessage completionMessage = (Protocol.CompletionMessage) msg;

    // Check if this is an ack and pass it to side effect checkpoint
    if (completionMessage.getResultCase() == Protocol.CompletionMessage.ResultCase.RESULT_NOT_SET) {
      this.sideEffectCheckpoint.tryHandleSideEffectAck(completionMessage.getEntryIndex());
      return;
    }

    // Retrieve the parser
    Function<Protocol.CompletionMessage, ReadyResult<?>> parser =
        this.waitingCompletionsParsers.remove(completionMessage.getEntryIndex());
    if (parser == null) {
      // This can happen if I'm replaying, but I already got a completion before reaching the entry
      this.waitingCompletions.put(
          ((Protocol.CompletionMessage) msg).getEntryIndex(), (Protocol.CompletionMessage) msg);
      return;
    }

    // Parse to ready result
    ReadyResult<?> readyResult = null;
    Throwable throwable = null;
    try {
      readyResult = parser.apply(completionMessage);
    } catch (Throwable t) {
      throwable = t;
    }

    // Push to the ready result queue
    if (throwable != null) {
      failRead(throwable);
    } else {
      this.readyResultPublisher.offerResult(completionMessage.getEntryIndex(), readyResult);
    }
  }

  private void write(MessageLite message) {
    LOG.trace("Writing to output message {} {}", message.getClass(), message);
    Objects.requireNonNull(this.outputSubscriber).onNext(message);
  }

  private void failRead(Throwable cause) {
    fail(ProtocolException.inputPublisherError(cause));
  }

  private <T> Function<Protocol.CompletionMessage, ReadyResult<?>> completionParser(
      Class<? extends MessageLite> entryClazz,
      Function<Protocol.CompletionMessage, ReadyResult<T>> parser) {
    return completionMsg -> {
      ProtocolException ex = Util.checkCompletion(entryClazz, completionMsg);
      if (ex != null) {
        throw ex;
      }
      return parser.apply(completionMsg);
    };
  }

  @SuppressWarnings("unchecked")
  private <T extends MessageLite> @Nullable ProtocolException checkEntry(
      MessageLite actualMsg,
      Class<? extends MessageLite> clazz,
      Function<T, ProtocolException> checkEntryHeader) {
    if (!clazz.equals(actualMsg.getClass())) {
      return ProtocolException.unexpectedMessage(clazz, actualMsg);
    }
    T actualEntry = (T) actualMsg;
    return checkEntryHeader.apply(actualEntry);
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
}
