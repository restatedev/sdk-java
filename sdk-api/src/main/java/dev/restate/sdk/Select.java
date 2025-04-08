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
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Select lets you await concurrently for multiple {@link DurableFuture}s to complete, and for the
 * first one to complete, either return its value directly or map it.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // Using awakeables as example here
 * var a1 = ctx.awakeable(String.class);
 * var a2 = ctx.awakeable(MyObject.class);
 * var a3 = ctx.awakeable(String.class);
 *
 * var result = Select.<String>select()
 *   // Just select the a1 as is
 *   .or(a1)
 *   // When selecting a2, map the success result
 *   .when(a2, myObject -> myObject.toString())
 *   // When selecting a3, map failure as another failure
 *   .when(a3, ThrowingFunction.identity(), ex -> {
 *     throw new TerminalException("a3 failed, too bad!");
 *   })
 *   // Finally await for the result
 *   .await();
 * }</pre>
 *
 * @param <T> the output value
 */
public final class Select<T> extends DurableFuture<T> {

  private final List<DurableFuture<?>> durableFutures;
  private AsyncResult<T> asyncResult;

  Select() {
    this.durableFutures = new ArrayList<>();
  }

  /**
   * Create a new {@link Select} operation.
   *
   * @param <T> The return of the select.
   */
  public static <T> Select<T> select() {
    return new Select<>();
  }

  /**
   * Add the given {@link DurableFuture} to this select.
   *
   * @return this, so it can be used fluently.
   */
  public Select<T> or(DurableFuture<T> durableFuture) {
    this.durableFutures.add(durableFuture);
    this.asyncResult = null;
    return this;
  }

  /**
   * Add the given {@link DurableFuture} to this select. If it completes first, the success result
   * will be mapped using {@code successMapper}, otherwise in case of {@link TerminalException}, the
   * exception will be thrown as is.
   *
   * @param durableFuture the {@link DurableFuture} to add to this select
   * @param successMapper the mapper to execute if the given {@link DurableFuture} completes with
   *     success. The mapper can throw a {@link TerminalException}, thus failing the resulting
   *     operation.
   * @return this, so it can be used fluently.
   */
  public <U> Select<T> when(DurableFuture<U> durableFuture, ThrowingFunction<U, T> successMapper) {
    this.durableFutures.add(durableFuture.map(successMapper));
    this.asyncResult = null;
    return this;
  }

  /**
   * Add the given {@link DurableFuture} to this select. If it completes first, the success result
   * will be mapped using {@code successMapper}, otherwise in case of {@link TerminalException}, the
   * exception will be mapped using {@code failureMapper}.
   *
   * @param durableFuture the {@link DurableFuture} to add to this select
   * @param successMapper the mapper to execute if the given {@link DurableFuture} completes with
   *     success. The mapper can throw a {@link TerminalException}, thus failing the resulting
   *     operation.
   * @param failureMapper the mapper to execute if the given {@link DurableFuture} completes with
   *     failure. The mapper can throw a {@link TerminalException}, thus failing the resulting
   *     operation.
   * @return this, so it can be used fluently.
   */
  public <U> Select<T> when(
      DurableFuture<U> durableFuture,
      ThrowingFunction<U, T> successMapper,
      ThrowingFunction<TerminalException, T> failureMapper) {
    this.durableFutures.add(durableFuture.map(successMapper, failureMapper));
    this.asyncResult = null;
    return this;
  }

  @Override
  protected AsyncResult<T> asyncResult() {
    if (this.asyncResult == null) {
      recreateAsyncResult();
    }
    return this.asyncResult;
  }

  @Override
  protected Executor serviceExecutor() {
    checkNonEmpty();
    return durableFutures.get(0).serviceExecutor();
  }

  private void checkNonEmpty() {
    if (durableFutures.isEmpty()) {
      throw new IllegalArgumentException("Select is empty");
    }
  }

  private void recreateAsyncResult() {
    checkNonEmpty();
    List<DurableFuture<?>> durableFutures = List.copyOf(this.durableFutures);
    List<AsyncResult<?>> ars =
        durableFutures.stream().map(DurableFuture::asyncResult).collect(Collectors.toList());
    HandlerContext ctx = ars.get(0).ctx();
    //noinspection unchecked
    this.asyncResult =
        ctx.createAnyAsyncResult(ars).map(i -> (CompletableFuture<T>) ars.get(i).poll());
  }
}
