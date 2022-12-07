package dev.restate.sdk.blocking;

import dev.restate.sdk.core.SuspendedException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

class Util {

  private Util() {}

  static <T> T awaitCompletableFuture(CompletableFuture<T> future) {
    try {
      return future.get();
    } catch (InterruptedException | CancellationException e) {
      throw SuspendedException.INSTANCE;
    } catch (ExecutionException e) {
      throw (RuntimeException) e.getCause();
    }
  }
}
