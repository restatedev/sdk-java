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
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.types.AbortedExecutionException;
import dev.restate.sdk.types.TerminalException;
import dev.restate.sdk.types.TimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * An {@code Awaitable} allows to await an asynchronous result. Once {@code await()} is called, the
 * execution stops until the asynchronous result is available.
 *
 * <p>The result can be either a success or a failure. In case of a failure, {@code await()} will
 * throw a {@link TerminalException}.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 *
 * @param <T> type of the awaitable result
 */
public abstract class Awaitable<T> {

  protected abstract AsyncResult<T> asyncResult();

  /**
   * Wait for the current awaitable to complete. Executing this method may trigger the suspension of
   * the function.
   *
   * <p><b>NOTE</b>: You should never wrap this invocation in a try-catch catching {@link
   * RuntimeException}, as it will catch {@link AbortedExecutionException} as well.
   *
   * @throws TerminalException if the awaitable is ready and contains a failure
   */
  public final T await() throws TerminalException {
    return Util.awaitCompletableFuture(asyncResult().poll());
  }

  /**
   * Same as {@link #await()}, but throws a {@link TimeoutException} if this {@link Awaitable}
   * doesn't complete before the provided {@code timeout}.
   */
  public final T await(Duration timeout) throws TerminalException {
    return this.withTimeout(timeout).await();
  }

  /**
   * @return an Awaitable that throws a {@link TerminalException} if this awaitable doesn't complete
   *     before the provided {@code timeout}.
   */
  public final Awaitable<T> withTimeout(Duration timeout) {
    return any(
            this, fromAsyncResult(Util.awaitCompletableFuture(asyncResult().ctx().sleep(timeout))))
        .map(
            i -> {
              if (i == 1) {
                throw new TimeoutException("Timed out waiting for awaitable after " + timeout);
              }
              return this.await();
            });
  }

  /** Map the result of this {@link Awaitable}. */
  public final <U> Awaitable<U> map(ThrowingFunction<T, U> mapper) {
    return fromAsyncResult(asyncResult().map(mapper));
  }

  static <T> Awaitable<T> fromAsyncResult(AsyncResult<T> asyncResult) {
    return new SingleAwaitable<>(asyncResult);
  }

  /**
   * Create an {@link Awaitable} that awaits any of the given awaitables.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#anyOf(CompletableFuture[])}.
   */
  public static Awaitable<Integer> any(
      Awaitable<?> first, Awaitable<?> second, Awaitable<?>... others) {
    List<Awaitable<?>> awaitables = new ArrayList<>(2 + others.length);
    awaitables.add(first);
    awaitables.add(second);
    awaitables.addAll(Arrays.asList(others));
    return any(awaitables);
  }

  /**
   * Create an {@link Awaitable} that awaits any of the given awaitables.
   *
   * <p>An empty list is not supported and will throw {@link IllegalArgumentException}.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#anyOf(CompletableFuture[])}.
   */
  public static Awaitable<Integer> any(List<Awaitable<?>> awaitables) {
    if (awaitables.isEmpty()) {
      throw new IllegalArgumentException("Awaitable any doesn't support an empty list");
    }
    HandlerContext ctx = awaitables.get(0).asyncResult().ctx();
    return fromAsyncResult(
        ctx.createAnyAsyncResult(
            awaitables.stream().map(Awaitable::asyncResult).collect(Collectors.toList())));
  }

  /**
   * Create an {@link Awaitable} that awaits all the given awaitables.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#allOf(CompletableFuture[])}.
   */
  public static Awaitable<Void> all(
      Awaitable<?> first, Awaitable<?> second, Awaitable<?>... others) {
    List<Awaitable<?>> awaitables = new ArrayList<>(2 + others.length);
    awaitables.add(first);
    awaitables.add(second);
    awaitables.addAll(Arrays.asList(others));

    return all(awaitables);
  }

  /**
   * Create an {@link Awaitable} that awaits all the given awaitables.
   *
   * <p>An empty list is not supported and will throw {@link IllegalArgumentException}.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#allOf(CompletableFuture[])}.
   */
  public static Awaitable<Void> all(List<Awaitable<?>> awaitables) {
    if (awaitables.isEmpty()) {
      throw new IllegalArgumentException("Awaitable all doesn't support an empty list");
    }
    if (awaitables.size() == 1) {
      return awaitables.get(0).map(unused -> null);
    } else {
      HandlerContext ctx = awaitables.get(0).asyncResult().ctx();
      return fromAsyncResult(
          ctx.createAllAsyncResult(
              awaitables.stream().map(Awaitable::asyncResult).collect(Collectors.toList())));
    }
  }

  static class SingleAwaitable<T> extends Awaitable<T> {

    private final AsyncResult<T> asyncResult;

    SingleAwaitable(AsyncResult<T> asyncResult) {
      this.asyncResult = asyncResult;
    }

    @Override
    protected AsyncResult<T> asyncResult() {
      return this.asyncResult;
    }
  }
}
