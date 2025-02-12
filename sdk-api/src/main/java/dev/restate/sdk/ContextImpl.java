// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.Output;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.types.DurablePromiseKey;
import dev.restate.sdk.types.Request;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.StateKey;
import dev.restate.sdk.types.TerminalException;
import dev.restate.serde.Serde;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.jspecify.annotations.NonNull;

class ContextImpl implements ObjectContext, WorkflowContext {

  private final HandlerContext handlerContext;
  private final Executor serviceExecutor;

  ContextImpl(HandlerContext handlerContext, Executor serviceExecutor) {
    this.handlerContext = handlerContext;
    this.serviceExecutor = serviceExecutor;
  }

  @Override
  public String key() {
    return handlerContext.objectKey();
  }

  @Override
  public Request request() {
    return handlerContext.request();
  }

  @Override
  public <T> Optional<T> get(StateKey<T> key) {
    return Awaitable.fromAsyncResult(
            Util.awaitCompletableFuture(handlerContext.get(key.name())), serviceExecutor)
        .mapWithoutExecutor(opt -> opt.map(key.serde()::deserialize))
        .await();
  }

  @Override
  public Collection<String> stateKeys() {
    return Util.awaitCompletableFuture(
        Util.awaitCompletableFuture(handlerContext.getKeys()).poll());
  }

  @Override
  public void clear(StateKey<?> key) {
    Util.awaitCompletableFuture(handlerContext.clear(key.name()));
  }

  @Override
  public void clearAll() {
    Util.awaitCompletableFuture(handlerContext.clearAll());
  }

  @Override
  public <T> void set(StateKey<T> key, @NonNull T value) {
    Util.awaitCompletableFuture(
        handlerContext.set(
            key.name(), Util.serializeWrappingException(handlerContext, key.serde(), value)));
  }

  @Override
  public Awaitable<Void> timer(String name, Duration duration) {
    return Awaitable.fromAsyncResult(
        Util.awaitCompletableFuture(handlerContext.timer(duration, name)), serviceExecutor);
  }

  @Override
  public <T, R> Awaitable<R> call(
      Target target, Serde<T> inputSerde, Serde<R> outputSerde, T parameter, CallOptions options) {
    Slice input = Util.serializeWrappingException(handlerContext, inputSerde, parameter);
    HandlerContext.CallResult result =
        Util.awaitCompletableFuture(
            handlerContext.call(
                target, input, options.getIdempotencyKey(), options.getHeaders().entrySet()));
    return Awaitable.fromAsyncResult(result.callAsyncResult(), serviceExecutor)
        .mapWithoutExecutor(outputSerde::deserialize);
  }

  @Override
  public <T> void send(Target target, Serde<T> inputSerde, T parameter, SendOptions sendOptions) {
    Slice input = Util.serializeWrappingException(handlerContext, inputSerde, parameter);
    Util.awaitCompletableFuture(
        handlerContext.send(
            target,
            input,
            sendOptions.getIdempotencyKey(),
            sendOptions.getHeaders().entrySet(),
            sendOptions.getDelay()));
  }

  @Override
  public <T> Awaitable<T> runAsync(
      String name, Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action) {
    return Awaitable.fromAsyncResult(
            Util.awaitCompletableFuture(
                handlerContext.submitRun(
                    name,
                    runCompleter ->
                        serviceExecutor.execute(
                            () -> {
                              Slice result;
                              try {
                                result = serde.serialize(action.get());
                              } catch (Throwable e) {
                                runCompleter.proposeFailure(e, retryPolicy);
                                return;
                              }
                              runCompleter.proposeSuccess(result);
                            }))),
            serviceExecutor)
        .mapWithoutExecutor(serde::deserialize);
  }

  @Override
  public <T> Awakeable<T> awakeable(Serde<T> serde) throws TerminalException {
    // Retrieve the awakeable
    HandlerContext.Awakeable awakeable = Util.awaitCompletableFuture(handlerContext.awakeable());
    return new Awakeable<>(awakeable.asyncResult(), serviceExecutor, serde, awakeable.id());
  }

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return new AwakeableHandle() {
      @Override
      public <T> void resolve(Serde<T> serde, @NonNull T payload) {
        Util.awaitCompletableFuture(
            handlerContext.resolveAwakeable(
                id, Util.serializeWrappingException(handlerContext, serde, payload)));
      }

      @Override
      public void reject(String reason) {
        Util.awaitCompletableFuture(
            handlerContext.rejectAwakeable(id, new TerminalException(reason)));
      }
    };
  }

  @Override
  public RestateRandom random() {
    return new RestateRandom(this.request().invocationId().toRandomSeed());
  }

  @Override
  public <T> DurablePromise<T> promise(DurablePromiseKey<T> key) {
    return new DurablePromise<>() {
      @Override
      public Awaitable<T> awaitable() {
        AsyncResult<Slice> result = Util.awaitCompletableFuture(handlerContext.promise(key.name()));
        return Awaitable.fromAsyncResult(result, serviceExecutor)
            .mapWithoutExecutor(key.serde()::deserialize);
      }

      @Override
      public Output<T> peek() {
        return Util.awaitCompletableFuture(
                Util.awaitCompletableFuture(handlerContext.peekPromise(key.name())).poll())
            .map(key.serde()::deserialize);
      }
    };
  }

  @Override
  public <T> DurablePromiseHandle<T> promiseHandle(DurablePromiseKey<T> key) {
    return new DurablePromiseHandle<>() {
      @Override
      public void resolve(T payload) throws IllegalStateException {
        Util.awaitCompletableFuture(
            Util.awaitCompletableFuture(
                    handlerContext.resolvePromise(
                        key.name(),
                        Util.serializeWrappingException(handlerContext, key.serde(), payload)))
                .poll());
      }

      @Override
      public void reject(String reason) throws IllegalStateException {
        Util.awaitCompletableFuture(
            Util.awaitCompletableFuture(
                    handlerContext.rejectPromise(key.name(), new TerminalException(reason)))
                .poll());
      }
    };
  }
}
