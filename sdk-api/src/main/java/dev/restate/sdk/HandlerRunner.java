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
import dev.restate.common.function.*;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.interceptor.HandlerInterceptor;
import dev.restate.sdk.interceptor.RunInterceptor;
import dev.restate.sdk.internal.ContextThreadLocal;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
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
  private final Executor executor;
  private final HandlerInterceptor handlerInterceptor;
  private final RunInterceptor runInterceptor;

  private static final Logger LOG = LogManager.getLogger(HandlerRunner.class);

  HandlerRunner(
      ThrowingBiFunction<? extends Context, REQ, RES> runner,
      SerdeFactory contextSerdeFactory,
      @Nullable Options options) {
    //noinspection unchecked
    this.runner = (ThrowingBiFunction<Context, REQ, RES>) runner;
    this.contextSerdeFactory = contextSerdeFactory;
    var opts = (options != null) ? options : Options.DEFAULT;
    this.executor = opts.executor;
    this.handlerInterceptor =
        HandlerInterceptor.Factory.combine(opts.handlerInterceptorFactories());
    this.runInterceptor = RunInterceptor.Factory.combine(opts.runInterceptorFactories());
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
            executor.execute(
                () -> {
                  HANDLER_CONTEXT_THREAD_LOCAL.set(handlerContext);
                  // TODO(tracing-plumbing): deprecate, superseded by sdk-interceptor-opentelemetry
                  try (Scope ignored =
                      handlerContext.request().openTelemetryContext().makeCurrent()) {
                    runnable.run();
                  } finally {
                    HANDLER_CONTEXT_THREAD_LOCAL.remove();
                  }
                });

    serviceExecutor.execute(
        () -> {
          // Any context switching, if necessary, will be done by ResolvedEndpointHandler
          Context ctx =
              new ContextImpl(handlerContext, serviceExecutor, contextSerdeFactory, runInterceptor);

          AtomicReference<Slice> resultHolder = new AtomicReference<>();

          HandlerInterceptor.Next userBlock =
              () -> {
                // Parse input
                REQ req;
                try {
                  req = requestSerde.deserialize(handlerContext.request().body());
                } catch (Throwable e) {
                  LOG.warn("Cannot deserialize input", e);
                  throw new TerminalException(
                      TerminalException.BAD_REQUEST_CODE,
                      "Cannot deserialize input: " + e.getMessage());
                }

                // Execute user code. The user runner declares throws Throwable so we
                // sneaky-throw any non-Exception throwables (Errors,
                // AbortedExecutionException) to propagate them through Next.proceed()
                // which is declared throws Exception.
                RES res;
                try {
                  ContextThreadLocal.setContext(ctx);
                  res = this.runner.apply(ctx, req);
                } catch (Throwable t1) {
                  sneakyThrow(t1);
                  return;
                } finally {
                  ContextThreadLocal.clearContext();
                }

                // Serialize output
                try {
                  resultHolder.set(responseSerde.serialize(res));
                } catch (Throwable e) {
                  LOG.warn("Cannot serialize output", e);
                  throw new TerminalException(
                      TerminalException.INTERNAL_SERVER_ERROR_CODE,
                      "Cannot serialize output: " + e.getMessage());
                }
              };

          try {
            handlerInterceptor.aroundHandler(
                new HandlerInterceptor.Context(
                    handlerContext.request(), handlerContext.attemptHeaders()),
                userBlock);
            returnFuture.complete(resultHolder.get());
          } catch (Throwable t) {
            returnFuture.completeExceptionally(t);
          }
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
   * {@link HandlerRunner} options, to configure the executor to use to run your services, and the
   * interceptors.
   *
   * <p>By default, executor will be configured to use virtual threads on Java 21+, or fallback to
   * {@link Executors#newCachedThreadPool()} for Java &lt; 21. The bounded pool is shared among all
   * {@link HandlerRunner} instances, and is used by {@link Restate#run}/{@link Context#run} as
   * well.
   *
   * <p>{@link HandlerInterceptor.Factory} and {@link RunInterceptor.Factory} registered via SPI are
   * also loaded by default.
   */
  public static final class Options
      implements dev.restate.sdk.endpoint.definition.HandlerRunner.Options {

    private static final Executor DEFAULT_EXECUTOR = createDefaultExecutor();
    private static final List<HandlerInterceptor.Factory> DEFAULT_HANDLER_INTERCEPTOR_FACTORIES =
        loadSpiHandlerFactories();
    private static final List<RunInterceptor.Factory> DEFAULT_RUN_INTERCEPTOR_FACTORIES =
        loadSpiRunFactories();

    /**
     * @deprecated Create a new instance of Options instead.
     */
    @Deprecated(forRemoval = true)
    public static final Options DEFAULT = new Options();

    private Executor executor;
    private ArrayList<HandlerInterceptor.Factory> handlerInterceptorFactories;
    private ArrayList<RunInterceptor.Factory> runInterceptorFactories;

    /** Create new options, check {@link Options} for the defaults documentation. */
    public Options() {
      this(
          DEFAULT_EXECUTOR,
          DEFAULT_HANDLER_INTERCEPTOR_FACTORIES,
          DEFAULT_RUN_INTERCEPTOR_FACTORIES);
    }

    private Options(
        Executor executor,
        List<HandlerInterceptor.Factory> handlerInterceptorFactories,
        List<RunInterceptor.Factory> runInterceptorFactories) {
      this.executor = executor;
      this.handlerInterceptorFactories = new ArrayList<>(handlerInterceptorFactories);
      this.runInterceptorFactories = new ArrayList<>(runInterceptorFactories);
    }

    /**
     * Create an instance of {@link Options} with the given {@code executor}.
     *
     * <p>The given executor is used for running the handler code, and {@link Restate#run}/{@link
     * Context#run} as well.
     */
    public static Options withExecutor(Executor executor) {
      return new Options(
          executor, DEFAULT_HANDLER_INTERCEPTOR_FACTORIES, DEFAULT_RUN_INTERCEPTOR_FACTORIES);
    }

    /**
     * Set the given {@code executor} as the handler executor.
     *
     * <p>The given executor is used for running the handler code, and {@link Restate#run}/{@link
     * Context#run} as well.
     *
     * @return self for fluency
     */
    public Options setExecutor(Executor executor) {
      this.executor = executor;
      return this;
    }

    /**
     * Append a {@link HandlerInterceptor.Factory} to the factories, as innermost in the chain.
     *
     * @return self for fluency
     */
    public Options addHandlerInterceptorFactory(HandlerInterceptor.Factory factory) {
      this.handlerInterceptorFactories.add(factory);
      return this;
    }

    /**
     * Append a {@link RunInterceptor.Factory} to the factories, as innermost in the chain.
     *
     * @return self for fluency
     */
    public Options addRunInterceptorFactory(RunInterceptor.Factory factory) {
      this.runInterceptorFactories.add(factory);
      return this;
    }

    /**
     * Overwrite the {@link HandlerInterceptor.Factory} chain with a new chain.
     *
     * @return self for fluency
     */
    public Options setHandlerInterceptorFactories(List<HandlerInterceptor.Factory> newFactories) {
      this.handlerInterceptorFactories = new ArrayList<>(newFactories);
      return this;
    }

    /**
     * Overwrite the {@link RunInterceptor.Factory} chain with a new chain.
     *
     * @return self for fluency
     */
    public Options setRunInterceptorFactories(List<RunInterceptor.Factory> newFactories) {
      this.runInterceptorFactories = new ArrayList<>(newFactories);
      return this;
    }

    public Executor executor() {
      return executor;
    }

    public List<HandlerInterceptor.Factory> handlerInterceptorFactories() {
      return Objects.requireNonNullElse(handlerInterceptorFactories, List.of());
    }

    public List<RunInterceptor.Factory> runInterceptorFactories() {
      return Objects.requireNonNullElse(runInterceptorFactories, List.of());
    }

    private static ExecutorService createDefaultExecutor() {
      // Try to use virtual threads if available (Java 21+)
      try {
        return (ExecutorService)
            Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
      } catch (Exception e) {
        LOG.debug(
            "Virtual threads not available, using unbounded thread pool. "
                + "If you need to customize the thread pool used by your restate handlers, "
                + "use HandlerRunner.Options.withExecutor() with Endpoint.bind()");

        return Executors.newCachedThreadPool();
      }
    }

    private static List<HandlerInterceptor.Factory> loadSpiHandlerFactories() {
      List<HandlerInterceptor.Factory> list = new ArrayList<>();
      ServiceLoader.load(HandlerInterceptor.Factory.class).forEach(list::add);
      return list;
    }

    private static List<RunInterceptor.Factory> loadSpiRunFactories() {
      List<RunInterceptor.Factory> list = new ArrayList<>();
      ServiceLoader.load(RunInterceptor.Factory.class).forEach(list::add);
      return list;
    }
  }

  static HandlerContext getHandlerContext() {
    return Objects.requireNonNull(
        dev.restate.sdk.endpoint.definition.HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL.get(),
        "Restate methods must be invoked from within a Restate handler");
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable t) throws E {
    throw (E) t;
  }
}
