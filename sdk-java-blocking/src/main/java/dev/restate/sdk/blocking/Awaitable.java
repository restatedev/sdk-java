package dev.restate.sdk.blocking;

import dev.restate.sdk.core.AbortedExecutionException;
import dev.restate.sdk.core.TerminalException;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.ReadyResultHolder;
import dev.restate.sdk.core.syscalls.Syscalls;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
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
public class Awaitable<T> {

  final Syscalls syscalls;
  final ReadyResultHolder<T> resultHolder;

  Awaitable(Syscalls syscalls, DeferredResult<T> deferredResult) {
    this.syscalls = syscalls;
    this.resultHolder = new ReadyResultHolder<>(deferredResult);
  }

  <U> Awaitable(Syscalls syscalls, DeferredResult<U> deferredResult, Function<U, T> resultMapper) {
    this.syscalls = syscalls;
    this.resultHolder = new ReadyResultHolder<>(deferredResult, resultMapper);
  }

  /**
   * Wait for the current awaitable to complete. Executing this method may trigger the suspension of
   * the function.
   *
   * <p><b>NOTE</b>: You should never wrap this invocation in a try-catch catching {@link
   * RuntimeException}, as it will catch {@link AbortedExecutionException} as well.
   *
   * @throws TerminalException if the awaitable is ready and contains a failure
   */
  public T await() throws TerminalException {
    if (!this.resultHolder.isCompleted()) {
      Util.<Void>blockOnSyscall(
          cb -> syscalls.resolveDeferred(this.resultHolder.getDeferredResult(), cb));
    }
    return Util.unwrapReadyResult(this.resultHolder.getReadyResult());
  }

  /**
   * Same as {@link #await()}, but throws a {@link TimeoutException} if this {@link Awaitable}
   * doesn't complete before the provided {@code timeout}.
   */
  public T await(Duration timeout) throws TerminalException, TimeoutException {
    DeferredResult<Void> sleep = Util.blockOnSyscall(cb -> this.syscalls.sleep(timeout, cb));

    int index =
        Util.blockOnResolve(
            this.syscalls,
            this.syscalls.createAnyDeferred(List.of(this.resultHolder.getDeferredResult(), sleep)));

    if (index == 1) {
      throw new TimeoutException();
    }
    // This await is no-op now
    return this.await();
  }

  public static AnyAwaitable any(Awaitable<?> first, Awaitable<?> second, Awaitable<?>... others) {
    List<Awaitable<?>> awaitables = new ArrayList<>(2 + others.length);
    awaitables.add(first);
    awaitables.add(second);
    awaitables.addAll(Arrays.asList(others));

    return new AnyAwaitable(
        first.syscalls,
        first.syscalls.createAnyDeferred(
            awaitables.stream()
                .map(a -> a.resultHolder.getDeferredResult())
                .collect(Collectors.toList())),
        awaitables);
  }

  public static Awaitable<Void> all(
      Awaitable<?> first, Awaitable<?> second, Awaitable<?>... others) {
    List<DeferredResult<?>> deferred = new ArrayList<>(2 + others.length);
    deferred.add(first.resultHolder.getDeferredResult());
    deferred.add(second.resultHolder.getDeferredResult());
    Arrays.stream(others).map(a -> a.resultHolder.getDeferredResult()).forEach(deferred::add);

    return new Awaitable<>(first.syscalls, first.syscalls.createAllDeferred(deferred));
  }
}
