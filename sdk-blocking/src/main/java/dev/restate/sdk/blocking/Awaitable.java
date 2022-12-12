package dev.restate.sdk.blocking;

import dev.restate.sdk.core.SuspendedException;
import io.grpc.StatusRuntimeException;
import java.util.Arrays;
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
public final class Awaitable<T> {

  private final CompletableFuture<T> future;

  public Awaitable(CompletableFuture<T> future) {
    this.future = future;
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
    return Util.awaitCompletableFuture(this.future);
  }

  private CompletableFuture<T> getFuture() {
    return future;
  }

  public static Awaitable<Void> allOf(Awaitable<?>... awaitables) {
    return new Awaitable<>(
        CompletableFuture.allOf(
            Arrays.stream(awaitables).map(Awaitable::getFuture).toArray(CompletableFuture[]::new)));
  }

  public static Awaitable<Object> anyOf(Awaitable<?>... awaitables) {
    return new Awaitable<>(
        CompletableFuture.anyOf(
            Arrays.stream(awaitables).map(Awaitable::getFuture).toArray(CompletableFuture[]::new)));
  }
}
