// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import com.google.protobuf.ByteString;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.HandlerSpecification;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import dev.restate.sdk.common.syscalls.Syscalls;
import io.opentelemetry.context.Scope;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/** Adapter class for {@link dev.restate.sdk.common.syscalls.HandlerRunner} to use the Java API. */
public class HandlerRunner<REQ, RES>
    implements dev.restate.sdk.common.syscalls.HandlerRunner<REQ, RES, HandlerRunner.Options> {
  private final BiFunction<Context, REQ, RES> runner;

  private static final Logger LOG = LogManager.getLogger(HandlerRunner.class);

  HandlerRunner(BiFunction<? extends Context, REQ, RES> runner) {
    //noinspection unchecked
    this.runner = (BiFunction<Context, REQ, RES>) runner;
  }

  @Override
  public void run(
      HandlerSpecification<REQ, RES> handlerSpecification,
      Syscalls syscalls,
      @Nullable Options options,
      SyscallCallback<ByteString> callback) {
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
          } catch (Error e) {
            throw e;
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
          } catch (Error e) {
            throw e;
          } catch (Throwable e) {
            callback.onCancel(e);
            return;
          }

          // Serialize output
          ByteString serializedResult;
          try {
            serializedResult = handlerSpecification.getResponseSerde().serializeToByteString(res);
          } catch (Error e) {
            throw e;
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
      BiFunction<CTX, REQ, RES> runner) {
    return new HandlerRunner<>(runner);
  }

  @SuppressWarnings("unchecked")
  public static <CTX extends Context, RES> HandlerRunner<Void, RES> of(Function<CTX, RES> runner) {
    return new HandlerRunner<>((context, o) -> runner.apply((CTX) context));
  }

  @SuppressWarnings("unchecked")
  public static <CTX extends Context, REQ> HandlerRunner<REQ, Void> of(
      BiConsumer<CTX, REQ> runner) {
    return new HandlerRunner<>(
        (context, o) -> {
          runner.accept((CTX) context, o);
          return null;
        });
  }

  @SuppressWarnings("unchecked")
  public static <CTX extends Context> HandlerRunner<Void, Void> of(Consumer<CTX> runner) {
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
