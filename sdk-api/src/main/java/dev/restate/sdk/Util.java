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
import dev.restate.common.function.ThrowingFunction;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.types.AbortedExecutionException;
import dev.restate.serde.Serde;
import org.jspecify.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

class Util {

  private Util() {}

  static <T, R> R executeMappingException(HandlerContext handlerContext, ThrowingFunction<T, R> fn, T t) {
    try {
      return fn.apply(t);
    } catch (Throwable e) {
      handlerContext.fail(e);
      AbortedExecutionException.sneakyThrow();
      return null;
    }
  }

  static <T> Slice serializeWrappingException(HandlerContext handlerContext, Serde<T> serde, T value) {
    return executeMappingException(handlerContext, serde::serialize, value);
  }

  static <T> @NonNull T awaitCompletableFuture(CompletableFuture<T> future) {
    try {
      return future.get();
    } catch (InterruptedException | CancellationException e) {
      AbortedExecutionException.sneakyThrow();
      return null; // Previous statement throws an exception
    } catch (ExecutionException e) {
      sneakyThrow(e.getCause());
      return null; // Previous statement throws an exception
    }
  }

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }
}
