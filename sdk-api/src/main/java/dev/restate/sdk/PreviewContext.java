// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.serde.Serde;
import dev.restate.sdk.types.TerminalException;
import dev.restate.sdk.function.ThrowingRunnable;
import dev.restate.sdk.function.ThrowingSupplier;

/**
 * Preview of new context features. Please note that the methods in this class <b>may break between
 * minor releases</b>, use with caution!
 *
 * <p>In order to use these methods, you <b>MUST enable the preview context</b>, through the
 * endpoint builders using {@code enablePreviewContext()}.
 */
public class PreviewContext {

  /**
   * @deprecated Use {@link Context#run(String, Serde, RetryPolicy, ThrowingSupplier)}
   */
  @Deprecated(since = "1.2", forRemoval = true)
  public static <T> T run(
      Context ctx, String name, Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return ctx.run(name, serde, retryPolicy, action);
  }

  /**
   * @deprecated Use {@link Context#run(String, RetryPolicy, ThrowingRunnable)}
   */
  @Deprecated(since = "1.2", forRemoval = true)
  public static void run(
      Context ctx, String name, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    ctx.run(name, retryPolicy, runnable);
  }

  /**
   * @deprecated Use {@link Context#run(Serde, RetryPolicy, ThrowingSupplier)}
   */
  @Deprecated(since = "1.2", forRemoval = true)
  public static <T> T run(
      Context ctx, Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return ctx.run(serde, retryPolicy, action);
  }

  /**
   * @deprecated Use {@link Context#run(RetryPolicy, ThrowingRunnable)}
   */
  @Deprecated(since = "1.2", forRemoval = true)
  public static void run(Context ctx, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    ctx.run(retryPolicy, runnable);
  }
}
