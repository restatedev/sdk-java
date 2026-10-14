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
import dev.restate.sdk.endpoint.HeadersAccessor;
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
  public CompletableFuture<Handle<Optional<Slice>>> get(String name) {
    return onCoreExecutor(
        () -> NotificationHandle.emptyOrSuccess(this.stateMachine.stateGet(name)));
  }

  @Override
  public CompletableFuture<Handle<Collection<String>>> getKeys() {
    return onCoreExecutor(() -> NotificationHandle.stateKeys(this.stateMachine.stateGetKeys()));
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
  public CompletableFuture<Handle<Void>> timer(Duration duration, String name) {
    return onCoreExecutor(() -> NotificationHandle.empty(this.stateMachine.sleep(duration, name)));
  }

  @Override
  public CompletableFuture<CallHandles> call(
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
          return new CallHandles(
              NotificationHandle.invocationId(callHandle.invocationIdHandle()),
              NotificationHandle.successOrFailure(callHandle.resultHandle()));
        });
  }

  @Override
  public CompletableFuture<Handle<String>> send(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    return onCoreExecutor(
        () ->
            NotificationHandle.invocationId(
                this.stateMachine.send(
                    target,
                    parameter,
                    idempotencyKey,
                    target.getScope(),
                    limitKey,
                    headers,
                    delay)));
  }

  @Override
  public CompletableFuture<Handle<Slice>> submitRun(
      @Nullable String name, Consumer<RunCompleter> closure) {
    return onCoreExecutor(
        () -> {
          StateMachine.RunResultHandle run = this.stateMachine.run(name);
          if (!run.replayed()) {
            // Retain the run closure only if the run wasn't replayed.
            this.scheduledRuns.put(run.handle(), closure);
          }
          return NotificationHandle.successOrFailure(run.handle());
        });
  }

  @Override
  public CompletableFuture<Awakeable> awakeable() {
    return onCoreExecutor(
        () -> {
          StateMachine.Awakeable awakeable = this.stateMachine.awakeable();
          return new Awakeable(
              awakeable.awakeableId(), NotificationHandle.successOrFailure(awakeable.handle()));
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
  public CompletableFuture<Handle<Slice>> promise(String key) {
    return onCoreExecutor(
        () -> NotificationHandle.successOrFailure(this.stateMachine.promiseGet(key)));
  }

  @Override
  public CompletableFuture<Handle<Output<Slice>>> peekPromise(String key) {
    return onCoreExecutor(
        () -> NotificationHandle.emptyOrSuccessOrFailure(this.stateMachine.promisePeek(key)));
  }

  @Override
  public CompletableFuture<Handle<Void>> resolvePromise(String key, Slice payload) {
    return onCoreExecutor(
        () -> NotificationHandle.emptyOrFailure(this.stateMachine.promiseComplete(key, payload)));
  }

  @Override
  public CompletableFuture<Handle<Void>> rejectPromise(String key, TerminalException reason) {
    return onCoreExecutor(
        () -> NotificationHandle.emptyOrFailure(this.stateMachine.promiseComplete(key, reason)));
  }

  @Override
  public CompletableFuture<Handle<Slice>> signal(String name) {
    return onCoreExecutor(
        () -> NotificationHandle.successOrFailure(this.stateMachine.createSignalHandle(name)));
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
  public CompletableFuture<Handle<Slice>> attachInvocation(String invocationId) {
    return onCoreExecutor(
        () ->
            NotificationHandle.successOrFailure(this.stateMachine.attachInvocation(invocationId)));
  }

  @Override
  public CompletableFuture<Handle<Output<Slice>>> getInvocationOutput(String invocationId) {
    return onCoreExecutor(
        () ->
            NotificationHandle.emptyOrSuccessOrFailure(
                this.stateMachine.getInvocationOutput(invocationId)));
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

  // -- Await

  @Override
  public CompletableFuture<AwaitResult> await(UnresolvedFuture tree) {
    CompletableFuture<AwaitResult> result = new CompletableFuture<>();
    coreExecutor.execute(
        () -> {
          try {
            this.pumpOutput();
          } catch (Throwable e) {
            this.failWithoutContextSwitch(e);
            result.complete(AwaitResult.Aborted.INSTANCE);
            return;
          }
          this.awaitLoop(tree, result);
        });
    return result;
  }

  private void awaitLoop(UnresolvedFuture tree, CompletableFuture<AwaitResult> result) {
    try {
      while (true) {
        StateMachine.AwaitResult response = this.stateMachine.doAwait(tree);

        if (response instanceof StateMachine.AwaitResult.AnyCompleted) {
          // Take exactly one notification, and convert it to the result of its operation. The
          // caller re-evaluates the tree, and awaits it again if it's not completed yet.
          AwaitResult.AnyCompleted anyCompleted = this.takeFirstNotification(tree);
          if (anyCompleted == null) {
            throw new IllegalStateException(
                "The state machine returned AnyCompleted, but no notification of the awaited"
                    + " future is available. This is an SDK bug.");
          }
          result.complete(anyCompleted);
          return;
        } else if (response instanceof StateMachine.AwaitResult.WaitExternalProgress) {
          this.pumpOutput();
          this.externalProgressChannel.awaitNext(() -> this.awaitLoop(tree, result));
          return;
        } else if (response instanceof StateMachine.AwaitResult.CancelSignalReceived) {
          result.complete(AwaitResult.Cancelled.INSTANCE);
          return;
        } else if (response instanceof StateMachine.AwaitResult.ExecuteRun executeRun) {
          triggerScheduledRun(executeRun.handle());
          // Let it loop now
        }
      }
    } catch (Throwable e) {
      this.failWithoutContextSwitch(e);
      result.complete(AwaitResult.Aborted.INSTANCE);
    }
  }

  /**
   * Take the notification of the first single of the tree, in depth-first order, whose notification
   * is available. Nodes already done are skipped.
   */
  private AwaitResult.@Nullable AnyCompleted takeFirstNotification(UnresolvedFuture node) {
    if (node.isDone()) {
      return null;
    }
    if (node instanceof UnresolvedFuture.Single single) {
      NotificationHandle<?> handle = (NotificationHandle<?>) single.handle();
      StateMachine.NotificationValue value = this.stateMachine.takeNotification(handle.value());
      return value != null ? new AwaitResult.AnyCompleted(single, handle.toResult(value)) : null;
    }
    for (int i = 0; i < UnresolvedFutureUtils.childrenCount(node); i++) {
      AwaitResult.AnyCompleted anyCompleted =
          takeFirstNotification(UnresolvedFutureUtils.child(node, i));
      if (anyCompleted != null) {
        return anyCompleted;
      }
    }
    return null;
  }

  // -- Run

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
