// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.Output;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.common.function.ThrowingRunnable;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.common.*;
import dev.restate.sdk.core.AsyncResults.AsyncResultInternal;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceType;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of the {@link HandlerContext}.
 *
 * <p>The state machine is not thread safe, hence every interaction with it is executed on the
 * {@code coreExecutor}: the public methods invoked by user code switch to it, while the private
 * methods assume they're already running on it.
 */
final class HandlerContextImpl implements HandlerContext {

  private final StateMachine stateMachine;
  private final ExternalProgressChannel externalProgressChannel;
  private final Consumer<Slice> outputSink;
  private final Executor coreExecutor;

  private final HandlerRequest handlerRequest;
  private final HeadersAccessor attemptHeaders;
  private final @Nullable String objectKey;
  private final ServiceType serviceType;
  private final @Nullable HandlerType handlerType;

  private final HashMap<Integer, Consumer<RunCompleter>> scheduledRuns;

  HandlerContextImpl(
      StateMachine vm,
      ExternalProgressChannel externalProgressChannel,
      Consumer<Slice> outputSink,
      Executor coreExecutor,
      String serviceName,
      String handlerName,
      ServiceType serviceType,
      @Nullable HandlerType handlerType,
      Context otelContext,
      HeadersAccessor attemptHeaders,
      StateMachine.Input input) {
    this.stateMachine = vm;
    this.externalProgressChannel = externalProgressChannel;
    this.outputSink = outputSink;
    this.coreExecutor = coreExecutor;

    this.handlerRequest =
        new HandlerRequest(
            new InvocationIdImpl(input.invocationId(), input.randomSeed()),
            otelContext,
            input.input(),
            input.headersAsMap(),
            input.scope(),
            input.limitKey(),
            input.idempotencyKey(),
            serviceName,
            handlerName);
    this.attemptHeaders = attemptHeaders;
    this.objectKey = input.key() != null && !input.key().isEmpty() ? input.key() : null;
    this.serviceType = serviceType;
    this.handlerType = handlerType;
    this.scheduledRuns = new HashMap<>();
  }

  private static void parseSuccessOrFailure(
      StateMachine.NotificationValue s, CompletableFuture<Slice> cf) {
    if (s instanceof StateMachine.NotificationValue.Success success) {
      cf.complete(success.slice());
    } else if (s instanceof StateMachine.NotificationValue.Failure failure) {
      cf.completeExceptionally(failure.exception());
    } else {
      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
    }
  }

  private static void parseEmptyOrSuccessOrFailure(
      StateMachine.NotificationValue s, CompletableFuture<Output<Slice>> cf) {
    if (s instanceof StateMachine.NotificationValue.Empty) {
      cf.complete(Output.notReady());
    } else if (s instanceof StateMachine.NotificationValue.Success success) {
      cf.complete(Output.ready(success.slice()));
    } else if (s instanceof StateMachine.NotificationValue.Failure failure) {
      cf.completeExceptionally(failure.exception());
    } else {
      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
    }
  }

  private static void parseEmptyOrFailure(
      StateMachine.NotificationValue s, CompletableFuture<Void> cf) {
    if (s instanceof StateMachine.NotificationValue.Empty) {
      cf.complete(null);
    } else if (s instanceof StateMachine.NotificationValue.Failure failure) {
      cf.completeExceptionally(failure.exception());
    } else {
      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
    }
  }

  @Override
  public String objectKey() {
    return this.objectKey;
  }

  @Override
  public HandlerRequest request() {
    return this.handlerRequest;
  }

  @Override
  public HeadersAccessor attemptHeaders() {
    return this.attemptHeaders;
  }

  @Override
  public boolean canReadState() {
    return serviceType == ServiceType.VIRTUAL_OBJECT || serviceType == ServiceType.WORKFLOW;
  }

  @Override
  public boolean canWriteState() {
    return handlerType == HandlerType.EXCLUSIVE || handlerType == HandlerType.WORKFLOW;
  }

