// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.legacy;

import static dev.restate.sdk.core.legacy.Util.sliceToByteString;
import static dev.restate.sdk.core.legacy.Util.toProtocolFailure;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.EndpointRequestHandler;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.StateMachine;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Legacy pure-Java {@link StateMachine}, used on JDK &lt; 23 or when the native shared-core library
 * is unavailable/disabled. This is a deprecated fallback that will be removed in a future release;
 * the native (FFM) state machine is the path that supports the latest Restate features.
 */
public final class LegacyStateMachine implements StateMachine {

  private static final Logger LOG = LogManager.getLogger(LegacyStateMachine.class);

  static final int CANCEL_SIGNAL_ID = 1;

  // Message handling
  private final MessageDecoder messageDecoder = new MessageDecoder();
  private final BufferingMessageSink outputSink = new BufferingMessageSink();

  // State machine context
  private final StateContext stateContext;

  // Implicit cancellation tracking.
  private final List<TrackedInvocationId> trackedInvocationIds = new ArrayList<>();

  private static final class TrackedInvocationId {
    private final int handle;
    private @Nullable String invocationId;

    TrackedInvocationId(int handle) {
      this.handle = handle;
    }

    boolean isResolved() {
      return invocationId != null;
    }
  }

  public LegacyStateMachine(
      HeadersAccessor headersAccessor,
      EndpointRequestHandler.LoggingContextSetter loggingContextSetter) {
    String contentTypeHeader = headersAccessor.get(ServiceProtocol.CONTENT_TYPE);

    var serviceProtocolVersion = ServiceProtocol.parseServiceProtocolVersion(contentTypeHeader);
    if (!ServiceProtocol.isSupported(serviceProtocolVersion)) {
      throw new ProtocolException(
          String.format(
              "Service endpoint does not support the service protocol version '%s'.",
              contentTypeHeader),
          ProtocolException.UNSUPPORTED_MEDIA_TYPE_CODE);
    }

    this.stateContext = new StateContext(loggingContextSetter, serviceProtocolVersion);
    this.stateContext.registerOutputSubscriber(this.outputSink);
  }

  @Override
  public void notifyInput(Slice bytes) {
    LOG.trace("Received input slice");
    this.messageDecoder.offer(bytes);

    InvocationInput invocationInput = this.messageDecoder.next();
    while (invocationInput != null) {
      LOG.trace(
          "Received input message {} {}",
          invocationInput.message().getClass(),
          invocationInput.message());

      this.stateContext.getCurrentState().onNewMessage(invocationInput, this.stateContext);

      invocationInput = this.messageDecoder.next();
    }
  }

  @Override
  public void notifyInputClosed() {
    LOG.trace("Input publisher closed");
    this.stateContext.getCurrentState().onInputClosed(this.stateContext);
  }

  @Override
  public void notifyError(Throwable throwable) {
    this.stateContext.getCurrentState().hitError(throwable, null, null, this.stateContext);
  }

  @Override
  public Slice takeOutput() {
    return Slice.wrap(this.outputSink.take());
  }

  @Override
  public String getResponseContentType() {
    return ServiceProtocol.serviceProtocolVersionToHeaderValue(
        stateContext.getNegotiatedProtocolVersion());
  }

  @Override
  public boolean isReadyToExecute() {
    var state = this.stateContext.getCurrentState();
    return !(state instanceof WaitingStartState || state instanceof WaitingReplayEntriesState);
  }

  @Override
  public InvocationState state() {
    return this.stateContext.getCurrentState().getInvocationState();
  }

