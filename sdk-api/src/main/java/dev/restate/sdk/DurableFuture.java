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
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.TimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
public sealed interface DurableFuture<T> permits DurableFutureImpl {

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
  T await() throws TerminalException;

  /**
   * Same as {@link #await()}, but throws a {@link TimeoutException} if this {@link DurableFuture}
   * doesn't complete before the provided {@code timeout}.
   */
  T await(Duration timeout) throws TerminalException;

  /**
   * @return a {@link DurableFuture} that throws a {@link TimeoutException} if this future doesn't
   *     complete before the provided {@code timeout}.
   */
  DurableFuture<T> withTimeout(Duration timeout);

  /**
   * Map the success result of this {@link DurableFuture}.
   *
   * @param mapper the mapper to execute if this {@link DurableFuture} completes with success. The
   *     mapper can throw a {@link TerminalException}, thus failing the resulting {@link
   *     DurableFuture}.
   * @return a new {@link DurableFuture} with the mapped result, when completed
   */
  <U> DurableFuture<U> map(ThrowingFunction<T, U> mapper);

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
  <U> DurableFuture<U> map(
      ThrowingFunction<T, U> successMapper, ThrowingFunction<TerminalException, U> failureMapper);

  /**
   * Map the failure result of this {@link DurableFuture}.
   *
   * @param failureMapper the mapper to execute if this {@link DurableFuture} completes with
   *     failure. The mapper can throw a {@link TerminalException}, thus failing the resulting
   *     {@link DurableFuture}.
   * @return a new {@link DurableFuture} with the mapped result, when completed
   */
  DurableFuture<T> mapFailure(ThrowingFunction<TerminalException, T> failureMapper);

  /**
   * Create an {@link DurableFuture} that awaits any of the given futures. The resulting {@link
   * DurableFuture} returns the index of the completed future in the provided list.
   *
   * @see Select
   */
  static DurableFuture<Integer> any(
      DurableFuture<?> first, DurableFuture<?> second, DurableFuture<?>... others) {
    List<DurableFuture<?>> durableFutures = new ArrayList<>(2 + others.length);
    durableFutures.add(first);
    durableFutures.add(second);
    durableFutures.addAll(Arrays.asList(others));
    return new DurableFutureImpl.Any(DurableFutureImpl.fromList(durableFutures));
  }

  /**
   * Create an {@link DurableFuture} that awaits any of the given futures. The resulting {@link
   * DurableFuture} returns the index of the completed future in the provided list.
   *
   * <p>An empty list is not supported and will throw {@link IllegalArgumentException}.
   *
   * @see Select
   */
  static DurableFuture<Integer> any(List<DurableFuture<?>> durableFutures) {
    if (durableFutures.isEmpty()) {
      throw new IllegalArgumentException("DurableFuture.any doesn't support an empty list");
    }
    return new DurableFutureImpl.Any(List.copyOf(DurableFutureImpl.fromList(durableFutures)));
  }

  /**
   * Create an {@link DurableFuture} that awaits all the given futures.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#allOf(CompletableFuture[])}.
   */
  static DurableFuture<Void> all(
      DurableFuture<?> first, DurableFuture<?> second, DurableFuture<?>... others) {
    List<DurableFuture<?>> durableFutures = new ArrayList<>(2 + others.length);
    durableFutures.add(first);
    durableFutures.add(second);
    durableFutures.addAll(Arrays.asList(others));

    return new DurableFutureImpl.All(DurableFutureImpl.fromList(durableFutures));
  }

  /**
   * Create an {@link DurableFuture} that awaits all the given futures.
   *
   * <p>An empty list is not supported and will throw {@link IllegalArgumentException}.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#allOf(CompletableFuture[])}.
   */
  static DurableFuture<Void> all(List<DurableFuture<?>> durableFutures) {
    if (durableFutures.isEmpty()) {
      throw new IllegalArgumentException("DurableFuture.all doesn't support an empty list");
    }
    if (durableFutures.size() == 1) {
      return durableFutures.get(0).map(unused -> null);
    }
    return new DurableFutureImpl.All(List.copyOf(DurableFutureImpl.fromList(durableFutures)));
  }
}
