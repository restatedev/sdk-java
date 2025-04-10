// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.function.ThrowingFunction;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.jspecify.annotations.NonNull;

class Util {

  private Util() {}

  static <T, R> R executeOrFail(HandlerContext handlerContext, ThrowingFunction<T, R> fn, T t) {
    try {
      return fn.apply(t);
    } catch (Throwable e) {
      handlerContext.fail(e);
      AbortedExecutionException.sneakyThrow();
      return null;
    }
  }

  static <R> R executeOrFail(HandlerContext handlerContext, ThrowingSupplier<R> fn) {
    try {
      return fn.get();
    } catch (Throwable e) {
      handlerContext.fail(e);
      AbortedExecutionException.sneakyThrow();
      return null;
    }
  }

  static <T> @NonNull T awaitCompletableFuture(CompletableFuture<T> future) {
    try {
      return future.get();
    } catch (InterruptedException | CancellationException e) {
      AbortedExecutionException.sneakyThrow();
      return null; // Previous statement throws an exception
    } catch (ExecutionException | CompletionException e) {
      sneakyThrow(e.getCause());
      return null; // Previous statement throws an exception
    }
  }

  @SuppressWarnings("unchecked")
  static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }
}