  @Override
  public AwaitResult doAwait(UnresolvedFuture future) {
    // Implicit cancellation: the VM owns the cancellation protocol (mirroring the canonical native
    // core's do_await). We implicitly await the cancel signal (handle 1) alongside the user's await
    // tree. If the cancel signal fires, we resolve every tracked child invocation id, send a cancel
    // signal to each, consume the cancel notification, and surface CancelSignalReceived. The
    // HandlerContextImpl only needs to cancel the awaited future on CancelSignalReceived.
    UnresolvedFuture futureWithCancellation =
        new UnresolvedFuture.FirstCompleted(
            List.of(future, new UnresolvedFuture.Single(CANCEL_SIGNAL_ID)));

    AwaitResult response = doProgress(futureWithCancellation.handles());
    if (!(response instanceof AwaitResult.AnyCompleted)) {
      return response;
    }

    // If the completed handle is NOT the cancel signal, just let the caller proceed.
    if (!this.stateContext.getCurrentState().isCompleted(CANCEL_SIGNAL_ID)) {
      return AwaitResult.ANY_COMPLETED;
    }

    // The cancel signal fired: resolve all the tracked child invocation ids, then cancel them.
    for (TrackedInvocationId tracked : this.trackedInvocationIds) {
      if (tracked.isResolved()) {
        continue;
      }
      AwaitResult resolve = doProgress(List.of(tracked.handle));
      if (!(resolve instanceof AwaitResult.AnyCompleted)) {
        // Can't resolve the invocation id yet (still waiting on external progress); propagate.
        // (Suspension would have already thrown AbortedExecutionException out of doProgress.)
        return resolve;
      }
      NotificationValue value = takeNotification(tracked.handle);
      if (value instanceof NotificationValue.InvocationId invocationId) {
        tracked.invocationId = invocationId.invocationId();
      } else {
        throw new IllegalStateException(
            "Expecting an invocation id for a tracked call handle, but got: " + value);
      }
    }

    for (TrackedInvocationId tracked : this.trackedInvocationIds) {
      cancelInvocation(java.util.Objects.requireNonNull(tracked.invocationId));
    }
    this.trackedInvocationIds.clear();

    // Consume the cancel notification and surface the cancellation.
    takeNotification(CANCEL_SIGNAL_ID);
    return AwaitResult.CANCEL_SIGNAL_RECEIVED;
  }

  /**
   * Single step of {@code do_progress}, translating the legacy {@link State.DoProgressResponse}.
   */
  private AwaitResult doProgress(List<Integer> anyHandle) {
    State.DoProgressResponse response =
        this.stateContext.getCurrentState().doProgress(anyHandle, this.stateContext);

    if (response instanceof State.DoProgressResponse.AnyCompleted) {
      return AwaitResult.ANY_COMPLETED;
    } else if (response instanceof State.DoProgressResponse.ExecuteRun executeRun) {
      return new AwaitResult.ExecuteRun(executeRun.handle());
    } else if (response instanceof State.DoProgressResponse.ReadFromInput) {
      // Need more input from the runtime to make progress.
      return AwaitResult.WAIT_EXTERNAL_PROGRESS;
    } else if (response instanceof State.DoProgressResponse.WaitingPendingRun) {
      // A run is still executing; wait for it to propose its completion.
      return AwaitResult.WAIT_EXTERNAL_PROGRESS;
    }
    throw new IllegalStateException("Unexpected doProgress response: " + response);
  }

  @Override
  public @Nullable NotificationValue takeNotification(int handle) {
    NotificationValue value =
        this.stateContext
            .getCurrentState()
            .takeNotification(handle, this.stateContext)
            .orElse(null);

    // Keep the implicit-cancellation tracking in sync: if the handler consumes an invocation-id
    // notification we tracked, remember it so we don't try to re-resolve it during cancellation.
    if (value instanceof NotificationValue.InvocationId invocationId) {
      for (TrackedInvocationId tracked : this.trackedInvocationIds) {
        if (tracked.handle == handle) {
          tracked.invocationId = invocationId.invocationId();
          break;
        }
      }
    }

    return value;
  }

  @Override
  public Input input() {
    return this.stateContext.getCurrentState().processInputCommand(this.stateContext);
  }

  @Override
  public int stateGet(String key) {
    LOG.debug("Executing 'Get state {}'", key);
    return this.stateContext.getCurrentState().processStateGetCommand(key, this.stateContext);
  }

  @Override
  public int stateGetKeys() {
    LOG.debug("Executing 'Get state keys'");
    return this.stateContext.getCurrentState().processStateGetKeysCommand(this.stateContext);
  }

