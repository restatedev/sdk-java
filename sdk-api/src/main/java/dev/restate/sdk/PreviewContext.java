// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.function.ThrowingRunnable;
import dev.restate.sdk.common.function.ThrowingSupplier;
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.Syscalls;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Preview of new context features. Please note that the methods in this class <b>may break between
 * minor releases</b>, use with caution!
 *
 * <p>In order to use these methods, you <b>MUST enable the preview context</b>, through the
 * endpoint builders using {@code enablePreviewContext()}.
 */
public class PreviewContext {

  /**
   * Like {@link Context#run(String, Serde, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   */
  public static <T> T run(
      Context ctx, String name, Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    Syscalls syscalls = ((ContextImpl) ctx).syscalls;
    CompletableFuture<CompletableFuture<ByteBuffer>> enterFut = new CompletableFuture<>();
    syscalls.enterSideEffectBlock(
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
          syscalls, serde, Util.awaitCompletableFuture(exitFut));
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
      syscalls.exitSideEffectBlockWithException(failure, retryPolicy, exitCallback);
    } else {
      syscalls.exitSideEffectBlock(
          Util.serializeWrappingException(syscalls, serde, res), exitCallback);
    }

    return Util.deserializeWrappingException(syscalls, serde, Util.awaitCompletableFuture(exitFut));
  }

  /**
   * Like {@link Context#run(String, ThrowingRunnable)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   */
  public static void run(
      Context ctx, String name, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    run(
        ctx,
        name,
        Serde.VOID,
        retryPolicy,
        () -> {
          runnable.run();
          return null;
        });
  }

  /**
   * Like {@link Context#run(Serde, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   */
  public static <T> T run(
      Context ctx, Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return run(ctx, null, serde, retryPolicy, action);
  }

  /**
   * Like {@link Context#run(ThrowingRunnable)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   */
  public static void run(Context ctx, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    run(ctx, null, retryPolicy, runnable);
  }
}
