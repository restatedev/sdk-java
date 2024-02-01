// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.Deferred;
import dev.restate.sdk.common.syscalls.Result;
import dev.restate.sdk.common.syscalls.Syscalls;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An {@code Awaitable} allows to await an asynchronous result. Once {@code await()} is called, the
 * execution stops until the asynchronous result is available.
 *
 * <p>The result can be either a success or a failure. In case of a failure, {@code await()} will
 * throw a {@link TerminalException}.
 *
 * @param <T> type of the awaitable result
 */
@NotThreadSafe
public abstract class Awaitable<T> {

  protected final Syscalls syscalls;

  Awaitable(Syscalls syscalls) {
    this.syscalls = syscalls;
  }

  protected abstract Deferred<?> deferred();

  protected abstract Result<T> awaitResult();

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
    return Util.unwrapResult(this.awaitResult());
  }

  /**
   * Same as {@link #await()}, but throws a {@link TimeoutException} if this {@link Awaitable}
   * doesn't complete before the provided {@code timeout}.
   */
  public final T await(Duration timeout) throws TerminalException, TimeoutException {
    Deferred<Void> sleep = Util.blockOnSyscall(cb -> this.syscalls.sleep(timeout, cb));
    Awaitable<Void> sleepAwaitable = single(this.syscalls, sleep);

    int index = any(this, sleepAwaitable).awaitIndex();

    if (index == 1) {
      throw new TimeoutException();
    }
    // This await is no-op now
    return this.await();
  }

  /** Map the result of this {@link Awaitable}. */
  public final <U> Awaitable<U> map(Function<T, U> mapper) {
    return new MappedAwaitable<>(
        this,
        result -> {
          if (result.isSuccess()) {
            return Result.success(mapper.apply(result.getValue()));
          }
          //noinspection unchecked
          return (Result<U>) result;
        });
  }

  static <T> Awaitable<T> single(Syscalls syscalls, Deferred<T> deferred) {
    return new SingleAwaitable<>(syscalls, deferred);
  }

  /**
   * Create an {@link Awaitable} that awaits any of the given awaitables.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#anyOf(CompletableFuture[])}.
   */
  public static AnyAwaitable any(Awaitable<?> first, Awaitable<?> second, Awaitable<?>... others) {
    List<Awaitable<?>> awaitables = new ArrayList<>(2 + others.length);
    awaitables.add(first);
    awaitables.add(second);
    awaitables.addAll(Arrays.asList(others));

    return new AnyAwaitable(
        first.syscalls,
        first.syscalls.createAnyDeferred(
            awaitables.stream().map(Awaitable::deferred).collect(Collectors.toList())),
        awaitables);
  }

  /**
   * Create an {@link Awaitable} that awaits all the given awaitables.
   *
   * <p>The behavior is the same as {@link
   * java.util.concurrent.CompletableFuture#allOf(CompletableFuture[])}.
   */
  public static Awaitable<Void> all(
      Awaitable<?> first, Awaitable<?> second, Awaitable<?>... others) {
    List<Deferred<?>> deferred = new ArrayList<>(2 + others.length);
    deferred.add(first.deferred());
    deferred.add(second.deferred());
    Arrays.stream(others).map(Awaitable::deferred).forEach(deferred::add);

    return single(first.syscalls, first.syscalls.createAllDeferred(deferred));
  }

  static class SingleAwaitable<T> extends Awaitable<T> {

    private final Deferred<T> deferred;
    private Result<T> result;

    SingleAwaitable(Syscalls syscalls, Deferred<T> deferred) {
      super(syscalls);
      this.deferred = deferred;
    }

    @Override
    protected Deferred<?> deferred() {
      return this.deferred;
    }

    @Override
    protected Result<T> awaitResult() {
      if (!this.deferred.isCompleted()) {
        Util.<Void>blockOnSyscall(cb -> syscalls.resolveDeferred(this.deferred, cb));
      }
      if (this.result == null) {
        this.result = this.deferred.toResult();
      }
      return this.result;
    }
  }

  static class MappedAwaitable<T, U> extends Awaitable<U> {

    private final Awaitable<T> inner;
    private final Function<Result<T>, Result<U>> mapper;
    private Result<U> mappedResult;

    MappedAwaitable(Awaitable<T> inner, Function<Result<T>, Result<U>> mapper) {
      super(inner.syscalls);
      this.inner = inner;
      this.mapper = mapper;
    }

    @Override
    protected Deferred<?> deferred() {
      return inner.deferred();
    }

    @Override
    public Result<U> awaitResult() throws TerminalException {
      if (mappedResult == null) {
        this.mappedResult = this.mapper.apply(this.inner.awaitResult());
      }
      return this.mappedResult;
    }
  }
}
