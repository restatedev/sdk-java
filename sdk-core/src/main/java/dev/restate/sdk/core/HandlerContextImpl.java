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
import dev.restate.sdk.core.statemachine.InvocationState;
import dev.restate.sdk.core.statemachine.NotificationValue;
import dev.restate.sdk.core.statemachine.StateMachine;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

class HandlerContextImpl implements HandlerContextInternal {

  private static final Logger LOG = LogManager.getLogger(HandlerContextImpl.class);

  private static final int CANCEL_HANDLE = 1;

  private final HandlerRequest handlerRequest;
  private final StateMachine stateMachine;
  private final @Nullable String objectKey;
  private final String fullyQualifiedHandlerName;

  private CompletableFuture<Void> nextProcessedRun;
  private final List<AsyncResultInternal<String>> invocationIdsToCancel;
  private final HashMap<Integer, Consumer<RunCompleter>> scheduledRuns;

  HandlerContextImpl(
      String fullyQualifiedHandlerName,
      StateMachine stateMachine,
      Context otelContext,
      StateMachine.Input input) {
    this.handlerRequest =
        new HandlerRequest(input.invocationId(), otelContext, input.body(), input.headers());
    this.objectKey = input.key();
    this.stateMachine = stateMachine;
    this.fullyQualifiedHandlerName = fullyQualifiedHandlerName;
    this.invocationIdsToCancel = new ArrayList<>();
    this.scheduledRuns = new HashMap<>();
  }

  private static void parseSuccessOrFailure(NotificationValue s, CompletableFuture<Slice> cf) {
    if (s instanceof NotificationValue.Success success) {
      cf.complete(success.slice());
    } else if (s instanceof NotificationValue.Failure failure) {
      cf.completeExceptionally(failure.exception());
    } else {
      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
    }
  }

  private static void parseEmptyOrSuccessOrFailure(
      NotificationValue s, CompletableFuture<Output<Slice>> cf) {
    if (s instanceof NotificationValue.Empty) {
      cf.complete(Output.notReady());
    } else if (s instanceof NotificationValue.Success success) {
      cf.complete(Output.ready(success.slice()));
    } else if (s instanceof NotificationValue.Failure failure) {
      cf.completeExceptionally(failure.exception());
    } else {
      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
    }
  }

