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
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.interceptor.RunContextPropagator;
import dev.restate.sdk.interceptor.RunInterceptor;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.SerdePipeline;
import dev.restate.serde.TypeTag;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("deprecation")
class ContextImpl implements ObjectContext, WorkflowContext {

  private static final ThreadLocal<Boolean> INSIDE_RUN = new ThreadLocal<>();

  private final HandlerContext handlerContext;
  private final HandlerRequest request;
  private final Executor serviceExecutor;
  private final SerdeFactory serdeFactory;
  private final SerdePipeline serdePipeline;
  private final RestateRandom random;
  private final RunInterceptor runInterceptor;
  private final RunContextPropagator runContextPropagator;

  /**
   * @param request the request of the invocation, with the decoded body.
   */
  ContextImpl(
      HandlerContext handlerContext,
      HandlerRequest request,
      Executor serviceExecutor,
      SerdeFactory serdeFactory,
      RunInterceptor runInterceptor,
      RunContextPropagator runContextPropagator) {
    this.handlerContext = handlerContext;
    this.request = request;
    this.serviceExecutor = serviceExecutor;
    this.serdeFactory = serdeFactory;
    this.serdePipeline = new SerdePipeline(serdeFactory, handlerContext.payloadCodec());
    this.random = new RestateRandom(this.request().invocationId().toRandomSeed());
    this.runInterceptor = runInterceptor;
    this.runContextPropagator = runContextPropagator;
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
    return this.request;
  }

  @Override
  public <T> Optional<T> get(StateKey<T> key) {
    checkNotInsideRun();
    return DurableFutureImpl.single(
            handlerContext,
            Util.awaitCompletableFuture(handlerContext.get(key.name())),
            opt -> opt.map(s -> decode(key.serdeInfo(), s)))
        .await();
  }

  @Override
  public Collection<String> stateKeys() {
    checkNotInsideRun();
    return DurableFutureImpl.single(
            handlerContext, Util.awaitCompletableFuture(handlerContext.getKeys()))
        .await();
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
    Util.awaitCompletableFuture(handlerContext.set(key.name(), encode(key.serdeInfo(), value)));
  }

  @Override
  public DurableFuture<Void> timer(String name, Duration duration) {
    checkNotInsideRun();
    return DurableFutureImpl.single(
        handlerContext, Util.awaitCompletableFuture(handlerContext.timer(duration, name)));
  }

  @Override
  public <T, R> CallDurableFuture<R> call(Request<T, R> request) {
    checkNotInsideRun();
    Slice input = encode(request.getRequestTypeTag(), request.getRequest());
    HandlerContext.CallHandles callHandles =
        Util.awaitCompletableFuture(
            handlerContext.call(
                request.getTarget(),
                input,
                request.getIdempotencyKey(),
                request.getLimitKey(),
                request.getHeaders() == null
                    ? Collections.emptyList()
                    : request.getHeaders().entrySet()));

    return new CallDurableFuture<>(
        handlerContext, callHandles, s -> decode(request.getResponseTypeTag(), s));
  }

  @Override
  public <Req, Res> InvocationHandle<Res> send(
      Request<Req, Res> request, @Nullable Duration delay) {
    checkNotInsideRun();
    Slice input = encode(request.getRequestTypeTag(), request.getRequest());

    var invocationIdAwaitable =
        DurableFutureImpl.single(
            handlerContext,
            Util.awaitCompletableFuture(
                handlerContext.send(
                    request.getTarget(),
                    input,
                    request.getIdempotencyKey(),
                    request.getLimitKey(),
                    request.getHeaders() == null
                        ? Collections.emptyList()
                        : request.getHeaders().entrySet(),
                    delay)));

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
      return DurableFutureImpl.single(
          handlerContext,
          Util.awaitCompletableFuture(handlerContext.attachInvocation(invocationId())),
          s -> decode(responseSerde, s));
    }

    @Override
    public Output<R> getOutput() {
      checkNotInsideRun();
      return DurableFutureImpl.single(
              handlerContext,
              Util.awaitCompletableFuture(handlerContext.getInvocationOutput(invocationId())),
              o -> o.map(s -> decode(responseSerde, s)))
          .await();
    }

