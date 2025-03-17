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
import java.util.concurrent.Executor;
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

  protected abstract Executor serviceExecutor();

  /**
   * Wait for the current awaitable to complete. Executing this method may trigger the suspension of
   * the function.
   *
   * <p><b>NOTE</b>: You should never wrap this function in a try-catch catching {@link Throwable},
   * as it will catch {@link AbortedExecutionException} as well, which will prevent the service
   * invocation to orderly suspend.
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
   * @return an Awaitable that throws a {@link TimeoutException} if this awaitable doesn't complete
   *     before the provided {@code timeout}.
   */
  public final Awaitable<T> withTimeout(Duration timeout) {
    return any(
            this,
            fromAsyncResult(
                Util.awaitCompletableFuture(asyncResult().ctx().timer(timeout, null)),
                this.serviceExecutor()))
        .mapWithoutExecutor(
            i -> {
              if (i == 1) {
                throw new TimeoutException("Timed out waiting for awaitable after " + timeout);
              }
              return this.await();
            });
  }

  /**
   * Map the success result of this {@link Awaitable}.
   *
   * @param mapper the mapper to execute if this {@link Awaitable} completes with success. The
   *     mapper can throw a {@link TerminalException}, thus failing the resulting {@link Awaitable}.
   * @return a new {@link Awaitable} with the mapped result, when completed
   */
  public final <U> Awaitable<U> map(ThrowingFunction<T, U> mapper) {
    return fromAsyncResult(
        asyncResult()
            .map(
                t ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            return mapper.apply(t);
                          } catch (Throwable e) {
                            Util.sneakyThrow(e);
                            return null;
                          }
                        },
                        serviceExecutor()),
                null),
        this.serviceExecutor());
  }

  /**
   * Map both the success and the failure result of this {@link Awaitable}.
   *
   * @param successMapper the mapper to execute if this {@link Awaitable} completes with success.
   *     The mapper can throw a {@link TerminalException}, thus failing the resulting {@link
   *     Awaitable}.
   * @param failureMapper the mapper to execute if this {@link Awaitable} completes with failure.
   *     The mapper can throw a {@link TerminalException}, thus failing the resulting {@link
   *     Awaitable}.
   * @return a new {@link Awaitable} with the mapped result, when completed
   */
  public final <U> Awaitable<U> map(
      ThrowingFunction<T, U> successMapper, ThrowingFunction<TerminalException, U> failureMapper) {
    return fromAsyncResult(
        asyncResult()
            .map(
                t ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            return successMapper.apply(t);
                          } catch (Throwable e) {
                            Util.sneakyThrow(e);
                            return null;
                          }
                        },
                        serviceExecutor()),
                t ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            return failureMapper.apply(t);
                          } catch (Throwable e) {
                            Util.sneakyThrow(e);
                            return null;
                          }
                        },
                        serviceExecutor())),
        this.serviceExecutor());
  }

  /**
   * Map the failure result of this {@link Awaitable}.
   *
   * @param failureMapper the mapper to execute if this {@link Awaitable} completes with failure.
   *     The mapper can throw a {@link TerminalException}, thus failing the resulting {@link
   *     Awaitable}.
   * @return a new {@link Awaitable} with the mapped result, when completed
   */
  public final Awaitable<T> mapFailure(ThrowingFunction<TerminalException, T> failureMapper) {
    return fromAsyncResult(
        asyncResult()
            .mapFailure(
                t ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            return failureMapper.apply(t);
                          } catch (Throwable e) {
                            Util.sneakyThrow(e);
                            return null;
                          }
                        },
                        serviceExecutor())),
        this.serviceExecutor());
  }

  /**
   * Map without executor switching. This is an optimization used only internally for operations
   * safe to perform without switching executor.
   */
  final <U> Awaitable<U> mapWithoutExecutor(ThrowingFunction<T, U> mapper) {
    return fromAsyncResult(
        asyncResult().map(i -> CompletableFuture.completedFuture(mapper.apply(i)), null),
        this.serviceExecutor());
  }

  static <T> Awaitable<T> fromAsyncResult(AsyncResult<T> asyncResult, Executor serviceExecutor) {
    return new SingleAwaitable<>(asyncResult, serviceExecutor);
  }

  /**
   * Create an {@link Awaitable} that awaits any of the given awaitables. The resulting {@link
   * Awaitable} returns the index of the completed awaitable in the provided list.
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
   * Create an {@link Awaitable} that awaits any of the given awaitables. The resulting {@link
   * Awaitable} returns the index of the completed awaitable in the provided list.
   *
   * <p>An empty list is not supported and will throw {@link IllegalArgumentException}.
   */
  public static Awaitable<Integer> any(List<Awaitable<?>> awaitables) {
    if (awaitables.isEmpty()) {
      throw new IllegalArgumentException("Awaitable any doesn't support an empty list");
    }
    List<AsyncResult<?>> ars =
        awaitables.stream().map(Awaitable::asyncResult).collect(Collectors.toList());
    HandlerContext ctx = ars.get(0).ctx();
    return fromAsyncResult(ctx.createAnyAsyncResult(ars), awaitables.get(0).serviceExecutor());
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
      return awaitables.get(0).mapWithoutExecutor(unused -> null);
    } else {
      List<AsyncResult<?>> ars =
          awaitables.stream().map(Awaitable::asyncResult).collect(Collectors.toList());
      HandlerContext ctx = ars.get(0).ctx();
      return fromAsyncResult(ctx.createAllAsyncResult(ars), awaitables.get(0).serviceExecutor());
    }
  }

  static final class SingleAwaitable<T> extends Awaitable<T> {

    private final AsyncResult<T> asyncResult;
    private final Executor serviceExecutor;

    SingleAwaitable(AsyncResult<T> asyncResult, Executor serviceExecutor) {
      this.asyncResult = asyncResult;
      this.serviceExecutor = serviceExecutor;
    }

    @Override
    protected AsyncResult<T> asyncResult() {
      return this.asyncResult;
    }

    @Override
    protected Executor serviceExecutor() {
      return serviceExecutor;
    }
  }
}
