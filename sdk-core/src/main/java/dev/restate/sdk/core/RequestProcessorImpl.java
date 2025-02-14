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
import dev.restate.sdk.core.statemachine.StateMachine;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.types.TerminalException;
import io.opentelemetry.context.Context;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

final class RequestProcessorImpl implements RequestProcessor {

  private static final Logger LOG = LogManager.getLogger(RequestProcessorImpl.class);

  private final String fullyQualifiedHandlerName;
  private final StateMachine stateMachine;
  private final HandlerDefinition<Object, Object> handlerDefinition;
  private final Context otelContext;
  private final EndpointRequestHandler.LoggingContextSetter loggingContextSetter;
  private final Executor syscallsExecutor;

  @SuppressWarnings("unchecked")
  public RequestProcessorImpl(
      String fullyQualifiedHandlerName,
      StateMachine stateMachine,
      HandlerDefinition<?, ?> handlerDefinition,
      Context otelContext,
      EndpointRequestHandler.LoggingContextSetter loggingContextSetter,
      Executor syscallExecutor) {
    this.fullyQualifiedHandlerName = fullyQualifiedHandlerName;
    this.stateMachine = stateMachine;
    this.otelContext = otelContext;
    this.loggingContextSetter = loggingContextSetter;
    this.handlerDefinition = (HandlerDefinition<Object, Object>) handlerDefinition;
    this.syscallsExecutor = syscallExecutor;
  }

  // Flow methods implementation

  @Override
  public void subscribe(Flow.Subscriber<? super Slice> subscriber) {
    LOG.trace("Start processing invocation");
    this.stateMachine.subscribe(subscriber);
    stateMachine
        .waitForReady()
        .thenCompose(v -> this.onReady())
        .whenComplete(
            (v, t) -> {
              if (t != null) {
                this.onError(t);
              }
            });
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.stateMachine.onSubscribe(subscription);
  }

  @Override
  public void onNext(Slice item) {
    this.stateMachine.onNext(item);
  }

  @Override
  public void onError(Throwable throwable) {
    this.stateMachine.onError(throwable);
  }

  @Override
  public void onComplete() {
    this.stateMachine.onComplete();
  }

  @Override
  public int statusCode() {
    return 200;
  }

  @Override
  public String responseContentType() {
    return this.stateMachine.getResponseContentType();
  }

  private CompletableFuture<Void> onReady() {
    StateMachine.Input input = stateMachine.input();

    if (input == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("State machine input is empty"));
    }

    this.loggingContextSetter.set(
        EndpointRequestHandler.LoggingContextSetter.INVOCATION_ID_KEY,
        input.invocationId().toString());

    // Prepare HandlerContext object
    HandlerContextInternal contextInternal =
        this.syscallsExecutor != null
            ? new ExecutorSwitchingHandlerContextImpl(
                fullyQualifiedHandlerName, stateMachine, otelContext, input, this.syscallsExecutor)
            : new HandlerContextImpl(fullyQualifiedHandlerName, stateMachine, otelContext, input);

    CompletableFuture<Slice> userCodeFuture =
        this.handlerDefinition
            .getRunner()
            .run(
                contextInternal,
                handlerDefinition.getRequestSerde(),
                handlerDefinition.getResponseSerde()
            );

    return userCodeFuture.handle(
        (slice, t) -> {
          if (t != null) {
            this.end(contextInternal, t);
          } else {
            this.writeOutputAndEnd(contextInternal, slice);
          }
          return null;
        });
  }

  private CompletableFuture<Void> writeOutputAndEnd(
      HandlerContextInternal contextInternal, Slice output) {
    return contextInternal.writeOutput(output).thenAccept(v -> this.end(contextInternal, null));
  }

  private CompletableFuture<Void> end(
      HandlerContextInternal contextInternal, @Nullable Throwable exception) {
    if (exception == null || ExceptionUtils.containsSuspendedException(exception)) {
      contextInternal.close();
    } else {
      LOG.warn("Error when processing the invocation", exception);
      if (ExceptionUtils.isTerminalException(exception)) {
        return contextInternal
            .writeOutput((TerminalException) exception)
            .thenAccept(
                v -> {
                  LOG.trace("Closed correctly with non ok exception", exception);
                  contextInternal.close();
                });
      } else {
        contextInternal.fail(exception);
      }
    }
    return CompletableFuture.completedFuture(null);
  }
}