  @Override
  public void stateSet(String key, Slice bytes) {
    LOG.debug("Executing 'Set state {}'", key);
    ByteString keyBuffer = ByteString.copyFromUtf8(key);
    this.stateContext.getEagerState().set(keyBuffer, bytes);
    this.stateContext
        .getCurrentState()
        .processNonCompletableCommand(
            Protocol.SetStateCommandMessage.newBuilder()
                .setKey(keyBuffer)
                .setValue(Protocol.Value.newBuilder().setContent(sliceToByteString(bytes)).build())
                .build(),
            CommandAccessor.SET_STATE,
            this.stateContext);
  }

  @Override
  public void stateClear(String key) {
    LOG.debug("Executing 'Clear state {}'", key);
    ByteString keyBuffer = ByteString.copyFromUtf8(key);
    this.stateContext.getEagerState().clear(keyBuffer);
    this.stateContext
        .getCurrentState()
        .processNonCompletableCommand(
            Protocol.ClearStateCommandMessage.newBuilder().setKey(keyBuffer).build(),
            CommandAccessor.CLEAR_STATE,
            this.stateContext);
  }

  @Override
  public void stateClearAll() {
    LOG.debug("Executing 'Clear all state'");
    this.stateContext.getEagerState().clearAll();
    this.stateContext
        .getCurrentState()
        .processNonCompletableCommand(
            Protocol.ClearAllStateCommandMessage.getDefaultInstance(),
            CommandAccessor.CLEAR_ALL_STATE,
            this.stateContext);
  }

  @Override
  public int sleep(Duration duration, @Nullable String name) {
    LOG.debug("Executing 'Sleeping for {}'", duration);
    var completionId = this.stateContext.getJournal().nextCompletionNotificationId();

    var sleepCommandBuilder =
        Protocol.SleepCommandMessage.newBuilder()
            .setWakeUpTime(Instant.now().toEpochMilli() + duration.toMillis())
            .setResultCompletionId(completionId);
    if (name != null) {
      sleepCommandBuilder.setName(name);
    }

    return this.stateContext.getCurrentState()
        .processCompletableCommand(
            sleepCommandBuilder.build(),
            CommandAccessor.SLEEP,
            new int[] {completionId},
            this.stateContext)[0];
  }

  @Override
  public CallHandle call(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers) {
    LOG.debug("Executing 'Call {}'", target);
    if (idempotencyKey != null && idempotencyKey.isBlank()) {
      throw ProtocolException.idempotencyKeyIsEmpty();
    }

    var invocationIdCompletionId = this.stateContext.getJournal().nextCompletionNotificationId();
    var callCompletionId = this.stateContext.getJournal().nextCompletionNotificationId();

    var callCommandBuilder =
        Protocol.CallCommandMessage.newBuilder()
            .setServiceName(target.getService())
            .setHandlerName(target.getHandler())
            .setParameter(sliceToByteString(payload))
            .setInvocationIdNotificationIdx(invocationIdCompletionId)
            .setResultCompletionId(callCompletionId);
    if (target.getKey() != null) {
      callCommandBuilder.setKey(target.getKey());
    }
    if (idempotencyKey != null) {
      callCommandBuilder.setIdempotencyKey(idempotencyKey);
    }
    if (headers != null) {
      for (var header : headers) {
        callCommandBuilder.addHeaders(
            Protocol.Header.newBuilder()
                .setKey(header.getKey())
                .setValue(header.getValue())
                .build());
      }
    }

    var notificationHandles =
        this.stateContext
            .getCurrentState()
            .processCompletableCommand(
                callCommandBuilder.build(),
                CommandAccessor.CALL,
                new int[] {invocationIdCompletionId, callCompletionId},
                this.stateContext);

    // Track the invocation-id handle for implicit cancellation of child calls.
    this.trackedInvocationIds.add(new TrackedInvocationId(notificationHandles[0]));

    return new CallHandle(notificationHandles[0], notificationHandles[1]);
  }