    @Override
    public SignalHandle signal(String name) {
      String invocationId = invocationId();
      return new SignalHandle() {
        @Override
        public <T> void resolve(TypeTag<T> typeTag, T payload) {
          checkNotInsideRun();
          Util.awaitCompletableFuture(
              handlerContext.resolveSignal(invocationId, name, encode(typeTag, payload)));
        }

        @Override
        public void reject(String reason) {
          checkNotInsideRun();
          Util.awaitCompletableFuture(
              handlerContext.rejectSignal(invocationId, name, new TerminalException(reason)));
        }
      };
    }
  }

  @Override
  public <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action) {
    checkNotInsideRun();
    Serde<T> serde = serdeFactory.create(typeTag);

    // Capture thread local state here
    RunContextPropagator.CapturedContext capturedContext = runContextPropagator.capture();

    // Run closure will run the action using the serviceExecutor.
    // The capturedContext dance here is to propagate thread locals.
    Consumer<HandlerContext.RunCompleter> runClosure =
        runCompleter ->
            serviceExecutor.execute(
                capturedContext.wrap(
                    () -> executeRunAction(name, serde, retryPolicy, action, runCompleter)));

    return DurableFutureImpl.single(
        handlerContext,
        Util.awaitCompletableFuture(handlerContext.submitRun(name, runClosure)),
        s -> decode(serde, s));
  }

  private <T> void executeRunAction(
      @Nullable String name,
      Serde<T> serde,
      RetryPolicy retryPolicy,
      ThrowingSupplier<T> action,
      HandlerContext.RunCompleter runCompleter) {
    AtomicReference<Slice> resultHolder = new AtomicReference<>();

    RunInterceptor.Next userAction =
        () -> {
          INSIDE_RUN.set(Boolean.TRUE);
          try {
            T value;
            try {
              value = action.get();
            } catch (Throwable t) {
              // action.get() throws Throwable (ThrowingSupplier); propagate non-Exception
              // throwables (Errors, AbortedExecutionException) via sneakyThrow so they
              // cross Next.proceed() which declares throws Exception.
              sneakyThrow(t);
              return;
            }
            resultHolder.set(serde.serialize(value));
          } finally {
            INSIDE_RUN.remove();
          }
        };

    try {
      runInterceptor.aroundRun(new RunInterceptor.Context(this.request, name), userAction);
    } catch (Throwable t) {
      runCompleter.proposeFailure(t, retryPolicy);
      return;
    }

    // Encoding failures are not run failures, but fail the invocation, which is then retried.
    Slice result;
    try {
      result = Util.awaitCompletableFuture(serdePipeline.payloadCodec().encode(resultHolder.get()));
    } catch (Throwable t) {
      handlerContext.fail(t);
      return;
    }
    runCompleter.proposeSuccess(result);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable t) throws E {
    throw (E) t;
  }

  @Override
  public <T> Awakeable<T> awakeable(TypeTag<T> typeTag) throws TerminalException {
    checkNotInsideRun();
    Serde<T> serde = serdeFactory.create(typeTag);
    // Retrieve the awakeable
    HandlerContext.Awakeable awakeable = Util.awaitCompletableFuture(handlerContext.awakeable());
    return new Awakeable<>(
        handlerContext, awakeable.handle(), s -> decode(serde, s), awakeable.id());
  }

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return new AwakeableHandle() {
      @Override
      public <T> void resolve(TypeTag<T> serde, @NonNull T payload) {
        checkNotInsideRun();
        Util.awaitCompletableFuture(handlerContext.resolveAwakeable(id, encode(serde, payload)));
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
  public <T> DurableFuture<T> signal(String name, TypeTag<T> typeTag) throws TerminalException {
    checkNotInsideRun();
    Serde<T> serde = serdeFactory.create(typeTag);
    return DurableFutureImpl.single(
        handlerContext,
        Util.awaitCompletableFuture(handlerContext.signal(name)),
        s -> decode(serde, s));
  }

  @Override
  public RestateRandom random() {
    return this.random;
  }

  @Override
  public <T> DurablePromise<T> promise(DurablePromiseKey<T> key) {
    return new DurablePromise<>() {
      @Override
      public DurableFuture<T> future() {
        checkNotInsideRun();
        return DurableFutureImpl.single(
            handlerContext,
            Util.awaitCompletableFuture(handlerContext.promise(key.name())),
            s -> decode(key.serdeInfo(), s));
      }

      @Override
      public Output<T> peek() {
        checkNotInsideRun();
        return DurableFutureImpl.single(
                handlerContext,
                Util.awaitCompletableFuture(handlerContext.peekPromise(key.name())),
                o -> o.map(s -> decode(key.serdeInfo(), s)))
            .await();
      }
    };
  }

  @Override
  public <T> DurablePromiseHandle<T> promiseHandle(DurablePromiseKey<T> key) {
    return new DurablePromiseHandle<>() {
      @Override
      public void resolve(T payload) throws IllegalStateException {
        checkNotInsideRun();
        DurableFutureImpl.single(
                handlerContext,
                Util.awaitCompletableFuture(
                    handlerContext.resolvePromise(key.name(), encode(key.serdeInfo(), payload))))
            .await();
      }

      @Override
      public void reject(String reason) throws IllegalStateException {
        checkNotInsideRun();
        DurableFutureImpl.single(
                handlerContext,
                Util.awaitCompletableFuture(
                    handlerContext.rejectPromise(key.name(), new TerminalException(reason))))
            .await();
      }
    };
  }

  // -- Serialization and payload codec

  /** Serialize and encode the value. Failures fail the invocation. */
  private <T> Slice encode(TypeTag<T> typeTag, T value) {
    return Util.executeOrFail(
        handlerContext, () -> Util.awaitCompletableFuture(serdePipeline.encode(typeTag, value)));
  }

  /**
   * Decode and deserialize the value. This is used as mapper of the {@link
   * DurableFutureImpl.Single} nodes, so it runs when the node is completed, before evaluating the
   * rest of the future tree.
   */
  private <T> T decode(TypeTag<T> typeTag, Slice value) {
    return Util.awaitCompletableFuture(serdePipeline.decode(typeTag, value));
  }
}