  @Override
  public boolean canReadPromises() {
    return serviceType == ServiceType.WORKFLOW;
  }

  @Override
  public boolean canWritePromises() {
    return serviceType == ServiceType.WORKFLOW;
  }

  /**
   * @return fully qualified method name in the form {fullyQualifiedServiceName}/{methodName}
   */
  String getFullyQualifiedMethodName() {
    return this.handlerRequest.serviceName() + "/" + this.handlerRequest.handlerName();
  }

  StateMachine.InvocationState getInvocationState() {
    return this.stateMachine.state();
  }

  @Override
  public CompletableFuture<AsyncResult<Optional<Slice>>> get(String name) {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.stateGet(name),
                (s, cf) -> {
                  if (s instanceof StateMachine.NotificationValue.Empty) {
                    cf.complete(Optional.empty());
                  } else if (s instanceof StateMachine.NotificationValue.Success success) {
                    cf.complete(Optional.of(success.slice()));
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<AsyncResult<Collection<String>>> getKeys() {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.stateGetKeys(),
                (s, cf) -> {
                  if (s instanceof StateMachine.NotificationValue.StateKeys stateKeys) {
                    cf.complete(stateKeys.stateKeys());
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<Void> clear(String name) {
    return onCoreExecutor(() -> this.stateMachine.stateClear(name));
  }

  @Override
  public CompletableFuture<Void> clearAll() {
    return onCoreExecutor(this.stateMachine::stateClearAll);
  }

  @Override
  public CompletableFuture<Void> set(String name, Slice value) {
    return onCoreExecutor(() -> this.stateMachine.stateSet(name, value));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> timer(Duration duration, String name) {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sleep(duration, name),
                (s, cf) -> {
                  if (s instanceof StateMachine.NotificationValue.Empty) {
                    cf.complete(null);
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<CallResult> call(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers) {
    return onCoreExecutor(
        () -> {
          StateMachine.CallHandle callHandle =
              this.stateMachine.call(
                  target, parameter, idempotencyKey, target.getScope(), limitKey, headers);

          AsyncResultInternal<String> invocationIdAsyncResult =
              AsyncResults.single(this, callHandle.invocationIdHandle(), invocationIdCompleter());

          AsyncResult<Slice> callAsyncResult =
              AsyncResults.single(
                  this, callHandle.resultHandle(), HandlerContextImpl::parseSuccessOrFailure);

          return new CallResult(invocationIdAsyncResult, callAsyncResult);
        });
  }

  @Override
  public CompletableFuture<AsyncResult<String>> send(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    return onCoreExecutor(
        () -> {
          int sendHandle =
              this.stateMachine.send(
                  target, parameter, idempotencyKey, target.getScope(), limitKey, headers, delay);

          return AsyncResults.single(this, sendHandle, invocationIdCompleter());
        });
  }

  private static AsyncResults.Completer<String> invocationIdCompleter() {
    return (s, cf) -> {
      if (s instanceof StateMachine.NotificationValue.InvocationId invocationId) {
        cf.complete(invocationId.invocationId());
      } else {
        throw ProtocolException.unexpectedNotificationVariant(s.getClass());
      }
    };
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> submitRun(
      @Nullable String name, Consumer<RunCompleter> closure) {
    return onCoreExecutor(
        () -> {
          StateMachine.RunResultHandle run = this.stateMachine.run(name);
          if (!run.replayed()) {
            // Retain the run closure only if the run wasn't replayed.
            this.scheduledRuns.put(run.handle(), closure);
          }
          return AsyncResults.single(this, run.handle(), HandlerContextImpl::parseSuccessOrFailure);
        });
  }

  @Override
  public CompletableFuture<Awakeable> awakeable() {
    return onCoreExecutor(
        () -> {
          StateMachine.Awakeable awakeable = this.stateMachine.awakeable();
          return new Awakeable(
              awakeable.awakeableId(),
              AsyncResults.single(
                  this, awakeable.handle(), HandlerContextImpl::parseSuccessOrFailure));
        });
  }

  @Override
  public CompletableFuture<Void> resolveAwakeable(String id, Slice payload) {
    return onCoreExecutor(() -> this.stateMachine.completeAwakeable(id, payload));
  }

  @Override
  public CompletableFuture<Void> rejectAwakeable(String id, TerminalException reason) {
    return onCoreExecutor(() -> this.stateMachine.completeAwakeable(id, reason));
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> promise(String key) {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promiseGet(key),
                HandlerContextImpl::parseSuccessOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> peekPromise(String key) {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promisePeek(key),
                HandlerContextImpl::parseEmptyOrSuccessOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> resolvePromise(String key, Slice payload) {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promiseComplete(key, payload),
                HandlerContextImpl::parseEmptyOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> rejectPromise(String key, TerminalException reason) {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promiseComplete(key, reason),
                HandlerContextImpl::parseEmptyOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> signal(String name) {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.createSignalHandle(name),
                HandlerContextImpl::parseSuccessOrFailure));
  }

  @Override
  public CompletableFuture<Void> resolveSignal(String invocationId, String name, Slice payload) {
    return onCoreExecutor(() -> this.stateMachine.completeSignal(invocationId, name, payload));
  }

  @Override
  public CompletableFuture<Void> rejectSignal(
      String invocationId, String name, TerminalException reason) {
    return onCoreExecutor(() -> this.stateMachine.completeSignal(invocationId, name, reason));
  }

  @Override
  public CompletableFuture<Void> cancelInvocation(String invocationId) {
    return onCoreExecutor(() -> this.stateMachine.cancelInvocation(invocationId));
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> attachInvocation(String invocationId) {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.attachInvocation(invocationId),
                HandlerContextImpl::parseSuccessOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> getInvocationOutput(String invocationId) {
    return onCoreExecutor(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.getInvocationOutput(invocationId),
                HandlerContextImpl::parseEmptyOrSuccessOrFailure));
  }

  @SuppressWarnings("removal")
  @Deprecated
  @Override
  public CompletableFuture<Void> writeOutput(Slice value) {
    return onCoreExecutor(() -> this.stateMachine.writeOutput(value));
  }

  @SuppressWarnings("removal")
  @Deprecated
  @Override
  public CompletableFuture<Void> writeOutput(TerminalException throwable) {
    return onCoreExecutor(() -> this.stateMachine.writeOutput(throwable));
  }

  void pollAsyncResult(AsyncResultInternal<?> asyncResult) {
    coreExecutor.execute(
        () -> {
          try {
            this.pumpOutput();
            this.pollAsyncResultInner(asyncResult);
          } catch (Throwable e) {
            this.failWithoutContextSwitch(e);
          }
        });
  }

  private void pollAsyncResultInner(AsyncResultInternal<?> asyncResult) {
    while (true) {
      // Let's start by trying to complete it
      try {
        asyncResult.tryComplete(this::takeNotification);
      } catch (Throwable e) {
        this.failWithoutContextSwitch(e);
        asyncResult.publicFuture().completeExceptionally(AbortedExecutionException.INSTANCE);
        return;
      }
      // If done, nothing more to do here.
      if (asyncResult.isDone()) {
        return;
      }

      // Not ready yet: make progress on what's still uncompleted. doAwait walks the async-result
      // tree directly (asyncResult is the await tree) and returns null when nothing is left to
      // await.
      StateMachine.AwaitResult response;
      try {
        response = this.stateMachine.doAwait(asyncResult);
      } catch (Throwable e) {
        this.failWithoutContextSwitch(e);
        asyncResult.publicFuture().completeExceptionally(AbortedExecutionException.INSTANCE);
        return;
      }

      if (response instanceof StateMachine.AwaitResult.AnyCompleted) {
        // Let it loop now
      } else if (response instanceof StateMachine.AwaitResult.WaitExternalProgress) {
        this.pumpOutput();
        this.externalProgressChannel.awaitNext(() -> this.pollAsyncResultInner(asyncResult));
        return;
      } else if (response instanceof StateMachine.AwaitResult.CancelSignalReceived) {
        asyncResult.tryCancel();
        return;
      } else if (response instanceof StateMachine.AwaitResult.ExecuteRun) {
        triggerScheduledRun(((StateMachine.AwaitResult.ExecuteRun) response).handle());
        // Let it loop now
      }
    }
  }

  Optional<StateMachine.NotificationValue> takeNotification(int handle) {
    return Optional.ofNullable(this.stateMachine.takeNotification(handle));
  }

  private void proposeRunSuccess(int runHandle, Slice toWrite) {
    coreExecutor.execute(
        () -> {
          try {
            this.stateMachine.proposeRunCompletion(runHandle, toWrite);
          } catch (Throwable e) {
            this.failWithoutContextSwitch(e);
          }
          this.pumpOutput();
          this.externalProgressChannel.signal();
        });
  }

  private void proposeRunFailure(
      int runHandle,
      Throwable throwable,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    coreExecutor.execute(
        () -> {
          try {
            if (throwable instanceof TerminalException) {
              this.stateMachine.proposeRunCompletion(runHandle, (TerminalException) throwable);
            } else {
              this.stateMachine.proposeRunCompletion(
                  runHandle, throwable, attemptDuration, retryPolicy);
            }
          } catch (Throwable e) {
            this.failWithoutContextSwitch(e);
          }
          this.pumpOutput();
          this.externalProgressChannel.signal();
        });
  }

  private void triggerScheduledRun(int handle) {
    var consumer =
        Objects.requireNonNull(
            this.scheduledRuns.get(handle), "The given handle doesn't exist, this is an SDK bug");
    var startTime = Instant.now();
    consumer.accept(
        new RunCompleter() {
          @Override
          public void proposeSuccess(Slice toWrite) {
            proposeRunSuccess(handle, toWrite);
          }

          @Override
          public void proposeFailure(Throwable toWrite, @Nullable RetryPolicy retryPolicy) {
            proposeRunFailure(
                handle, toWrite, Duration.between(startTime, Instant.now()), retryPolicy);
          }
        });
  }

  private void pumpOutput() {
    Slice chunk = stateMachine.takeOutput();
    if (chunk.readableBytes() > 0) outputSink.accept(chunk);
  }

  @Override
  public void fail(Throwable cause) {
    coreExecutor.execute(() -> this.failWithoutContextSwitch(cause));
  }

  private void failWithoutContextSwitch(Throwable cause) {
    this.stateMachine.notifyError(cause);
  }

  @Override
  public AsyncResult<Integer> createAnyAsyncResult(List<AsyncResult<?>> children) {
    return AsyncResults.any(
        this,
        children.stream().map(dr -> (AsyncResultInternal<?>) dr).collect(Collectors.toList()));
  }

  @Override
  public AsyncResult<Void> createAllAsyncResult(List<AsyncResult<?>> children) {
    return AsyncResults.all(
        this,
        children.stream().map(dr -> (AsyncResultInternal<?>) dr).collect(Collectors.toList()));
  }

  // -- Executor switching and failure propagation

  /**
   * Run the given state machine operation on the core executor.
   *
   * <p>If the operation fails, the invocation fails too, and the returned future completes with
   * {@link AbortedExecutionException}.
   */
  private CompletableFuture<Void> onCoreExecutor(ThrowingRunnable operation) {
    return onCoreExecutor(
        () -> {
          operation.run();
          return null;
        });
  }

  /**
   * Run the given state machine operation on the core executor.
   *
   * <p>If the operation fails, the invocation fails too, and the returned future completes with
   * {@link AbortedExecutionException}.
   */
  private <T> CompletableFuture<T> onCoreExecutor(ThrowingSupplier<T> operation) {
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                return CompletableFuture.completedFuture(operation.get());
              } catch (Throwable e) {
                this.failWithoutContextSwitch(e);
                return CompletableFuture.<T>failedFuture(AbortedExecutionException.INSTANCE);
              }
            },
            coreExecutor)
        .thenCompose(Function.identity());
  }
}