  private static void parseEmptyOrFailure(NotificationValue s, CompletableFuture<Void> cf) {
    if (s instanceof NotificationValue.Empty) {
      cf.complete(null);
    } else if (s instanceof NotificationValue.Failure failure) {
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
  public String getFullyQualifiedMethodName() {
    return this.fullyQualifiedHandlerName;
  }

  @Override
  public InvocationState getInvocationState() {
    return this.stateMachine.state();
  }

  @Override
  public Executor stateMachineExecutor() {
    return Runnable::run;
  }

  @Override
  public CompletableFuture<AsyncResult<Optional<Slice>>> get(String name) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.stateGet(name),
                (s, cf) -> {
                  if (s instanceof NotificationValue.Empty) {
                    cf.complete(Optional.empty());
                  } else if (s instanceof NotificationValue.Success success) {
                    cf.complete(Optional.of(success.slice()));
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<AsyncResult<Collection<String>>> getKeys() {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.stateGetKeys(),
                (s, cf) -> {
                  if (s instanceof NotificationValue.StateKeys stateKeys) {
                    cf.complete(stateKeys.stateKeys());
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<Void> clear(String name) {
    return this.catchExceptions(() -> this.stateMachine.stateClear(name));
  }

  @Override
  public CompletableFuture<Void> clearAll() {
    return this.catchExceptions(this.stateMachine::stateClearAll);
  }

  @Override
  public CompletableFuture<Void> set(String name, Slice value) {
    return this.catchExceptions(() -> this.stateMachine.stateSet(name, value));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> timer(Duration duration, String name) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sleep(duration, name),
                (s, cf) -> {
                  if (s instanceof NotificationValue.Empty) {
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
      @Nullable Collection<Map.Entry<String, String>> headers) {
    return catchExceptions(
        () -> {
          StateMachine.CallHandle callHandle =
              this.stateMachine.call(target, parameter, idempotencyKey, headers);

          AsyncResultInternal<String> invocationIdAsyncResult =
              AsyncResults.single(this, callHandle.invocationIdHandle(), invocationIdCompleter());
          this.invocationIdsToCancel.add(invocationIdAsyncResult);

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
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    return catchExceptions(
        () -> {
          int sendHandle =
              this.stateMachine.send(target, parameter, idempotencyKey, headers, delay);

          return AsyncResults.single(this, sendHandle, invocationIdCompleter());
        });
  }

  private static AsyncResults.Completer<String> invocationIdCompleter() {
    return (s, cf) -> {
      if (s instanceof NotificationValue.InvocationId invocationId) {
        cf.complete(invocationId.invocationId());
      } else {
        throw ProtocolException.unexpectedNotificationVariant(s.getClass());
      }
    };
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> submitRun(
      @Nullable String name, Consumer<RunCompleter> closure) {
    return catchExceptions(
        () -> {
          int runHandle = this.stateMachine.run(name);
          this.scheduledRuns.put(runHandle, closure);
          return AsyncResults.single(this, runHandle, HandlerContextImpl::parseSuccessOrFailure);
        });
  }

  @Override
  public CompletableFuture<Awakeable> awakeable() {
    return catchExceptions(
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
    return this.catchExceptions(() -> this.stateMachine.completeAwakeable(id, payload));
  }

  @Override
  public CompletableFuture<Void> rejectAwakeable(String id, TerminalException reason) {
    return this.catchExceptions(() -> this.stateMachine.completeAwakeable(id, reason));
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> promise(String key) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promiseGet(key),
                HandlerContextImpl::parseSuccessOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> peekPromise(String key) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promisePeek(key),
                HandlerContextImpl::parseEmptyOrSuccessOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> resolvePromise(String key, Slice payload) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promiseComplete(key, payload),
                HandlerContextImpl::parseEmptyOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> rejectPromise(String key, TerminalException reason) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promiseComplete(key, reason),
                HandlerContextImpl::parseEmptyOrFailure));
  }

  @Override
  public CompletableFuture<Void> cancelInvocation(String invocationId) {
    return this.catchExceptions(() -> this.stateMachine.cancelInvocation(invocationId));
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> attachInvocation(String invocationId) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.attachInvocation(invocationId),
                HandlerContextImpl::parseSuccessOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> getInvocationOutput(String invocationId) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.getInvocationOutput(invocationId),
                HandlerContextImpl::parseEmptyOrSuccessOrFailure));
  }

  @Override
  public CompletableFuture<Void> writeOutput(Slice value) {
    return this.catchExceptions(() -> this.stateMachine.writeOutput(value));
  }

  @Override
  public CompletableFuture<Void> writeOutput(TerminalException throwable) {
    return this.catchExceptions(() -> this.stateMachine.writeOutput(throwable));
  }

  @Override
  public void pollAsyncResult(AsyncResultInternal<?> asyncResult) {
    // We use the separate function for the recursion,
    // as there's no need to jump back and forth between threads again.
    this.pollAsyncResultInner(asyncResult);
  }

  private void pollAsyncResultInner(AsyncResultInternal<?> asyncResult) {
    while (true) {
      if (asyncResult.isDone()) {
        return;
      }

      // Let's look for the cancellation notification
      var cancellationNotification = this.stateMachine.takeNotification(CANCEL_HANDLE);
      if (cancellationNotification.isPresent()) {
        LOG.info("Detected cancellation signal! Will start cancelling child invocations");

        // Let's wait to cancel all
        @SuppressWarnings({"rawtypes", "unchecked"})
        AsyncResultInternal<Void> allInvocationIds =
            AsyncResults.all(this, (List) this.invocationIdsToCancel);
        allInvocationIds
            .publicFuture()
            .whenComplete(
                (ignored, throwable) -> {
                  if (throwable != null) {
                    // Already handled
                    return;
                  }
                  LOG.info("All child invocation ids retrieved");
                  try {
                    for (var invocationIdAr : this.invocationIdsToCancel) {
                      this.stateMachine.cancelInvocation(
                          Objects.requireNonNull(invocationIdAr.publicFuture().getNow(null)));
                    }
                    asyncResult.tryCancel();
                  } catch (Throwable e) {
                    // Not good!
                    this.failWithoutContextSwitch(e);
                  }
                });
        // Let's resolve all the invocation IDs
        pollAsyncResultInner(allInvocationIds);
        return;
      }

      // Let's start by trying to complete it
      asyncResult.tryComplete(this.stateMachine);

      // Now let's take the unprocessed leaves
      List<Integer> uncompletedLeaves =
          Stream.concat(asyncResult.uncompletedLeaves(), Stream.of(CANCEL_HANDLE)).toList();
      if (uncompletedLeaves.size() == 1) {
        // Nothing else to do!
        return;
      }

      // Not ready yet, let's try to do some progress
      StateMachine.DoProgressResponse response;
      try {
        response = this.stateMachine.doProgress(uncompletedLeaves);
      } catch (Throwable e) {
        this.failWithoutContextSwitch(e);
        asyncResult.publicFuture().completeExceptionally(AbortedExecutionException.INSTANCE);
        return;
      }

      if (response instanceof StateMachine.DoProgressResponse.AnyCompleted) {
        // Let it loop now
      } else if (response instanceof StateMachine.DoProgressResponse.ReadFromInput
          || response instanceof StateMachine.DoProgressResponse.WaitingPendingRun) {
        CompletableFuture.anyOf(
                this.waitNextProcessedRun(), this.stateMachine.waitNextInputSignal())
            .thenAccept(v -> this.pollAsyncResultInner(asyncResult));
        return;
      } else if (response instanceof StateMachine.DoProgressResponse.ExecuteRun) {
        triggerScheduledRun(((StateMachine.DoProgressResponse.ExecuteRun) response).handle());
        // Let it loop now
      }
    }
  }

  @Override
  public void proposeRunSuccess(int runHandle, Slice toWrite) {
    try {
      this.stateMachine.proposeRunCompletion(runHandle, toWrite);
    } catch (Exception e) {
      this.failWithoutContextSwitch(e);
    }
    triggerNextProcessedRun();
  }

  @Override
  public void proposeRunFailure(
      int runHandle,
      Throwable toWrite,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    try {
      this.stateMachine.proposeRunCompletion(runHandle, toWrite, attemptDuration, retryPolicy);
    } catch (Exception e) {
      this.failWithoutContextSwitch(e);
    }
    triggerNextProcessedRun();
  }

  private void triggerNextProcessedRun() {
    if (this.nextProcessedRun != null) {
      var fut = this.nextProcessedRun;
      this.nextProcessedRun = null;
      fut.complete(null);
    }
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

  private CompletableFuture<Void> waitNextProcessedRun() {
    if (this.nextProcessedRun == null) {
      this.nextProcessedRun = new CompletableFuture<>();
    }
    return this.nextProcessedRun;
  }

  @Override
  public void close() {
    this.stateMachine.end();
  }

  @Override
  public void fail(Throwable cause) {
    this.failWithoutContextSwitch(cause);
  }

  @Override
  public void failWithoutContextSwitch(Throwable cause) {
    this.stateMachine.onError(cause);
  }

  // -- Wrapper for failure propagation

  private CompletableFuture<Void> catchExceptions(ThrowingRunnable r) {
    try {
      r.run();
      return CompletableFuture.completedFuture(null);
    } catch (Throwable e) {
      this.failWithoutContextSwitch(e);
      return CompletableFuture.failedFuture(AbortedExecutionException.INSTANCE);
    }
  }

  private <T> CompletableFuture<T> catchExceptions(ThrowingSupplier<T> r) {
    try {
      return CompletableFuture.completedFuture(r.get());
    } catch (Throwable e) {
      this.failWithoutContextSwitch(e);
      return CompletableFuture.failedFuture(AbortedExecutionException.INSTANCE);
    }
  }
}
