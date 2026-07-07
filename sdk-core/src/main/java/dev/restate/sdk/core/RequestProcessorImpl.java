// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.Slice;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import io.opentelemetry.context.Context;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/** Handles I/O (Flow.Processor), pre-flight replay buffering, and user code orchestration. */
final class RequestProcessorImpl implements RequestProcessor {

  private static final Logger LOG = LogManager.getLogger(RequestProcessorImpl.class);

  private enum State {
    /** Buffering replay input — waiting for {@code vm.isReadyToExecute()}. */
    WAITING_READY_TO_EXECUTE,
    /** Handler user code is running. */
    RUNNING_HANDLER,
    /** Handler has finished. */
    CLOSED
  }

  private State state = State.WAITING_READY_TO_EXECUTE;

  private final String serviceName;
  private final String handlerName;
  private final StateMachine stateMachine;
  private final ServiceType serviceType;
  private final HandlerDefinition<Object, Object> handlerDefinition;
  private final Context otelContext;
  private final HeadersAccessor attemptHeaders;
  private final Executor syscallsExecutor;
  private final AtomicReference<Runnable> onClosedInvocationStreamHook;
  private final ExternalProgressChannel externalProgressChannel;

  // ------- I/O

  private Flow.@Nullable Subscriber<? super Slice> outputSubscriber;
  private Flow.@Nullable Subscription inputSubscription;

  @SuppressWarnings("unchecked")
  RequestProcessorImpl(
      StateMachine stateMachine,
      String serviceName,
      String handlerName,
      ServiceType serviceType,
      HandlerDefinition<?, ?> handlerDefinition,
      Context otelContext,
      HeadersAccessor attemptHeaders,
      Executor syscallExecutor) {
    this.serviceName = serviceName;
    this.handlerName = handlerName;
    this.stateMachine = stateMachine;
    this.serviceType = serviceType;
    this.otelContext = otelContext;
    this.attemptHeaders = attemptHeaders;
    this.handlerDefinition = (HandlerDefinition<Object, Object>) handlerDefinition;
    this.syscallsExecutor = syscallExecutor;
    this.onClosedInvocationStreamHook = new AtomicReference<>();
    this.externalProgressChannel = new ExternalProgressChannel();
  }

  @Override
  public int statusCode() {
    return 200;
  }

  @Override
  public String responseContentType() {
    return stateMachine.getResponseContentType();
  }

  // ---------------------------------------------------------------------------
  // Flow.Publisher — output side
  // ---------------------------------------------------------------------------

  @Override
  public void subscribe(Flow.Subscriber<? super Slice> subscriber) {
    LOG.trace("Start processing invocation");
    this.outputSubscriber = subscriber;
    subscriber.onSubscribe(
        new Flow.Subscription() {
          @Override
          public void request(long n) {
            // We don't support backpressure here because writing to the output stream is driven by
            // code.
            assert n == Long.MAX_VALUE;
          }

          @Override
          public void cancel() {
            // This is called by the network layer at the very end.
            onClose();
          }
        });
  }

  // ---------------------------------------------------------------------------
  // Flow.Subscriber — input side
  // ---------------------------------------------------------------------------

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.inputSubscription = subscription;
    subscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(Slice slice) {
    if (state == State.CLOSED) return;

    try {
      stateMachine.notifyInput(slice);
      onInputEvent();
    } catch (Throwable e) {
      onError(e);
    }
  }

  // This is a generic error handling when things go south at any point
  @Override
  public void onError(Throwable throwable) {
    if (state == State.CLOSED) return;

    LOG.warn("Invocation failed", throwable);
    try {
      stateMachine.notifyError(throwable);
    } catch (Throwable ignored) {
    }

    onClose();
  }

  @Override
  public void onComplete() {
    if (state == State.CLOSED) return;
    try {
      stateMachine.notifyInputClosed();
      onInputEvent();
    } catch (Throwable e) {
      onError(e);
      return;
    }

    // We don't need it anymore
    cancelInputSubscription();
  }

  // ---------------------------------------------------------------------------
  // State machine events
  // ---------------------------------------------------------------------------

