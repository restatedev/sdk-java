// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.function.ThrowingBiConsumer;
import dev.restate.sdk.common.function.ThrowingBiFunction;
import dev.restate.sdk.common.function.ThrowingConsumer;
import dev.restate.sdk.common.function.ThrowingFunction;
import dev.restate.sdk.common.syscalls.HandlerSpecification;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import dev.restate.sdk.common.syscalls.Syscalls;
import io.opentelemetry.context.Scope;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/** Adapter class for {@link dev.restate.sdk.common.syscalls.HandlerRunner} to use the Java API. */
public class HandlerRunner<REQ, RES>
    implements dev.restate.sdk.common.syscalls.HandlerRunner<REQ, RES, HandlerRunner.Options> {
  private final ThrowingBiFunction<Context, REQ, RES> runner;

  private static final Logger LOG = LogManager.getLogger(HandlerRunner.class);

  HandlerRunner(ThrowingBiFunction<? extends Context, REQ, RES> runner) {
    //noinspection unchecked
    this.runner = (ThrowingBiFunction<Context, REQ, RES>) runner;
  }

  @Override
  public void run(
      HandlerSpecification<REQ, RES> handlerSpecification,
      Syscalls syscalls,
      @Nullable Options options,
      SyscallCallback<ByteBuffer> callback) {
    if (options == null) {
      options = Options.DEFAULT;
    }

    // Wrap the executor for setting/unsetting the thread local
    Options finalOptions = options;
    Executor wrapped =
        runnable ->
            finalOptions.executor.execute(
                () -> {
                  SYSCALLS_THREAD_LOCAL.set(syscalls);
                  try (Scope ignored = syscalls.request().otelContext().makeCurrent()) {
                    runnable.run();
                  } finally {
                    SYSCALLS_THREAD_LOCAL.remove();
                  }
                });
    wrapped.execute(
        () -> {
          // Any context switching, if necessary, will be done by ResolvedEndpointHandler
          Context ctx = new ContextImpl(syscalls);

          // Parse input
          REQ req;
          try {
            req =
                handlerSpecification.getRequestSerde().deserialize(syscalls.request().bodyBuffer());
          } catch (Throwable e) {
            LOG.warn("Cannot deserialize input", e);
            callback.onCancel(
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
            callback.onCancel(e);
            return;
          }

          // Serialize output
          ByteBuffer serializedResult;
          try {
            serializedResult = handlerSpecification.getResponseSerde().serializeToByteBuffer(res);
          } catch (Throwable e) {
            LOG.warn("Cannot serialize output", e);
            callback.onCancel(
                new TerminalException(
                    TerminalException.INTERNAL_SERVER_ERROR_CODE,
                    "Cannot serialize output: " + e.getMessage()));
            return;
          }

          // Complete callback
          callback.onSuccess(serializedResult);
        });
  }

  public static <CTX extends Context, REQ, RES> HandlerRunner<REQ, RES> of(
      ThrowingBiFunction<CTX, REQ, RES> runner) {
    return new HandlerRunner<>(runner);
  }

  @SuppressWarnings("unchecked")
  public static <CTX extends Context, RES> HandlerRunner<Void, RES> of(
      ThrowingFunction<CTX, RES> runner) {
    return new HandlerRunner<>((context, o) -> runner.apply((CTX) context));
  }

  @SuppressWarnings("unchecked")
  public static <CTX extends Context, REQ> HandlerRunner<REQ, Void> of(
      ThrowingBiConsumer<CTX, REQ> runner) {
    return new HandlerRunner<>(
        (context, o) -> {
          runner.accept((CTX) context, o);
          return null;
        });
  }

  @SuppressWarnings("unchecked")
  public static <CTX extends Context> HandlerRunner<Void, Void> of(ThrowingConsumer<CTX> runner) {
    return new HandlerRunner<>(
        (ctx, o) -> {
          runner.accept((CTX) ctx);
          return null;
        });
  }

  public static class Options {
    public static final Options DEFAULT = new Options(Executors.newCachedThreadPool());

    private final Executor executor;

    /**
     * You can run on virtual threads by using the executor {@code
     * Executors.newVirtualThreadPerTaskExecutor()}.
     */
    public Options(Executor executor) {
      this.executor = executor;
    }
  }
}
