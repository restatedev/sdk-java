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
import dev.restate.sdk.common.DurablePromiseKey;
import dev.restate.sdk.common.HandlerRequest;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.TypeTag;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class ContextImpl implements ObjectContext, WorkflowContext {

  private static final ThreadLocal<Boolean> INSIDE_RUN = new ThreadLocal<>();

  private final HandlerContext handlerContext;
  private final Executor serviceExecutor;
  private final SerdeFactory serdeFactory;

  ContextImpl(HandlerContext handlerContext, Executor serviceExecutor, SerdeFactory serdeFactory) {
    this.handlerContext = handlerContext;
    this.serviceExecutor = serviceExecutor;
    this.serdeFactory = serdeFactory;
  }

  static void checkNotInsideRun() {
    if (Boolean.TRUE.equals(INSIDE_RUN.get())) {
      throw new IllegalStateException(
          "Cannot invoke context method inside ctx.run(). "
              + "The run closure is meant for non-deterministic operations (e.g., HTTP calls, database reads). "
              + "You MUST use context methods outside of ctx.run(), check the documentation: https://docs.restate.dev/develop/java/durable-steps#run");
    }
  }

  @Override
  public String key() {
    return handlerContext.objectKey();
  }

  @Override
  public HandlerRequest request() {
    return handlerContext.request();
  }

  @Override
  public <T> Optional<T> get(StateKey<T> key) {
    checkNotInsideRun();
    return DurableFuture.fromAsyncResult(
            Util.awaitCompletableFuture(handlerContext.get(key.name())), serviceExecutor)
        .mapWithoutExecutor(opt -> opt.map(serdeFactory.create(key.serdeInfo())::deserialize))
        .await();
  }

  @Override
  public Collection<String> stateKeys() {
    checkNotInsideRun();
    return Util.awaitCompletableFuture(
        Util.awaitCompletableFuture(handlerContext.getKeys()).poll());
  }

  @Override
  public void clear(StateKey<?> key) {
    checkNotInsideRun();
    Util.awaitCompletableFuture(handlerContext.clear(key.name()));
  }

  @Override
  public void clearAll() {
    checkNotInsideRun();
    Util.awaitCompletableFuture(handlerContext.clearAll());
  }

  @Override
  public <T> void set(StateKey<T> key, @NonNull T value) {
    checkNotInsideRun();
    Util.awaitCompletableFuture(
        handlerContext.set(
            key.name(),
            Util.executeOrFail(
                handlerContext, serdeFactory.create(key.serdeInfo())::serialize, value)));
  }

  @Override
  public DurableFuture<Void> timer(String name, Duration duration) {
    checkNotInsideRun();
    return DurableFuture.fromAsyncResult(
        Util.awaitCompletableFuture(handlerContext.timer(duration, name)), serviceExecutor);
  }

  @Override
  public <T, R> CallDurableFuture<R> call(Request<T, R> request) {
    checkNotInsideRun();
    Slice input =
        Util.executeOrFail(
            handlerContext,
            serdeFactory.create(request.getRequestTypeTag())::serialize,
            request.getRequest());
    HandlerContext.CallResult result =
        Util.awaitCompletableFuture(
            handlerContext.call(
                request.getTarget(),
                input,
                request.getIdempotencyKey(),
                request.getHeaders() == null
                    ? Collections.emptyList()
                    : request.getHeaders().entrySet()));

    return new CallDurableFuture<>(
        handlerContext,
        result
            .callAsyncResult()
            .map(
                s ->
                    CompletableFuture.completedFuture(
                        serdeFactory.create(request.getResponseTypeTag()).deserialize(s))),
        DurableFuture.fromAsyncResult(result.invocationIdAsyncResult(), serviceExecutor));
  }

  @Override
  public <Req, Res> InvocationHandle<Res> send(
      Request<Req, Res> request, @Nullable Duration delay) {
    checkNotInsideRun();
    Slice input =
        Util.executeOrFail(
            handlerContext,
            serdeFactory.create(request.getRequestTypeTag())::serialize,
            request.getRequest());

    var invocationIdAwaitable =
        DurableFuture.fromAsyncResult(
            Util.awaitCompletableFuture(
                handlerContext.send(
                    request.getTarget(),
                    input,
                    request.getIdempotencyKey(),
                    request.getHeaders() == null
                        ? Collections.emptyList()
                        : request.getHeaders().entrySet(),
                    delay)),
            serviceExecutor);

    return new BaseInvocationHandle<>(
        Util.executeOrFail(
            handlerContext, () -> serdeFactory.create(request.getResponseTypeTag()))) {
      @Override
      public String invocationId() {
        return invocationIdAwaitable.await();
      }
    };
  }

  @Override
  public <R> InvocationHandle<R> invocationHandle(String invocationId, TypeTag<R> responseTypeTag) {
    checkNotInsideRun();
    return new BaseInvocationHandle<>(
        Util.executeOrFail(handlerContext, () -> serdeFactory.create(responseTypeTag))) {
      @Override
      public String invocationId() {
        return invocationId;
      }
    };
  }

  abstract class BaseInvocationHandle<R> implements InvocationHandle<R> {
    private final Serde<R> responseSerde;

    BaseInvocationHandle(Serde<R> responseSerde) {
      this.responseSerde = responseSerde;
    }

    @Override
    public void cancel() {
      checkNotInsideRun();
      Util.awaitCompletableFuture(handlerContext.cancelInvocation(invocationId()));
    }

    @Override
    public DurableFuture<R> attach() {
      checkNotInsideRun();
      return DurableFuture.fromAsyncResult(
          Util.awaitCompletableFuture(handlerContext.attachInvocation(invocationId()))
              .map(s -> CompletableFuture.completedFuture(responseSerde.deserialize(s))),
          serviceExecutor);
    }

    @Override
    public Output<R> getOutput() {
      checkNotInsideRun();
      return DurableFuture.fromAsyncResult(
              Util.awaitCompletableFuture(handlerContext.getInvocationOutput(invocationId()))
                  .map(o -> CompletableFuture.completedFuture(o.map(responseSerde::deserialize))),
              serviceExecutor)
          .await();
    }
  }

  @Override
  public <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action) {
    checkNotInsideRun();
    Serde<T> serde = serdeFactory.create(typeTag);
    return DurableFuture.fromAsyncResult(
            Util.awaitCompletableFuture(
                handlerContext.submitRun(
                    name,
                    runCompleter ->
                        serviceExecutor.execute(
                            () -> {
                              Slice result;
                              INSIDE_RUN.set(Boolean.TRUE);
                              try {
                                result = serde.serialize(action.get());
                              } catch (Throwable e) {
                                INSIDE_RUN.remove();
                                runCompleter.proposeFailure(e, retryPolicy);
                                return;
                              }
                              INSIDE_RUN.remove();
                              runCompleter.proposeSuccess(result);
                            }))),
            serviceExecutor)
        .mapWithoutExecutor(serde::deserialize);
  }

  @Override
  public <T> Awakeable<T> awakeable(TypeTag<T> typeTag) throws TerminalException {
    checkNotInsideRun();
    Serde<T> serde = serdeFactory.create(typeTag);
    // Retrieve the awakeable
    HandlerContext.Awakeable awakeable = Util.awaitCompletableFuture(handlerContext.awakeable());
    return new Awakeable<>(awakeable.asyncResult(), serviceExecutor, serde, awakeable.id());
  }

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return new AwakeableHandle() {
      @Override
      public <T> void resolve(TypeTag<T> serde, @NonNull T payload) {
        checkNotInsideRun();
        Util.awaitCompletableFuture(
            handlerContext.resolveAwakeable(
                id,
                Util.executeOrFail(
                    handlerContext, serdeFactory.create(serde)::serialize, payload)));
      }

      @Override
      public void reject(String reason) {
        checkNotInsideRun();
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
      public DurableFuture<T> future() {
        checkNotInsideRun();
        AsyncResult<Slice> result = Util.awaitCompletableFuture(handlerContext.promise(key.name()));
        return DurableFuture.fromAsyncResult(result, serviceExecutor)
            .mapWithoutExecutor(serdeFactory.create(key.serdeInfo())::deserialize);
      }

      @Override
      public Output<T> peek() {
        checkNotInsideRun();
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
        checkNotInsideRun();
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
        checkNotInsideRun();
        Util.awaitCompletableFuture(
            Util.awaitCompletableFuture(
                    handlerContext.rejectPromise(key.name(), new TerminalException(reason)))
                .poll());
      }
    };
  }
}