  private void onInputEvent() {
    if (state == State.WAITING_READY_TO_EXECUTE && stateMachine.isReadyToExecute()) {
      startHandler();
    } else if (state == State.RUNNING_HANDLER) {
      externalProgressChannel.signal();
    }
  }

  private void onNextOutputSlice(Slice slice) {
    if (outputSubscriber != null) {
      outputSubscriber.onNext(slice);
    }
  }

  private void onClose() {
    // Stop user code (won't have effect if not running anymore)
    Runnable cancelTask = onClosedInvocationStreamHook.get();
    if (cancelTask != null) {
      cancelTask.run();
    }

    // Unblock eventually blocked doProgress
    externalProgressChannel.signal();

    // Cancel input subscription if still there
    cancelInputSubscription();

    // Pump remaining output
    Slice chunk;
    if (outputSubscriber != null) {
      chunk = stateMachine.takeOutput();
    } else {
      chunk = Slice.EMPTY;
    }

    // Close state machine
    this.state = State.CLOSED;
    stateMachine.close();

    // Send final bits and close output subscriber
    if (chunk.readableBytes() > 0) outputSubscriber.onNext(chunk);
    outputSubscriber.onComplete();
    outputSubscriber = null;
  }

  private void onUserCodeResult(@Nullable Slice slice, @Nullable Throwable throwable) {
    if (state == State.CLOSED) {
      // Nothing to do, invocation was already closed, this is the result of the abortion afterward.
      return;
    }

    try {
      if (throwable != null) {
        if (throwable instanceof TerminalException) {
          LOG.info("Invocation ended with terminal error", throwable);
          stateMachine.writeOutput((TerminalException) throwable);
          stateMachine.end();
        } else if (ExceptionUtils.containsAbortedExecutionException(throwable)) {
          // Nothing to do
        } else {
          onError(throwable);
          return;
        }
      } else {
        stateMachine.writeOutput(Objects.requireNonNullElse(slice, Slice.EMPTY));
        stateMachine.end();
        LOG.info("Invocation ended successfully");
      }
    } catch (Throwable e) {
      // Error happened when trying to write the final bits
      onError(e);
      return;
    }

    onClose();
  }

  // ---------------------------------------------------------------------------
  // Business logic
  // ---------------------------------------------------------------------------

  private void startHandler() {
    state = State.RUNNING_HANDLER;
    LOG.info("Invocation started");

    // Get vm input
    StateMachine.Input stateMachineInput = stateMachine.input();

    HandlerContextImpl ctx =
        syscallsExecutor != null
            ? new ExecutorSwitchingHandlerContextImpl(
                stateMachine,
                externalProgressChannel,
                this::onNextOutputSlice,
                serviceName,
                handlerName,
                serviceType,
                handlerDefinition.getHandlerType(),
                otelContext,
                attemptHeaders,
                stateMachineInput,
                this.syscallsExecutor)
            : new HandlerContextImpl(
                stateMachine,
                externalProgressChannel,
                this::onNextOutputSlice,
                serviceName,
                handlerName,
                serviceType,
                handlerDefinition.getHandlerType(),
                otelContext,
                attemptHeaders,
                stateMachineInput);

    CompletableFuture<Slice> handlerResultFut =
        this.handlerDefinition
            .getRunner()
            .run(
                ctx,
                handlerDefinition.getRequestSerde(),
                handlerDefinition.getResponseSerde(),
                onClosedInvocationStreamHook);

    // Wire up the completion of the handler result back to this class.
    // Because the handler result fut gets completed on the user executor, we need to trampoline
    // back on the thread where we're executing here.
    if (this.syscallsExecutor != null) {
      handlerResultFut.whenCompleteAsync(this::onUserCodeResult, this.syscallsExecutor);
    } else {
      handlerResultFut.whenComplete(this::onUserCodeResult);
    }
  }

  private void cancelInputSubscription() {
    if (this.inputSubscription != null) {
      this.inputSubscription.cancel();
      this.inputSubscription = null;
    }
  }
}
