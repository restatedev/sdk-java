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
import dev.restate.sdk.core.statemachine.InvocationState;
import dev.restate.sdk.core.statemachine.NotificationValue;
import dev.restate.sdk.core.statemachine.StateMachine;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.types.*;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

class HandlerContextImpl implements HandlerContextInternal {

  private final Request request;
  private final StateMachine stateMachine;
  private final @Nullable String objectKey;
  private final String fullyQualifiedHandlerName;

  private CompletableFuture<Void> nextProcessedRun;
  private final HashMap<Integer, Consumer<RunCompleter>> scheduledRuns;

  HandlerContextImpl(
      String fullyQualifiedHandlerName,
      StateMachine stateMachine,
      Context otelContext,
      StateMachine.Input input) {
    this.request = new Request(input.invocationId(), otelContext, input.body(), input.headers());
    this.objectKey = input.key();
    this.stateMachine = stateMachine;
    this.fullyQualifiedHandlerName = fullyQualifiedHandlerName;
    this.scheduledRuns = new HashMap<>();
  }

  @Override
  public String objectKey() {
    return this.objectKey;
  }

  @Override
  public Request request() {
    return this.request;
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
  public CompletableFuture<AsyncResult<Optional<Slice>>> get(String name) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.stateGet(name),
                (s, cf) -> {
                  if (s instanceof NotificationValue.Empty) {
                    cf.complete(Optional.empty());
                  } else if (s instanceof NotificationValue.Success) {
                    cf.complete(Optional.of(((NotificationValue.Success) s).slice()));
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
                  if (s instanceof NotificationValue.StateKeys) {
                    cf.complete(((NotificationValue.StateKeys) s).stateKeys());
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
  public CompletableFuture<AsyncResult<Void>> sleep(Duration duration) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sleep(duration),
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
      @Nullable List<Map.Entry<String, String>> headers) {
    return catchExceptions(
        () -> {
          StateMachine.CallHandle callHandle =
              this.stateMachine.call(target, parameter, idempotencyKey, headers);

          AsyncResult<String> invocationIdAsyncResult =
              AsyncResults.single(
                  this,
                  callHandle.invocationIdHandle(),
                  (s, cf) -> {
                    if (s instanceof NotificationValue.InvocationId) {
                      cf.complete(((NotificationValue.InvocationId) s).invocationId());
                    } else {
                      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                    }
                  });

          AsyncResult<Slice> callAsyncResult =
              AsyncResults.single(
                  this,
                  callHandle.resultHandle(),
                  (s, cf) -> {
                    if (s instanceof NotificationValue.Success) {
                      cf.complete(((NotificationValue.Success) s).slice());
                    } else if (s instanceof NotificationValue.Failure) {
                      cf.completeExceptionally(((NotificationValue.Failure) s).exception());
                    } else {
                      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                    }
                  });

          return new CallResult(invocationIdAsyncResult, callAsyncResult);
        });
  }

  @Override
  public CompletableFuture<AsyncResult<String>> send(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable List<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    return catchExceptions(
        () -> {
          int sendHandle =
              this.stateMachine.send(target, parameter, idempotencyKey, headers, delay);

          AsyncResult<String> invocationIdAsyncResult =
              AsyncResults.single(
                  this,
                  sendHandle,
                  (s, cf) -> {
                    if (s instanceof NotificationValue.InvocationId) {
                      cf.complete(((NotificationValue.InvocationId) s).invocationId());
                    } else {
                      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                    }
                  });

          return invocationIdAsyncResult;
        });
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> submitRun(
      @Nullable String name, Consumer<RunCompleter> closure) {
    return catchExceptions(
        () -> {
          int runHandle = this.stateMachine.run(name);
          this.scheduledRuns.put(runHandle, closure);
          return AsyncResults.single(
              this,
              runHandle,
              (s, cf) -> {
                if (s instanceof NotificationValue.Success) {
                  cf.complete(((NotificationValue.Success) s).slice());
                } else if (s instanceof NotificationValue.Failure) {
                  cf.completeExceptionally(((NotificationValue.Failure) s).exception());
                } else {
                  throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                }
              });
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
                  this,
                  awakeable.handle(),
                  (s, cf) -> {
                    if (s instanceof NotificationValue.Success) {
                      cf.complete(((NotificationValue.Success) s).slice());
                    } else if (s instanceof NotificationValue.Failure) {
                      cf.completeExceptionally(((NotificationValue.Failure) s).exception());
                    } else {
                      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                    }
                  }));
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
                (s, cf) -> {
                  if (s instanceof NotificationValue.Success) {
                    cf.complete(((NotificationValue.Success) s).slice());
                  } else if (s instanceof NotificationValue.Failure) {
                    cf.completeExceptionally(((NotificationValue.Failure) s).exception());
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> peekPromise(String key) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promiseGet(key),
                (s, cf) -> {
                  if (s instanceof NotificationValue.Empty) {
                    cf.complete(Output.notReady());
                  } else if (s instanceof NotificationValue.Success) {
                    cf.complete(Output.ready(((NotificationValue.Success) s).slice()));
                  } else if (s instanceof NotificationValue.Failure) {
                    cf.completeExceptionally(((NotificationValue.Failure) s).exception());
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> resolvePromise(String key, Slice payload) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promiseComplete(key, payload),
                (s, cf) -> {
                  if (s instanceof NotificationValue.Empty) {
                    cf.complete(null);
                  } else if (s instanceof NotificationValue.Failure) {
                    cf.completeExceptionally(((NotificationValue.Failure) s).exception());
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> rejectPromise(String key, TerminalException reason) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.promiseComplete(key, reason),
                (s, cf) -> {
                  if (s instanceof NotificationValue.Empty) {
                    cf.complete(null);
                  } else if (s instanceof NotificationValue.Failure) {
                    cf.completeExceptionally(((NotificationValue.Failure) s).exception());
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
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
  public void pollAsyncResult(AsyncResults.AsyncResultInternal<?> asyncResult) {
    // We use the separate function for the recursion,
    // as there's no need to jump back and forth between threads again.
    this.pollAsyncResultInner(asyncResult);
  }

  private void pollAsyncResultInner(AsyncResults.AsyncResultInternal<?> asyncResult) {
    while (true) {
      if (asyncResult.isDone()) {
        return;
      }

      // Let's start by trying to complete it
      asyncResult.tryComplete(this.stateMachine);

      // Now let's take the unprocessed leaves
      List<Integer> uncompletedLeaves = asyncResult.uncompletedLeaves().toList();
      if (uncompletedLeaves.isEmpty()) {
        // Nothing else to do!
        return;
      }

      // Not ready yet, let's try to do some progress
      StateMachine.DoProgressResponse response = this.stateMachine.doProgress(uncompletedLeaves);

      if (response instanceof StateMachine.DoProgressResponse.AnyCompleted) {
        // Let it loop now
      } else if (response instanceof StateMachine.DoProgressResponse.ReadFromInput) {
        this.stateMachine
            .waitNextInputSignal()
            .thenAccept(v -> this.pollAsyncResultInner(asyncResult));
        return;
      } else if (response instanceof StateMachine.DoProgressResponse.ExecuteRun) {
        triggerScheduledRun(((StateMachine.DoProgressResponse.ExecuteRun) response).handle());
        // Let it loop now
      } else if (response instanceof StateMachine.DoProgressResponse.WaitingPendingRun) {
        this.waitNextProcessedRun().thenAccept(v -> this.pollAsyncResultInner(asyncResult));
        return;
      }
    }
  }

  @Override
  public void proposeRunSuccess(int runHandle, Slice toWrite) {
    try {
      this.stateMachine.proposeRunCompletion(runHandle, toWrite);
      if (this.nextProcessedRun != null) {
        this.nextProcessedRun.complete(null);
        this.nextProcessedRun = null;
      }
    } catch (Exception e) {
      this.fail(e);
    }
  }

  @Override
  public void proposeRunFailure(
      int runHandle,
      Throwable toWrite,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    try {
      this.stateMachine.proposeRunCompletion(runHandle, toWrite, attemptDuration, retryPolicy);
      if (this.nextProcessedRun != null) {
        this.nextProcessedRun.complete(null);
        this.nextProcessedRun = null;
      }
    } catch (Exception e) {
      this.fail(e);
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
    this.stateMachine.onError(cause);
  }

  // -- Wrapper for failure propagation

  private CompletableFuture<Void> catchExceptions(ThrowingRunnable r) {
    try {
      r.run();
      return CompletableFuture.completedFuture(null);
    } catch (Throwable e) {
      this.fail(e);
      return CompletableFuture.failedFuture(AbortedExecutionException.INSTANCE);
    }
  }

  private <T> CompletableFuture<T> catchExceptions(ThrowingSupplier<T> r) {
    try {
      return CompletableFuture.completedFuture(r.get());
    } catch (Throwable e) {
      this.fail(e);
      return CompletableFuture.failedFuture(AbortedExecutionException.INSTANCE);
    }
  }
}
