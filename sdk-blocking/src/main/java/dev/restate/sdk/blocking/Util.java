package dev.restate.sdk.blocking;

import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.syscalls.ReadyResult;
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

  static <T> T unwrapReadyResult(ReadyResult<T> res) {
    if (res.isOk()) {
      return res.getResult();
    }
    throw (RuntimeException) res.getFailure();
  }
}
