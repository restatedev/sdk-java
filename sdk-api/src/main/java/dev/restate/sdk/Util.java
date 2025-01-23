// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.definition.HandlerContext;
import dev.restate.sdk.types.AbortedExecutionException;
import dev.restate.sdk.types.Output;
import dev.restate.sdk.serde.Serde;
import dev.restate.sdk.function.ThrowingFunction;
import dev.restate.sdk.definition.AsyncResult;
import dev.restate.sdk.definition.Result;
import dev.restate.sdk.common.syscalls.SyscallCallback;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

class Util {

  private Util() {}

  static <T> T blockOnResolve(HandlerContext handlerContext, AsyncResult<T> asyncResult) {
    if (!asyncResult.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> handlerContext.resolveDeferred(asyncResult, cb));
    }

    return Util.unwrapResult(asyncResult.toResult());
  }

  static <T> T awaitCompletableFuture(CompletableFuture<T> future) {
    try {
      return future.get();
    } catch (InterruptedException | CancellationException e) {
      AbortedExecutionException.sneakyThrow();
      return null; // Previous statement throws an exception
    } catch (ExecutionException e) {
      throw (RuntimeException) e.getCause();
    }
  }

  static <T> T blockOnSyscall(Consumer<SyscallCallback<T>> syscallExecutor) {
    CompletableFuture<T> fut = new CompletableFuture<>();
    syscallExecutor.accept(SyscallCallback.completingFuture(fut));
    return Util.awaitCompletableFuture(fut);
  }

  static <T> T unwrapResult(Result<T> res) {
    if (res.isSuccess()) {
      return res.getValue();
    }
    throw res.getFailure();
  }

  static <T> Optional<T> unwrapOptionalReadyResult(Result<T> res) {
    if (!res.isSuccess()) {
      throw res.getFailure();
    }
    if (res.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(res.getValue());
  }

  static <T> Output<T> unwrapOutputReadyResult(Result<T> res) {
    if (!res.isSuccess()) {
      throw res.getFailure();
    }
    if (res.isEmpty()) {
      return Output.notReady();
    }
    return Output.ready(res.getValue());
  }

  static <T, R> R executeMappingException(HandlerContext handlerContext, ThrowingFunction<T, R> fn, T t) {
    try {
      return fn.apply(t);
    } catch (Throwable e) {
      handlerContext.fail(e);
      AbortedExecutionException.sneakyThrow();
      return null;
    }
  }

  static <T> ByteBuffer serializeWrappingException(HandlerContext handlerContext, Serde<T> serde, T value) {
    return executeMappingException(handlerContext, serde::serializeToByteBuffer, value);
  }

  static <T> T deserializeWrappingException(
          HandlerContext handlerContext, Serde<T> serde, ByteBuffer byteString) {
    return executeMappingException(handlerContext, serde::deserialize, byteString);
  }
}
