package dev.restate.sdk.core.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.SyscallCallback;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
  private final SideEffectAckPublisher sideEffectAckPublisher;
  private final EntriesQueue entriesQueue;
  private final ReadyResultPublisher readyResultPublisher;

  // Flow sub/pub
  private Flow.Subscriber<? super MessageLite> outputSubscriber;
  private Flow.Subscription inputSubscription;
  private Runnable afterStartCallback;

  public InvocationStateMachine(String serviceName, Span span) {
    this.serviceName = serviceName;
    this.span = span;

    this.sideEffectAckPublisher = new SideEffectAckPublisher();
    this.entriesQueue = new EntriesQueue();
    this.readyResultPublisher = new ReadyResultPublisher();
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
  }

  @Override
  public void onNext(MessageLite msg) {
    LOG.trace("Received input message {} {}", msg.getClass(), msg);
    if (this.state == State.WAITING_START) {
      this.onStart(msg);
    } else if (msg instanceof Protocol.CompletionMessage) {
      Protocol.CompletionMessage completionMessage = (Protocol.CompletionMessage) msg;

      // If ack, give it to side effect publisher
      if (completionMessage.getResultCase()
          == Protocol.CompletionMessage.ResultCase.RESULT_NOT_SET) {
        this.sideEffectAckPublisher.tryHandleSideEffectAck(completionMessage.getEntryIndex());
      } else {
        this.readyResultPublisher.offerCompletion((Protocol.CompletionMessage) msg);
      }
    } else {
      // We check the index rather than the state, because we might still be replaying
      this.entriesQueue.offer(msg);
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
    this.sideEffectAckPublisher.abort(SuspendedException.INSTANCE);
    this.readyResultPublisher.abort(SuspendedException.INSTANCE);
  }

  // --- Init routine to wait for the start message

  void start(Runnable afterStartCallback) {
    this.afterStartCallback = afterStartCallback;
    this.inputSubscription.request(1);
  }

  void onStart(MessageLite msg) {
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
          "Start", Attributes.of(Tracing.RESTATE_INVOCATION_ID, this.invocationId.toStringUtf8()));
    }

    // Execute state transition
    this.transitionState(State.REPLAYING);
    if (this.entriesToReplay == 0) {
      this.transitionState(State.PROCESSING);
    }

    this.inputSubscription.request(Long.MAX_VALUE);

    // Now execute the callback after start
    Runnable afterStartCallback = this.afterStartCallback;
    this.afterStartCallback = null;
    afterStartCallback.run();
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
      this.readyResultPublisher.abort(SuspendedException.INSTANCE);
      this.sideEffectAckPublisher.abort(SuspendedException.INSTANCE);
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
      this.readyResultPublisher.abort(cause);
      this.sideEffectAckPublisher.abort(cause);
      this.entriesQueue.abort(cause);
    }
  }

  // --- Methods to implement syscalls

  @SuppressWarnings("unchecked")
  <E extends MessageLite, T> void processCompletableJournalEntry(
      E inputEntry,
      Predicate<E> waitForCompletion,
      Consumer<Span> traceFn,
      Function<E, ProtocolException> checkEntryHeader,
      Function<E, ReadyResultInternal<T>> entryParser,
      Function<Protocol.CompletionMessage, ReadyResultInternal<T>> completionParser,
      SyscallCallback<DeferredResult<T>> callback) {
    maybeTransitionToProcessing();
    if (Objects.equals(this.state, State.CLOSED)) {
      callback.onCancel(SuspendedException.INSTANCE);
    } else if (Objects.equals(this.state, State.REPLAYING)) {
      // Retrieve the entry
      this.readEntry(
          (entryIndex, actualMsg) -> {
            ProtocolException e =
                Util.checkEntryClassAndHeader(actualMsg, inputEntry.getClass(), checkEntryHeader);

            if (e != null) {
              callback.onCancel(e);
            } else {
              if (waitForCompletion.test((E) actualMsg)) {
                this.readyResultPublisher.offerCompletionParser(
                    entryIndex,
                    Util.createCompletionParserCheckingResultVariant(
                        inputEntry.getClass(), completionParser));
                callback.onSuccess(new SingleDeferredResult<>(entryIndex));
              } else {
                ReadyResultInternal<T> readyResultInternal = entryParser.apply((E) actualMsg);
                callback.onSuccess(readyResultInternal);
              }
            }
          },
          callback::onCancel);
    } else if (this.state.canWriteOut()) {
      if (span.isRecording()) {
        traceFn.accept(span);
      }

      // Retrieve the index
      int entryIndex = this.currentJournalIndex;

      // Write out the input entry
      this.write(inputEntry);
      this.incrementCurrentIndex();

      // Register the completion parser
      this.readyResultPublisher.offerCompletionParser(
          entryIndex,
          Util.createCompletionParserCheckingResultVariant(
              inputEntry.getClass(), completionParser));

      // Call the onSuccess
      callback.onSuccess(new SingleDeferredResult<>(entryIndex));
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  <T extends MessageLite> void processJournalEntryWithoutWaitingAck(
      T inputEntry,
      Consumer<Span> traceFn,
      Function<T, ProtocolException> checkEntryHeader,
      SyscallCallback<Void> callback) {
    maybeTransitionToProcessing();
    if (Objects.equals(this.state, State.CLOSED)) {
      callback.onCancel(SuspendedException.INSTANCE);
    } else if (Objects.equals(this.state, State.REPLAYING)) {
      // Retrieve the entry
      this.readEntry(
          (entryIndex, actualMsg) -> {
            ProtocolException e =
                Util.checkEntryClassAndHeader(actualMsg, inputEntry.getClass(), checkEntryHeader);

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
      this.readEntry(
          (entryIndex, msg) -> {
            ProtocolException e =
                Util.checkEntryClassAndHeader(
                    msg, Protocol.SideEffectEntryMessage.class, v -> null);

            if (e != null) {
              failureCallback.accept(e);
            } else {
              entryCallback.accept((Protocol.SideEffectEntryMessage) msg);
            }
          },
          failureCallback);
    } else if (this.state.canWriteOut()) {
      this.sideEffectAckPublisher.executeEnterSideEffect(
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
    } else if (Objects.equals(this.state, State.CLOSED)) {
      failureCallback.accept(SuspendedException.INSTANCE);
    } else if (this.state.canWriteOut()) {
      if (span.isRecording()) {
        traceFn.accept(span);
      }

      // Write new entry
      this.sideEffectAckPublisher.registerExecutedSideEffect(this.currentJournalIndex);
      this.write(sideEffectToWrite);
      this.incrementCurrentIndex();

      entryCallback.accept(sideEffectToWrite);
    } else {
      throw new IllegalStateException(
          "This method was invoked when the state machine is not ready to process user code. This is probably an SDK bug");
    }
  }

  <T> void resolveDeferred(DeferredResult<T> deferredToResolve, SyscallCallback<Void> callback) {
    if (deferredToResolve.isCompleted()) {
      callback.onSuccess(null);
      return;
    }

    if (deferredToResolve instanceof SingleDeferredResult) {
      this.readyResultPublisher.onNewReadyResult(
          new ReadyResultPublisher.OnNewReadyResultCallback() {
            @Override
            public boolean onNewReadyResult(Map<Integer, ReadyResultInternal<?>> resultMap) {
              boolean resolved =
                  ((SingleDeferredResult<?>) deferredToResolve).tryResolve(resultMap);
              if (resolved) {
                callback.onSuccess(null);
                return true;
              }
              return false;
            }

            @Override
            public void onCancel(Throwable e) {
              callback.onCancel(e);
            }
          });
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
    maybeTransitionToProcessing();
  }

  private void maybeTransitionToProcessing() {
    if (currentJournalIndex >= entriesToReplay && this.state == State.REPLAYING) {
      assert this.entriesQueue.isEmpty();
      this.transitionState(State.PROCESSING);
    }
  }

  void readEntry(BiConsumer<Integer, MessageLite> msgCallback, Consumer<Throwable> errorCallback) {
    this.entriesQueue.read(
        msg -> {
          incrementCurrentIndex();
          msgCallback.accept(this.currentJournalIndex - 1, msg);
        },
        errorCallback);
  }

  private void write(MessageLite message) {
    LOG.trace("Writing to output message {} {}", message.getClass(), message);
    Objects.requireNonNull(this.outputSubscriber).onNext(message);
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
}
