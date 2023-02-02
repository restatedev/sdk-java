package dev.restate.sdk.blocking;

import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.Syscalls;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An {@code Awaitable} allows to await an asynchronous result. Once {@code await()} is called, the
 * execution stops until the asynchronous result is available.
 *
 * <p>The result can be either a success or a failure. In case of a failure, {@code await()} will
 * throw a {@link StatusRuntimeException}.
 *
 * @param <T> type of the awaitable result
 */
@NotThreadSafe
public class Awaitable<T> {

  private final Syscalls syscalls;
  private final DeferredResult<T> deferredResult;

  Awaitable(Syscalls syscalls, DeferredResult<T> deferredResult) {
    this.syscalls = syscalls;
    this.deferredResult = deferredResult;
  }

  /**
   * Wait for the current awaitable to complete. Executing this method may trigger the suspension of
   * the function.
   *
   * <p><b>NOTE</b>: You should never wrap this invocation in a try-catch catching {@link
   * RuntimeException}, as it will catch {@link SuspendedException} as well.
   *
   * @throws StatusRuntimeException if the awaitable is ready and contains a failure
   */
  public T await() throws StatusRuntimeException {
    if (!this.deferredResult.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> syscalls.resolveDeferred(this.deferredResult, cb));
    }

    return Util.unwrapReadyResult(this.deferredResult.toReadyResult());
  }

  public static Awaitable<Object> any(
      Awaitable<?> first, Awaitable<?> second, Awaitable<?>... others) {
    List<DeferredResult<?>> deferred = new ArrayList<>(2 + others.length);
    deferred.add(first.deferredResult);
    deferred.add(second.deferredResult);
    Arrays.stream(others).map(a -> a.deferredResult).forEach(deferred::add);

    return new Awaitable<>(first.syscalls, first.syscalls.createAnyDeferred(deferred));
  }

  public static Awaitable<Void> all(
      Awaitable<?> first, Awaitable<?> second, Awaitable<?>... others) {
    List<DeferredResult<?>> deferred = new ArrayList<>(2 + others.length);
    deferred.add(first.deferredResult);
    deferred.add(second.deferredResult);
    Arrays.stream(others).map(a -> a.deferredResult).forEach(deferred::add);

    return new Awaitable<>(first.syscalls, first.syscalls.createAllDeferred(deferred));
  }
}
