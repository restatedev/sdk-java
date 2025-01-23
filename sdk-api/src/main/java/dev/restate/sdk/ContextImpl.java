// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.definition.HandlerContext;
import dev.restate.sdk.function.ThrowingSupplier;
import dev.restate.sdk.definition.AsyncResult;
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import dev.restate.sdk.serde.Serde;
import dev.restate.sdk.types.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
    AsyncResult<ByteBuffer> asyncResult = Util.blockOnSyscall(cb -> handlerContext.get(key.name(), cb));

    if (!asyncResult.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> handlerContext.resolveDeferred(asyncResult, cb));
    }

    return Util.unwrapOptionalReadyResult(asyncResult.toResult())
        .map(bs -> Util.deserializeWrappingException(handlerContext, key.serde(), bs));
  }

  @Override
  public Collection<String> stateKeys() {
    AsyncResult<Collection<String>> asyncResult = Util.blockOnSyscall(handlerContext::getKeys);

    if (!asyncResult.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> handlerContext.resolveDeferred(asyncResult, cb));
    }

    return Util.unwrapResult(asyncResult.toResult());
  }

  @Override
  public void clear(StateKey<?> key) {
    Util.<Void>blockOnSyscall(cb -> handlerContext.clear(key.name(), cb));
  }

  @Override
  public void clearAll() {
    Util.<Void>blockOnSyscall(handlerContext::clearAll);
  }

  @Override
  public <T> void set(StateKey<T> key, @NonNull T value) {
    Util.<Void>blockOnSyscall(
        cb ->
            handlerContext.set(
                key.name(), Util.serializeWrappingException(handlerContext, key.serde(), value), cb));
  }

  @Override
  public Awaitable<Void> timer(Duration duration) {
    AsyncResult<Void> result = Util.blockOnSyscall(cb -> handlerContext.sleep(duration, cb));
    return Awaitable.single(handlerContext, result);
  }

  @Override
  public <T, R> Awaitable<R> call(
      Target target, Serde<T> inputSerde, Serde<R> outputSerde, T parameter) {
    ByteBuffer input = Util.serializeWrappingException(handlerContext, inputSerde, parameter);
    AsyncResult<ByteBuffer> result = Util.blockOnSyscall(cb -> handlerContext.call(target, input, cb));
    return Awaitable.single(handlerContext, result)
        .map(bs -> Util.deserializeWrappingException(handlerContext, outputSerde, bs));
  }

  @Override
  public <T> void send(Target target, Serde<T> inputSerde, T parameter) {
    ByteBuffer input = Util.serializeWrappingException(handlerContext, inputSerde, parameter);
    Util.<Void>blockOnSyscall(cb -> handlerContext.send(target, input, null, cb));
  }

  @Override
  public <T> void send(Target target, Serde<T> inputSerde, T parameter, Duration delay) {
    ByteBuffer input = Util.serializeWrappingException(handlerContext, inputSerde, parameter);
    Util.<Void>blockOnSyscall(cb -> handlerContext.send(target, input, delay, cb));
  }

  @Override
  public <T> T run(
      String name, Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action) {
    CompletableFuture<CompletableFuture<ByteBuffer>> enterFut = new CompletableFuture<>();
    handlerContext.enterSideEffectBlock(
        name,
        new EnterSideEffectSyscallCallback() {
          @Override
          public void onNotExecuted() {
            enterFut.complete(new CompletableFuture<>());
          }

          @Override
          public void onSuccess(ByteBuffer result) {
            enterFut.complete(CompletableFuture.completedFuture(result));
          }

          @Override
          public void onFailure(TerminalException t) {
            enterFut.complete(CompletableFuture.failedFuture(t));
          }

          @Override
          public void onCancel(Throwable t) {
            enterFut.cancel(true);
          }
        });

    // If a failure was stored, it's simply thrown here
    CompletableFuture<ByteBuffer> exitFut = Util.awaitCompletableFuture(enterFut);
    if (exitFut.isDone()) {
      // We already have a result, we don't need to execute the action
      return Util.deserializeWrappingException(
              handlerContext, serde, Util.awaitCompletableFuture(exitFut));
    }

    ExitSideEffectSyscallCallback exitCallback =
        new ExitSideEffectSyscallCallback() {
          @Override
          public void onSuccess(ByteBuffer result) {
            exitFut.complete(result);
          }

          @Override
          public void onFailure(TerminalException t) {
            exitFut.completeExceptionally(t);
          }

          @Override
          public void onCancel(@Nullable Throwable t) {
            exitFut.cancel(true);
          }
        };

    T res = null;
    Throwable failure = null;
    try {
      res = action.get();
    } catch (Throwable e) {
      failure = e;
    }

    if (failure != null) {
      handlerContext.exitSideEffectBlockWithException(failure, retryPolicy, exitCallback);
    } else {
      handlerContext.exitSideEffectBlock(
          Util.serializeWrappingException(handlerContext, serde, res), exitCallback);
    }

    return Util.deserializeWrappingException(handlerContext, serde, Util.awaitCompletableFuture(exitFut));
  }

  @Override
  public <T> Awakeable<T> awakeable(Serde<T> serde) throws TerminalException {
    // Retrieve the awakeable
    Map.Entry<String, AsyncResult<ByteBuffer>> awakeable = Util.blockOnSyscall(handlerContext::awakeable);

    return new Awakeable<>(handlerContext, awakeable.getValue(), serde, awakeable.getKey());
  }

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return new AwakeableHandle() {
      @Override
      public <T> void resolve(Serde<T> serde, @NonNull T payload) {
        Util.<Void>blockOnSyscall(
            cb ->
                handlerContext.resolveAwakeable(
                    id, Util.serializeWrappingException(handlerContext, serde, payload), cb));
      }

      @Override
      public void reject(String reason) {
        Util.<Void>blockOnSyscall(cb -> handlerContext.rejectAwakeable(id, reason, cb));
      }
    };
  }

  @Override
  public RestateRandom random() {
    return new RestateRandom(this.request().invocationId().toRandomSeed(), this.handlerContext);
  }

  @Override
  public <T> DurablePromise<T> promise(DurablePromiseKey<T> key) {
    return new DurablePromise<>() {
      @Override
      public Awaitable<T> awaitable() {
        AsyncResult<ByteBuffer> result = Util.blockOnSyscall(cb -> handlerContext.promise(key.name(), cb));
        return Awaitable.single(handlerContext, result)
            .map(bs -> Util.deserializeWrappingException(handlerContext, key.serde(), bs));
      }

      @Override
      public Output<T> peek() {
        AsyncResult<ByteBuffer> asyncResult =
            Util.blockOnSyscall(cb -> handlerContext.peekPromise(key.name(), cb));

        if (!asyncResult.isCompleted()) {
          Util.<Void>blockOnSyscall(cb -> handlerContext.resolveDeferred(asyncResult, cb));
        }

        return Util.unwrapOutputReadyResult(asyncResult.toResult())
            .map(bs -> Util.deserializeWrappingException(handlerContext, key.serde(), bs));
      }
    };
  }

  @Override
  public <T> DurablePromiseHandle<T> promiseHandle(DurablePromiseKey<T> key) {
    return new DurablePromiseHandle<>() {
      @Override
      public void resolve(T payload) throws IllegalStateException {
        AsyncResult<Void> asyncResult =
            Util.blockOnSyscall(
                cb ->
                    handlerContext.resolvePromise(
                        key.name(),
                        Util.serializeWrappingException(handlerContext, key.serde(), payload),
                        cb));

        if (!asyncResult.isCompleted()) {
          Util.<Void>blockOnSyscall(cb -> handlerContext.resolveDeferred(asyncResult, cb));
        }

        Util.unwrapResult(asyncResult.toResult());
      }

      @Override
      public void reject(String reason) throws IllegalStateException {
        AsyncResult<Void> asyncResult =
            Util.blockOnSyscall(cb -> handlerContext.rejectPromise(key.name(), reason, cb));

        if (!asyncResult.isCompleted()) {
          Util.<Void>blockOnSyscall(cb -> handlerContext.resolveDeferred(asyncResult, cb));
        }

        Util.unwrapResult(asyncResult.toResult());
      }
    };
  }
}
