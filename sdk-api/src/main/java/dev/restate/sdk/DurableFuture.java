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
 * A {@link DurableFuture} allows to await an asynchronous result. Once {@link #await()} is called,
 * the execution stops until the asynchronous result is available.
 *
 * <p>The result can be either a success or a failure. In case of a failure, {@link #await()} will
 * throw a {@link TerminalException}.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 *
 * @param <T> type of the future result
 */
public abstract class DurableFuture<T> {

  protected abstract AsyncResult<T> asyncResult();

  protected abstract Executor serviceExecutor();

  /**
   * Wait for this {@link DurableFuture} to complete.
   *
   * <p>Executing this method may trigger the suspension of the function.
   *
   * <p><b>NOTE</b>: You should never wrap this function in a try-catch catching {@link Throwable},
   * as it will catch {@link AbortedExecutionException} as well, which will prevent the service
   * invocation to orderly suspend.
   *
   * @throws TerminalException if this future was completed with a failure
   */
  public final T await() throws TerminalException {
    return Util.awaitCompletableFuture(asyncResult().poll());
  }

  /**
   * Same as {@link #await()}, but throws a {@link TimeoutException} if this {@link DurableFuture}
   * doesn't complete before the provided {@code timeout}.
   */
  public final T await(Duration timeout) throws TerminalException {
    return this.withTimeout(timeout).await();
  }

  /**
   * @return a {@link DurableFuture} that throws a {@link TimeoutException} if this future doesn't
   *     complete before the provided {@code timeout}.
   */
  public final DurableFuture<T> withTimeout(Duration timeout) {
    return any(
            this,
            fromAsyncResult(
                Util.awaitCompletableFuture(asyncResult().ctx().timer(timeout, null)),
                this.serviceExecutor()))
        .mapWithoutExecutor(
            i -> {
              if (i == 1) {
                throw new TimeoutException("Timed out waiting for durable future after " + timeout);
              }
              return this.await();
            });
  }

  /**
   * Map the success result of this {@link DurableFuture}.
   *
   * @param mapper the mapper to execute if this {@link DurableFuture} completes with success. The
   *     mapper can throw a {@link TerminalException}, thus failing the resulting {@link
   *     DurableFuture}.
   * @return a new {@link DurableFuture} with the mapped result, when completed
   */
  public final <U> DurableFuture<U> map(ThrowingFunction<T, U> mapper) {
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
   * Map both the success and the failure result of this {@link DurableFuture}.
   *
   * @param successMapper the mapper to execute if this {@link DurableFuture} completes with
   *     success. The mapper can throw a {@link TerminalException}, thus failing the resulting
   *     {@link DurableFuture}.
   * @param failureMapper the mapper to execute if this {@link DurableFuture} completes with
   *     failure. The mapper can throw a {@link TerminalException}, thus failing the resulting
   *     {@link DurableFuture}.
   * @return a new {@link DurableFuture} with the mapped result, when completed
   */
  public final <U> DurableFuture<U> map(
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
   * Map the failure result of this {@link DurableFuture}.
   *
   * @param failureMapper the mapper to execute if this {@link DurableFuture} completes with
   *     failure. The mapper can throw a {@link TerminalException}, thus failing the resulting
   *     {@link DurableFuture}.
   * @return a new {@link DurableFuture} with the mapped result, when completed
   */
  public final DurableFuture<T> mapFailure(ThrowingFunction<TerminalException, T> failureMapper) {
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
  final <U> DurableFuture<U> mapWithoutExecutor(ThrowingFunction<T, U> mapper) {
    return fromAsyncResult(
        asyncResult().map(i -> CompletableFuture.completedFuture(mapper.apply(i)), null),
        this.serviceExecutor());
  }

  static <T> DurableFuture<T> fromAsyncResult(
      AsyncResult<T> asyncResult, Executor serviceExecutor) {
    return new SingleDurableFuture<>(asyncResult, serviceExecutor);
  }

  /**
   * Create an {@link DurableFuture} that awaits any of the given futures. The resulting {@link
   * DurableFuture} returns the index of the completed future in the provided list.
   *
   * @see Select
   */
  public static DurableFuture<Integer> any(
      DurableFuture<?> first, DurableFuture<?> second, DurableFuture<?>... others) {
    List<DurableFuture<?>> durableFutures = new ArrayList<>(2 + others.length);
    durableFutures.add(first);
    durableFutures.add(second);
    durableFutures.addAll(Arrays.asList(others));
    return any(durableFutures);
  }

  /**
   * Create an {@link DurableFuture} that awaits any of the given futures. The resulting {@link
   * DurableFuture} returns the index of the completed future in the provided list.
   *
   * <p>An empty list is not supported and will throw {@link IllegalArgumentException}.
   *
   * @see Select
   */
  public static DurableFuture<Integer> any(List<DurableFuture<?>> durableFutures) {
    if (durableFutures.isEmpty()) {
      throw new IllegalArgumentException("DurableFuture.any doesn't support an empty list");
    }
    List<AsyncResult<?>> ars =
        durableFutures.stream().map(DurableFuture::asyncResult).collect(Collectors.toList());
    HandlerContext ctx = ars.get(0).ctx();
    return fromAsyncResult(ctx.createAnyAsyncResult(ars), durableFutures.get(0).serviceExecutor());
  }

  /**
   * Create an {@link DurableFuture} that awaits all the given futures.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#allOf(CompletableFuture[])}.
   */
  public static DurableFuture<Void> all(
      DurableFuture<?> first, DurableFuture<?> second, DurableFuture<?>... others) {
    List<DurableFuture<?>> durableFutures = new ArrayList<>(2 + others.length);
    durableFutures.add(first);
    durableFutures.add(second);
    durableFutures.addAll(Arrays.asList(others));

    return all(durableFutures);
  }

  /**
   * Create an {@link DurableFuture} that awaits all the given futures.
   *
   * <p>An empty list is not supported and will throw {@link IllegalArgumentException}.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#allOf(CompletableFuture[])}.
   */
  public static DurableFuture<Void> all(List<DurableFuture<?>> durableFutures) {
    if (durableFutures.isEmpty()) {
      throw new IllegalArgumentException("DurableFuture.all doesn't support an empty list");
    }
    if (durableFutures.size() == 1) {
      return durableFutures.get(0).mapWithoutExecutor(unused -> null);
    } else {
      List<AsyncResult<?>> ars =
          durableFutures.stream().map(DurableFuture::asyncResult).collect(Collectors.toList());
      HandlerContext ctx = ars.get(0).ctx();
      return fromAsyncResult(
          ctx.createAllAsyncResult(ars), durableFutures.get(0).serviceExecutor());
    }
  }

  static final class SingleDurableFuture<T> extends DurableFuture<T> {

    private final AsyncResult<T> asyncResult;
    private final Executor serviceExecutor;

    SingleDurableFuture(AsyncResult<T> asyncResult, Executor serviceExecutor) {
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
