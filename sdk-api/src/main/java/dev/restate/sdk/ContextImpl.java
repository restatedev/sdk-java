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
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.endpoint.definition.AsyncResult;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

import dev.restate.sdk.types.DurablePromiseKey;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.types.Request;
import dev.restate.sdk.types.StateKey;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.common.Target;
import dev.restate.sdk.types.TerminalException;
import dev.restate.serde.Serde;
import org.jspecify.annotations.NonNull;

class ContextImpl implements ObjectContext, WorkflowContext {

  final HandlerContext handlerContext;

  ContextImpl(HandlerContext handlerContext) {
    this.handlerContext = handlerContext;
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
    return
            Util.awaitCompletableFuture(      Util.awaitCompletableFuture(
            handlerContext.get(key.name())).poll())
        .map(key.serde()::deserialize);
  }

  @Override
  public Collection<String> stateKeys() {
    return      Util.awaitCompletableFuture( Util.awaitCompletableFuture(handlerContext.getKeys()).poll());
  }

  @Override
  public void clear(StateKey<?> key) {
    Util.awaitCompletableFuture(
    handlerContext.clear(key.name()));
  }

  @Override
  public void clearAll() {
    Util.awaitCompletableFuture(
    handlerContext.clearAll());
  }

  @Override
  public <T> void set(StateKey<T> key, @NonNull T value) {
    Util.awaitCompletableFuture(
            handlerContext.set(
                key.name(), Util.serializeWrappingException(handlerContext, key.serde(), value)));
  }

  @Override
  public Awaitable<Void> timer(Duration duration) {
    return Awaitable.fromAsyncResult(   Util.awaitCompletableFuture(handlerContext.sleep(duration)));
  }

  @Override
  public <T, R> Awaitable<R> call(
          Target target, Serde<T> inputSerde, Serde<R> outputSerde, T parameter) {
    Slice input = Util.serializeWrappingException(handlerContext, inputSerde, parameter);
    HandlerContext.CallResult result =    Util.awaitCompletableFuture(handlerContext.call(target, input, null, null));
    return Awaitable.fromAsyncResult(result.callAsyncResult())
        .map(outputSerde::deserialize);
  }

  @Override
  public <T> void send(Target target, Serde<T> inputSerde, T parameter) {
    Slice input = Util.serializeWrappingException(handlerContext, inputSerde, parameter);
    Util.awaitCompletableFuture( handlerContext.send(target, input, null, null, null));
  }

  @Override
  public <T> void send(Target target, Serde<T> inputSerde, T parameter, Duration delay) {
    Slice input = Util.serializeWrappingException(handlerContext, inputSerde, parameter);
    Util.awaitCompletableFuture( handlerContext.send(target, input, null, null, delay));
  }

  @Override
  public <T> Awaitable<T> scheduleRun(
      String name, Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action) {
    return Awaitable.fromAsyncResult(Util.awaitCompletableFuture(handlerContext.scheduleRun(name, runCompleter -> {
      Slice result;
      try {
         result =  serde.serialize(action.get());
      } catch (Throwable e) {
        runCompleter.proposeFailure(e, retryPolicy);
        return;
      }
      runCompleter.proposeSuccess(result);
    }))).map(serde::deserialize);
 }

  @Override
  public <T> Awakeable<T> awakeable(Serde<T> serde) throws TerminalException {
    // Retrieve the awakeable
    HandlerContext.Awakeable awakeable = Util.awaitCompletableFuture(handlerContext.awakeable());
    return new Awakeable<>( awakeable.asyncResult(), serde, awakeable.id());
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
        Util.awaitCompletableFuture( handlerContext.rejectAwakeable(id, reason));
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
        return Awaitable.fromAsyncResult( result)
            .map(key.serde()::deserialize);
      }

      @Override
      public Output<T> peek() {
        return
                Util.awaitCompletableFuture(      Util.awaitCompletableFuture(
                                handlerContext.peekPromise(key.name())).poll())
                        .map(key.serde()::deserialize);
      }
    };
  }

  @Override
  public <T> DurablePromiseHandle<T> promiseHandle(DurablePromiseKey<T> key) {
    return new DurablePromiseHandle<>() {
      @Override
      public void resolve(T payload) throws IllegalStateException {
        Util.awaitCompletableFuture(      Util.awaitCompletableFuture(
                                handlerContext.resolvePromise(
                                        key.name(),
                                        Util.serializeWrappingException(handlerContext, key.serde(), payload)
                                        )
                        )

                        .poll());
      }

      @Override
      public void reject(String reason) throws IllegalStateException {
        Util.awaitCompletableFuture(      Util.awaitCompletableFuture(
                        handlerContext.rejectPromise(key.name(), reason
                        )
                )

                .poll());
      }
    };
  }
}