  @Override
  public int send(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    if (delay != null && !delay.isZero()) {
      LOG.debug("Executing 'Delayed send {} with delay {}'", target, delay);
    } else {
      LOG.debug("Executing 'Send {}'", target);
    }
    if (idempotencyKey != null && idempotencyKey.isBlank()) {
      throw ProtocolException.idempotencyKeyIsEmpty();
    }

    var invocationIdCompletionId = this.stateContext.getJournal().nextCompletionNotificationId();

    var sendCommandBuilder =
        Protocol.OneWayCallCommandMessage.newBuilder()
            .setServiceName(target.getService())
            .setHandlerName(target.getHandler())
            .setParameter(sliceToByteString(payload))
            .setInvocationIdNotificationIdx(invocationIdCompletionId);
    if (target.getKey() != null) {
      sendCommandBuilder.setKey(target.getKey());
    }
    if (idempotencyKey != null) {
      sendCommandBuilder.setIdempotencyKey(idempotencyKey);
    }
    if (headers != null) {
      for (var header : headers) {
        sendCommandBuilder.addHeaders(
            Protocol.Header.newBuilder()
                .setKey(header.getKey())
                .setValue(header.getValue())
                .build());
      }
    }
    if (delay != null && !delay.isZero()) {
      sendCommandBuilder.setInvokeTime(Instant.now().toEpochMilli() + delay.toMillis());
    }

    return this.stateContext.getCurrentState()
        .processCompletableCommand(
            sendCommandBuilder.build(),
            CommandAccessor.ONE_WAY_CALL,
            new int[] {invocationIdCompletionId},
            this.stateContext)[0];
  }

  @Override
  public Awakeable awakeable() {
    LOG.debug("Executing 'Create awakeable'");

    var signalId = this.stateContext.getJournal().nextSignalNotificationId();

    var signalHandle =
        this.stateContext
            .getCurrentState()
            .createSignalHandle(new NotificationId.SignalId(signalId), this.stateContext);

    // Encode awakeable id
    String awakeableId = Util.awakeableIdStr(this.stateContext.getStartInfo().id(), signalId);

    return new Awakeable(awakeableId, signalHandle);
  }

  @Override
  public void completeAwakeable(String awakeableId, Slice value) {
    LOG.debug("Executing 'Complete awakeable {} with success'", awakeableId);
    completeAwakeable(
        awakeableId,
        builder ->
            builder.setValue(
                Protocol.Value.newBuilder().setContent(sliceToByteString(value)).build()));
  }

  @Override
  public void completeAwakeable(String awakeableId, TerminalException exception) {
    LOG.debug("Executing 'Complete awakeable {} with failure'", awakeableId);
    verifyErrorMetadataFeatureSupport(exception);
    completeAwakeable(awakeableId, builder -> builder.setFailure(toProtocolFailure(exception)));
  }

  private void completeAwakeable(
      String awakeableId, Consumer<Protocol.CompleteAwakeableCommandMessage.Builder> filler) {
    var builder = Protocol.CompleteAwakeableCommandMessage.newBuilder().setAwakeableId(awakeableId);
    filler.accept(builder);

    this.stateContext
        .getCurrentState()
        .processNonCompletableCommand(
            builder.build(), CommandAccessor.COMPLETE_AWAKEABLE, this.stateContext);
  }

  @Override
  public int createSignalHandle(String signalName) {
    LOG.debug("Executing 'Create signal handle {}'", signalName);

    return this.stateContext
        .getCurrentState()
        .createSignalHandle(new NotificationId.SignalName(signalName), this.stateContext);
  }

  @Override
  public void completeSignal(String targetInvocationId, String signalName, Slice value) {
    LOG.debug(
        "Executing 'Complete signal {} to invocation {} with success'",
        signalName,
        targetInvocationId);
    this.completeSignal(
        targetInvocationId,
        signalName,
        builder ->
            builder.setValue(
                Protocol.Value.newBuilder().setContent(sliceToByteString(value)).build()));
  }

  @Override
  public void completeSignal(
      String targetInvocationId, String signalName, TerminalException exception) {
    LOG.debug(
        "Executing 'Complete signal {} to invocation {} with failure'",
        signalName,
        targetInvocationId);
    verifyErrorMetadataFeatureSupport(exception);
    this.completeSignal(
        targetInvocationId,
        signalName,
        builder -> builder.setFailure(toProtocolFailure(exception)));
  }

