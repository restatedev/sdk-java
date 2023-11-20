package dev.restate.sdk.blocking;

import com.google.protobuf.ByteString;
import dev.restate.sdk.core.Serde;
import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.ReadyResult;
import dev.restate.sdk.core.syscalls.SyscallCallback;
import dev.restate.sdk.core.syscalls.Syscalls;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

class Util {

  private Util() {}

  static <T> T blockOnResolve(Syscalls syscalls, DeferredResult<T> deferredResult) {
    if (!deferredResult.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> syscalls.resolveDeferred(deferredResult, cb));
    }

    return Util.unwrapReadyResult(deferredResult.toReadyResult());
  }

  static <T> T awaitCompletableFuture(CompletableFuture<T> future) {
    try {
      return future.get();
    } catch (InterruptedException | CancellationException e) {
      SuspendedException.sneakyThrow();
      return null; // Previous statement throws an exception
    } catch (ExecutionException e) {
      throw (RuntimeException) e.getCause();
    }
  }

  static <T> T blockOnSyscall(Consumer<SyscallCallback<T>> syscallExecutor) {
    CompletableFuture<T> fut = new CompletableFuture<>();
    syscallExecutor.accept(SyscallCallback.completingFuture(fut));
    return Util.awaitCompletableFuture(fut);
  }

  static <T> T unwrapReadyResult(ReadyResult<T> res) {
    if (res.isSuccess()) {
      return res.getResult();
    }
    throw res.getFailure();
  }

  static <T> Optional<T> unwrapOptionalReadyResult(ReadyResult<T> res) {
    if (!res.isSuccess()) {
      throw res.getFailure();
    }
    if (res.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(res.getResult());
  }

  static <T> ByteString serializeWrappingException(Syscalls syscalls, Serde<T> serde, T value) {
    try {
      return serde.serializeToByteString(value);
    } catch (Exception e) {
      syscalls.fail(e);
      SuspendedException.sneakyThrow();
      return null;
    }
  }

  static <T> T deserializeWrappingException(
      Syscalls syscalls, Serde<T> serde, ByteString byteString) {
    try {
      return serde.deserialize(byteString);
    } catch (Exception e) {
      syscalls.fail(e);
      SuspendedException.sneakyThrow();
      return null;
    }
  }
}
