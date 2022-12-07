package dev.restate.sdk.blocking;

import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.ReadyResult;
import dev.restate.sdk.core.syscalls.Syscalls;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.*;

/**
 * An {@code Awaitable} allows to await an asynchronous result. Once {@code await()} is called, the
 * execution stops until the asynchronous result is available.
 *
 * <p>The result can be either a success or a failure. In case of a failure, {@code await()} will
 * throw a {@link StatusRuntimeException}.
 *
 * @param <T> type of the awaitable result
 */
public class Awaitable<T> {

  private final Syscalls syscalls;
  private DeferredResult<T> deferredResult;

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
  @SuppressWarnings("unchecked")
  public T await() throws StatusRuntimeException {
    if (!(this.deferredResult instanceof ReadyResult)) {
      CompletableFuture<ReadyResult<T>> fut = new CompletableFuture<>();
      this.syscalls.resolveDeferred(this.deferredResult, fut::complete, fut::completeExceptionally);
      this.deferredResult = Util.awaitCompletableFuture(fut);
    }

    return Util.unwrapReadyResult((ReadyResult<T>) this.deferredResult);
  }
}