  private void completeSignal(
      String targetInvocationId,
      String signalName,
      Consumer<Protocol.SendSignalCommandMessage.Builder> filler) {
    var builder =
        Protocol.SendSignalCommandMessage.newBuilder()
            .setTargetInvocationId(targetInvocationId)
            .setName(signalName);
    filler.accept(builder);

    this.stateContext
        .getCurrentState()
        .processNonCompletableCommand(
            builder.build(), CommandAccessor.SEND_SIGNAL, this.stateContext);
  }

  @Override
  public int promiseGet(String key) {
    LOG.debug("Executing 'Await promise {}'", key);
    var completionId = this.stateContext.getJournal().nextCompletionNotificationId();
    return this.stateContext.getCurrentState()
        .processCompletableCommand(
            Protocol.GetPromiseCommandMessage.newBuilder()
                .setKey(key)
                .setResultCompletionId(completionId)
                .build(),
            CommandAccessor.GET_PROMISE,
            new int[] {completionId},
            this.stateContext)[0];
  }

  @Override
  public int promisePeek(String key) {
    LOG.debug("Executing 'Peek promise {}'", key);
    var completionId = this.stateContext.getJournal().nextCompletionNotificationId();
    return this.stateContext.getCurrentState()
        .processCompletableCommand(
            Protocol.PeekPromiseCommandMessage.newBuilder()
                .setKey(key)
                .setResultCompletionId(completionId)
                .build(),
            CommandAccessor.PEEK_PROMISE,
            new int[] {completionId},
            this.stateContext)[0];
  }

  @Override
  public int promiseComplete(String key, Slice value) {
    LOG.debug("Executing 'Complete promise {} with success'", key);
    return this.promiseComplete(
        key,
        builder ->
            builder.setCompletionValue(
                Protocol.Value.newBuilder().setContent(sliceToByteString(value)).build()));
  }

  @Override
  public int promiseComplete(String key, TerminalException exception) {
    LOG.debug("Executing 'Complete promise {} with failure'", key);
    verifyErrorMetadataFeatureSupport(exception);
    return this.promiseComplete(
        key, builder -> builder.setCompletionFailure(toProtocolFailure(exception)));
  }

  private int promiseComplete(
      String key, Consumer<Protocol.CompletePromiseCommandMessage.Builder> filler) {
    var completionId = this.stateContext.getJournal().nextCompletionNotificationId();

    var builder =
        Protocol.CompletePromiseCommandMessage.newBuilder()
            .setResultCompletionId(completionId)
            .setKey(key);
    filler.accept(builder);

    return this.stateContext.getCurrentState()
        .processCompletableCommand(
            builder.build(),
            CommandAccessor.COMPLETE_PROMISE,
            new int[] {completionId},
            this.stateContext)[0];
  }

  @Override
  public RunResultHandle run(String name) {
    LOG.debug("Executing 'Created run {}'", name);
    return this.stateContext.getCurrentState().processRunCommand(name, this.stateContext);
  }

  @Override
  public void proposeRunCompletion(int handle, Slice value) {
    LOG.debug("Executing 'Run completed with success'");
    this.stateContext.getCurrentState().proposeRunCompletion(handle, value, this.stateContext);
  }

  @Override
  public void proposeRunCompletion(int handle, TerminalException terminalException) {
    LOG.debug("Executing 'Run completed with terminal failure'");
    verifyErrorMetadataFeatureSupport(terminalException);
    this.stateContext
        .getCurrentState()
        .proposeRunCompletion(handle, terminalException, Duration.ZERO, null, this.stateContext);
  }

  @Override
  public void proposeRunCompletion(
      int handle,
      Throwable exception,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    LOG.debug("Executing 'Run completed with retryable failure'");
    this.stateContext
        .getCurrentState()
        .proposeRunCompletion(handle, exception, attemptDuration, retryPolicy, this.stateContext);
  }

