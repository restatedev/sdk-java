// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingBiConsumer;
import dev.restate.common.function.ThrowingBiFunction;
import dev.restate.common.function.ThrowingConsumer;
import dev.restate.common.function.ThrowingFunction;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.types.TerminalException;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Adapter class for {@link dev.restate.sdk.endpoint.definition.HandlerRunner} to use the Java API.
 */
public class HandlerRunner<REQ, RES>
    implements dev.restate.sdk.endpoint.definition.HandlerRunner<REQ, RES> {
  private final ThrowingBiFunction<Context, REQ, RES> runner;
  private final SerdeFactory contextSerdeFactory;
  private final Options options;

  private static final Logger LOG = LogManager.getLogger(HandlerRunner.class);

  HandlerRunner(
      ThrowingBiFunction<? extends Context, REQ, RES> runner,
      SerdeFactory contextSerdeFactory,
      @Nullable Options options) {
    //noinspection unchecked
    this.runner = (ThrowingBiFunction<Context, REQ, RES>) runner;
    this.contextSerdeFactory = contextSerdeFactory;
    this.options = (options != null) ? options : Options.DEFAULT;
  }

  @Override
  public CompletableFuture<Slice> run(
      HandlerContext handlerContext,
      Serde<REQ> requestSerde,
      Serde<RES> responseSerde,
      AtomicReference<Runnable> onClosedInvocationStreamHook) {
    CompletableFuture<Slice> returnFuture = new CompletableFuture<>();

    // Wrap the executor for setting/unsetting the thread local
    Executor serviceExecutor =
        runnable ->
            options.executor.execute(
                () -> {
                  HANDLER_CONTEXT_THREAD_LOCAL.set(handlerContext);
                  try (Scope ignored = handlerContext.request().otelContext().makeCurrent()) {
                    runnable.run();
                  } finally {
                    HANDLER_CONTEXT_THREAD_LOCAL.remove();
                  }
                });
    serviceExecutor.execute(
        () -> {
          // Any context switching, if necessary, will be done by ResolvedEndpointHandler
          Context ctx = new ContextImpl(handlerContext, serviceExecutor, contextSerdeFactory);

          // Parse input
          REQ req;
          try {
            req = requestSerde.deserialize(handlerContext.request().body());
          } catch (Throwable e) {
            LOG.warn("Cannot deserialize input", e);
            returnFuture.completeExceptionally(
                new TerminalException(
                    TerminalException.BAD_REQUEST_CODE,
                    "Cannot deserialize input: " + e.getMessage()));
            return;
          }

          // Execute user code
          RES res;
          try {
            res = this.runner.apply(ctx, req);
          } catch (Throwable e) {
            returnFuture.completeExceptionally(e);
            return;
          }

          // Serialize output
          Slice serializedResult;
          try {
            serializedResult = responseSerde.serialize(res);
          } catch (Throwable e) {
            LOG.warn("Cannot serialize output", e);
            returnFuture.completeExceptionally(
                new TerminalException(
                    TerminalException.INTERNAL_SERVER_ERROR_CODE,
                    "Cannot serialize output: " + e.getMessage()));
            return;
          }

          // Complete callback
          returnFuture.complete(serializedResult);
        });

    return returnFuture;
  }

  /** Factory method for {@link HandlerRunner}, used by codegen */
  public static <CTX extends Context, REQ, RES> HandlerRunner<REQ, RES> of(
      ThrowingBiFunction<CTX, REQ, RES> runner,
      SerdeFactory contextSerdeFactory,
      @Nullable Options options) {
    return new HandlerRunner<>(runner, contextSerdeFactory, options);
  }

  /** Factory method for {@link HandlerRunner}, used by codegen */
  @SuppressWarnings("unchecked")
  public static <CTX extends Context, RES> HandlerRunner<Void, RES> of(
      ThrowingFunction<CTX, RES> runner,
      SerdeFactory contextSerdeFactory,
      @Nullable Options options) {
    return new HandlerRunner<>(
        (context, o) -> runner.apply((CTX) context), contextSerdeFactory, options);
  }

  /** Factory method for {@link HandlerRunner}, used by codegen */
  @SuppressWarnings("unchecked")
  public static <CTX extends Context, REQ> HandlerRunner<REQ, Void> of(
      ThrowingBiConsumer<CTX, REQ> runner,
      SerdeFactory contextSerdeFactory,
      @Nullable Options options) {
    return new HandlerRunner<>(
        (context, o) -> {
          runner.accept((CTX) context, o);
          return null;
        },
        contextSerdeFactory,
        options);
  }

  /** Factory method for {@link HandlerRunner}, used by codegen */
  @SuppressWarnings("unchecked")
  public static <CTX extends Context> HandlerRunner<Void, Void> of(
      ThrowingConsumer<CTX> runner, SerdeFactory contextSerdeFactory, @Nullable Options options) {
    return new HandlerRunner<>(
        (ctx, o) -> {
          runner.accept((CTX) ctx);
          return null;
        },
        contextSerdeFactory,
        options);
  }

  /**
   * {@link HandlerRunner} options. You can override the default options to configure the executor
   * where to run the handlers.
   *
   * <p>You can run on virtual threads by using the executor {@code
   * Executors.newVirtualThreadPerTaskExecutor()}.
   */
  public static final class Options
      implements dev.restate.sdk.endpoint.definition.HandlerRunner.Options {
    /**
     * Default options will use a {@link Executors#newCachedThreadPool()} shared among all the
     * {@link HandlerRunner} instances.
     */
    public static final Options DEFAULT = new Options(Executors.newCachedThreadPool());

    private final Executor executor;

    /**
     * You can run on virtual threads by using the executor {@code
     * Executors.newVirtualThreadPerTaskExecutor()}.
     */
    private Options(Executor executor) {
      this.executor = executor;
    }

    /** Copy this options setting the given {@code executor}. */
    public static Options withExecutor(Executor executor) {
      return new Options(executor);
    }
  }
}
