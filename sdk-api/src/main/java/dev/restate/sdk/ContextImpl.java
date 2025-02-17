// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.*;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.types.DurablePromiseKey;
import dev.restate.sdk.types.Request;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.StateKey;
import dev.restate.sdk.types.TerminalException;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.NonNull;

class ContextImpl implements ObjectContext, WorkflowContext {

  private final HandlerContext handlerContext;
  private final Executor serviceExecutor;
  private final SerdeFactory serdeFactory;

  ContextImpl(HandlerContext handlerContext, Executor serviceExecutor, SerdeFactory serdeFactory) {
    this.handlerContext = handlerContext;
    this.serviceExecutor = serviceExecutor;
    this.serdeFactory = serdeFactory;
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
        .mapWithoutExecutor(opt -> opt.map(serdeFactory.create(key.serdeInfo())::deserialize))
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
            key.name(),
            Util.executeOrFail(
                handlerContext, serdeFactory.create(key.serdeInfo())::serialize, value)));
  }

  @Override
  public Awaitable<Void> timer(String name, Duration duration) {
    return Awaitable.fromAsyncResult(
        Util.awaitCompletableFuture(handlerContext.timer(duration, name)), serviceExecutor);
  }

  @Override
  public <T, R> CallAwaitable<R> call(CallRequest<T, R> callRequest) {
    Slice input =
        Util.executeOrFail(
            handlerContext,
            serdeFactory.create(callRequest.requestSerdeInfo())::serialize,
            callRequest.request());
    HandlerContext.CallResult result =
        Util.awaitCompletableFuture(
            handlerContext.call(
                callRequest.target(),
                input,
                callRequest.idempotencyKey(),
                callRequest.headers().entrySet()));

    return new CallAwaitable<>(
        result
            .callAsyncResult()
            .map(
                s ->
                    CompletableFuture.completedFuture(
                        serdeFactory.create(callRequest.responseSerdeInfo()).deserialize(s))),
        Awaitable.fromAsyncResult(result.invocationIdAsyncResult(), serviceExecutor));
  }

  @Override
  public <T> SendHandle send(SendRequest<T> sendRequest) {
    Slice input =
        Util.executeOrFail(
            handlerContext,
            serdeFactory.create(sendRequest.requestSerdeInfo())::serialize,
            sendRequest.request());
    var invocationIdAsyncResult =
        Util.awaitCompletableFuture(
            handlerContext.send(
                sendRequest.target(),
                input,
                sendRequest.idempotencyKey(),
                sendRequest.headers().entrySet(),
                sendRequest.delay()));
    return new SendHandle(Awaitable.fromAsyncResult(invocationIdAsyncResult, serviceExecutor));
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
                id,
                Util.executeOrFail(
                    handlerContext, serdeFactory.create(serde)::serialize, payload)));
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
            .mapWithoutExecutor(serdeFactory.create(key.serdeInfo())::deserialize);
      }

      @Override
      public Output<T> peek() {
        return Util.awaitCompletableFuture(
                Util.awaitCompletableFuture(handlerContext.peekPromise(key.name())).poll())
            .map(serdeFactory.create(key.serdeInfo())::deserialize);
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
                        Util.executeOrFail(
                            handlerContext,
                            serdeFactory.create(key.serdeInfo())::serialize,
                            payload)))
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