  @Override
  public void cancelInvocation(String targetInvocationId) {
    LOG.debug("Executing 'Cancel invocation {}'", targetInvocationId);
    this.stateContext
        .getCurrentState()
        .processNonCompletableCommand(
            Protocol.SendSignalCommandMessage.newBuilder()
                .setTargetInvocationId(targetInvocationId)
                .setIdx(CANCEL_SIGNAL_ID)
                .setVoid(Protocol.Void.getDefaultInstance())
                .build(),
            CommandAccessor.SEND_SIGNAL,
            this.stateContext);
  }

  @Override
  public int attachInvocation(String invocationId) {
    LOG.debug("Executing 'Attach invocation {}'", invocationId);
    var completionId = this.stateContext.getJournal().nextCompletionNotificationId();
    return this.stateContext.getCurrentState()
        .processCompletableCommand(
            Protocol.AttachInvocationCommandMessage.newBuilder()
                .setInvocationId(invocationId)
                .setResultCompletionId(completionId)
                .build(),
            CommandAccessor.ATTACH_INVOCATION,
            new int[] {completionId},
            this.stateContext)[0];
  }

  @Override
  public int getInvocationOutput(String invocationId) {
    LOG.debug("Executing 'Get invocation output {}'", invocationId);
    var completionId = this.stateContext.getJournal().nextCompletionNotificationId();
    return this.stateContext.getCurrentState()
        .processCompletableCommand(
            Protocol.GetInvocationOutputCommandMessage.newBuilder()
                .setInvocationId(invocationId)
                .setResultCompletionId(completionId)
                .build(),
            CommandAccessor.GET_INVOCATION_OUTPUT,
            new int[] {completionId},
            this.stateContext)[0];
  }

  @Override
  public void writeOutput(Slice value) {
    LOG.debug("Executing 'Write invocation output with success'");
    this.stateContext
        .getCurrentState()
        .processNonCompletableCommand(
            Protocol.OutputCommandMessage.newBuilder()
                .setValue(Protocol.Value.newBuilder().setContent(sliceToByteString(value)).build())
                .build(),
            CommandAccessor.OUTPUT,
            this.stateContext);
  }

  @Override
  public void writeOutput(TerminalException exception) {
    LOG.debug("Executing 'Write invocation output with failure'");
    verifyErrorMetadataFeatureSupport(exception);
    this.stateContext
        .getCurrentState()
        .processNonCompletableCommand(
            Protocol.OutputCommandMessage.newBuilder()
                .setFailure(toProtocolFailure(exception))
                .build(),
            CommandAccessor.OUTPUT,
            this.stateContext);
  }

  @Override
  public void end() {
    this.stateContext.getCurrentState().end(this.stateContext);
  }

  @Override
  public void close() {
    this.stateContext.getStateHolder().transition(new ClosedState());
    this.stateContext.closeOutputSubscriber();
  }

  private void verifyErrorMetadataFeatureSupport(TerminalException exception) {
    if (!exception.getMetadata().isEmpty()
        && stateContext.getNegotiatedProtocolVersion().getNumber()
            < Protocol.ServiceProtocolVersion.V6.getNumber()) {
      throw ProtocolException.unsupportedFeature(
          "terminal error metadata",
          Protocol.ServiceProtocolVersion.V6,
          stateContext.getNegotiatedProtocolVersion());
    }
  }

  /**
   * Output sink registered with the {@link StateContext}. The legacy state machine pushed encoded
   * messages straight to a downstream {@link Flow.Subscriber}; here we encode and buffer them so
   * they can be drained via {@link #takeOutput()}.
   */
  private static final class BufferingMessageSink implements Flow.Subscriber<MessageLite> {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(MessageLite item) {
      ByteBuffer encoded = ByteBuffer.allocate(MessageEncoder.encodeLength(item));
      MessageEncoder.encode(encoded, item);
      byte[] bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      buffer.writeBytes(bytes);
    }

    @Override
    public void onError(Throwable throwable) {}

    @Override
    public void onComplete() {}

    byte[] take() {
      byte[] out = buffer.toByteArray();
      buffer.reset();
      return out;
    }
  }
}
