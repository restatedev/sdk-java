// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import static dev.restate.sdk.core.statemachine.Util.sliceToByteString;
import static dev.restate.sdk.core.statemachine.Util.toProtocolFailure;

import com.google.protobuf.ByteString;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.common.*;
import dev.restate.sdk.core.EndpointRequestHandler;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class StateMachineImpl implements StateMachine {

  private static final Logger LOG = LogManager.getLogger(StateMachineImpl.class);
  private static final String AWAKEABLE_IDENTIFIER_PREFIX = "sign_1";
  private static final int CANCEL_SIGNAL_ID = 1;

  // Callbacks
  private final CompletableFuture<Void> waitForReadyFuture = new CompletableFuture<>();
  private @NonNull Runnable nextEventListener = () -> {};

  // Java Flow and message handling
  private final MessageDecoder messageDecoder = new MessageDecoder();
  private Flow.@Nullable Subscription inputSubscription;

  // State machine context
  private final StateContext stateContext;

  StateMachineImpl(
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
  }

  // -- Few callbacks

  @Override
  public CompletableFuture<Void> waitForReady() {
    return waitForReadyFuture;
  }

  @Override
  public void onNextEvent(Runnable runnable) {
    this.nextEventListener =
        () -> {
          this.nextEventListener.run();
          runnable.run();
        };
    // Trigger this now
    if (this.stateContext.isInputClosed()) {
      this.triggerNextEventSignal();
    }
  }

  private void triggerNextEventSignal() {
    Runnable listener = this.nextEventListener;
    this.nextEventListener = () -> {};
    listener.run();
  }

  // -- IO

  @Override
  public void subscribe(Flow.Subscriber<? super Slice> subscriber) {
    var outputSubscriber = new MessageEncoder(subscriber);
    this.stateContext.registerOutputSubscriber(outputSubscriber);
    outputSubscriber.onSubscribe(
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
    try {
      this.inputSubscription = subscription;
      this.inputSubscription.request(Long.MAX_VALUE);
    } catch (Throwable e) {
      this.onError(e);
    }
  }

  @Override
  public void onNext(Slice slice) {
    try {
      LOG.trace("Received input slice");
      this.messageDecoder.offer(slice);

      boolean shouldTriggerInputListener = this.messageDecoder.isNextAvailable();
      InvocationInput invocationInput = this.messageDecoder.next();
      while (invocationInput != null) {
        LOG.trace(
            "Received input message {} {}",
            invocationInput.message().getClass(),
            invocationInput.message());

        this.stateContext
            .getCurrentState()
            .onNewMessage(invocationInput, this.stateContext, this.waitForReadyFuture);

        invocationInput = this.messageDecoder.next();
      }

      if (shouldTriggerInputListener) {
        this.triggerNextEventSignal();
      }

    } catch (Throwable e) {
      this.onError(e);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    this.stateContext.getCurrentState().hitError(throwable, null, null, this.stateContext);
    this.triggerNextEventSignal();
    cancelInputSubscription();
  }

  @Override
  public void onComplete() {
    LOG.trace("Input publisher closed");
    try {
      this.stateContext.getCurrentState().onInputClosed(this.stateContext);
    } catch (Throwable e) {
      this.onError(e);
      return;
    }
    this.triggerNextEventSignal();
    this.cancelInputSubscription();
  }

  // -- State machine

  @Override
  public String getResponseContentType() {
    return ServiceProtocol.serviceProtocolVersionToHeaderValue(
        stateContext.getNegotiatedProtocolVersion());
  }

  @Override
  public DoProgressResponse doProgress(List<Integer> anyHandle) {
    return this.stateContext.getCurrentState().doProgress(anyHandle, this.stateContext);
  }

  @Override
  public boolean isCompleted(int handle) {
    return this.stateContext.getCurrentState().isCompleted(handle);
  }

  @Override
  public Optional<NotificationValue> takeNotification(int handle) {
    return this.stateContext.getCurrentState().takeNotification(handle, this.stateContext);
  }

  @Override
  public @Nullable Input input() {
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

    return new CallHandle(notificationHandles[0], notificationHandles[1]);
  }

  @Override
  public int send(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
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
    String awakeableId =
        AWAKEABLE_IDENTIFIER_PREFIX
            + Base64.getUrlEncoder()
                .encodeToString(
                    this.stateContext
                        .getStartInfo()
                        .id()
                        .concat(ByteString.copyFrom(ByteBuffer.allocate(4).putInt(signalId).flip()))
                        .toByteArray());

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
  public int run(String name) {
    LOG.debug("Executing 'Created run {}'", name);
    return this.stateContext.getCurrentState().processRunCommand(name, this.stateContext);
  }

  @Override
  public void proposeRunCompletion(int handle, Slice value) {
    LOG.debug("Executing 'Run completed with success'");
    try {
      this.stateContext.getCurrentState().proposeRunCompletion(handle, value, this.stateContext);
    } catch (Throwable e) {
      this.onError(e);
      return;
    }
    this.triggerNextEventSignal();
  }

  @Override
  public void proposeRunCompletion(
      int handle,
      Throwable exception,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    LOG.debug("Executing 'Run completed with failure'");
    try {
      this.stateContext
          .getCurrentState()
          .proposeRunCompletion(handle, exception, attemptDuration, retryPolicy, this.stateContext);
    } catch (Throwable e) {
      this.onError(e);
      return;
    }
    this.triggerNextEventSignal();
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
    cancelInputSubscription();
  }

  @Override
  public InvocationState state() {
    return this.stateContext.getCurrentState().getInvocationState();
  }

  private void cancelInputSubscription() {
    if (this.inputSubscription != null) {
      this.inputSubscription.cancel();
      this.inputSubscription = null;
    }
  }
}
