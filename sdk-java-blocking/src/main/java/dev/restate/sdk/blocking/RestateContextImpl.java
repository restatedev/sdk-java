package dev.restate.sdk.blocking;

import com.google.protobuf.ByteString;
import dev.restate.sdk.core.Serde;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.function.ThrowingSupplier;
import dev.restate.sdk.core.syscalls.*;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class RestateContextImpl implements RestateContext {

  private final Syscalls syscalls;

  RestateContextImpl(Syscalls syscalls) {
    this.syscalls = syscalls;
  }

  @Override
  public <T> Optional<T> get(StateKey<T> key) {
    DeferredResult<ByteString> deferredResult =
        Util.blockOnSyscall(cb -> syscalls.get(key.name(), cb));

    if (!deferredResult.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> syscalls.resolveDeferred(deferredResult, cb));
    }

    return Util.unwrapOptionalReadyResult(deferredResult.toReadyResult())
        .map(bs -> key.serde().deserialize(bs));
  }

  @Override
  public void clear(StateKey<?> key) {
    Util.<Void>blockOnSyscall(cb -> syscalls.clear(key.name(), cb));
  }

  @Override
  public <T> void set(StateKey<T> key, @Nonnull T value) {
    Util.<Void>blockOnSyscall(
        cb -> syscalls.set(key.name(), key.serde().serializeToByteString(value), cb));
  }

  @Override
  public Awaitable<Void> timer(Duration duration) {
    DeferredResult<Void> result = Util.blockOnSyscall(cb -> syscalls.sleep(duration, cb));
    return new Awaitable<>(syscalls, result);
  }

  @Override
  public <T, R> Awaitable<R> call(MethodDescriptor<T, R> methodDescriptor, T parameter) {
    DeferredResult<R> result =
        Util.blockOnSyscall(cb -> syscalls.call(methodDescriptor, parameter, cb));
    return new Awaitable<>(syscalls, result);
  }

  @Override
  public <T> void oneWayCall(MethodDescriptor<T, ?> methodDescriptor, T parameter) {
    Util.<Void>blockOnSyscall(cb -> syscalls.backgroundCall(methodDescriptor, parameter, null, cb));
  }

  @Override
  public <T> void delayedCall(
      MethodDescriptor<T, ?> methodDescriptor, T parameter, Duration delay) {
    Util.<Void>blockOnSyscall(
        cb -> syscalls.backgroundCall(methodDescriptor, parameter, delay, cb));
  }

  @Override
  public <T> T sideEffect(Serde<T> serde, ThrowingSupplier<T> action) {
    CompletableFuture<CompletableFuture<ByteString>> enterFut = new CompletableFuture<>();
    syscalls.enterSideEffectBlock(
        new EnterSideEffectSyscallCallback() {
          @Override
          public void onNotExecuted() {
            enterFut.complete(new CompletableFuture<>());
          }

          @Override
          public void onResult(ByteString result) {
            enterFut.complete(CompletableFuture.completedFuture(result));
          }

          @Override
          public void onFailure(StatusRuntimeException t) {
            enterFut.complete(CompletableFuture.failedFuture(t));
          }

          @Override
          public void onCancel(Throwable t) {
            enterFut.cancel(true);
          }
        });

    // If a failure was stored, it's simply thrown here
    CompletableFuture<ByteString> exitFut = Util.awaitCompletableFuture(enterFut);
    if (exitFut.isDone()) {
      // We already have a result, we don't need to execute the action
      return serde.deserialize(Util.awaitCompletableFuture(exitFut));
    }

    ExitSideEffectSyscallCallback exitCallback =
        new ExitSideEffectSyscallCallback() {
          @Override
          public void onResult(ByteString result) {
            exitFut.complete(result);
          }

          @Override
          public void onFailure(StatusRuntimeException t) {
            exitFut.completeExceptionally(t);
          }

          @Override
          public void onCancel(@Nullable Throwable t) {
            exitFut.cancel(true);
          }
        };

    T res = null;
    Throwable failure = null;
    try {
      res = action.get();
    } catch (Throwable e) {
      failure = e;
    }

    if (failure != null) {
      syscalls.exitSideEffectBlockWithException(failure, exitCallback);
    } else {
      syscalls.exitSideEffectBlock(serde.serializeToByteString(res), exitCallback);
    }

    return serde.deserialize(Util.awaitCompletableFuture(exitFut));
  }

  @Override
  public <T> Awakeable<T> awakeable(Serde<T> serde) throws StatusRuntimeException {
    // Retrieve the awakeable
    Map.Entry<String, DeferredResult<ByteString>> awakeable =
        Util.blockOnSyscall(syscalls::awakeable);

    return new Awakeable<>(syscalls, awakeable.getValue(), serde, awakeable.getKey());
  }

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return new AwakeableHandle() {
      @Override
      public <T> void resolve(Serde<T> serde, @Nonnull T payload) {
        Util.<Void>blockOnSyscall(
            cb -> syscalls.resolveAwakeable(id, serde.serializeToByteString(payload), cb));
      }

      @Override
      public void reject(String reason) {
        Util.<Void>blockOnSyscall(cb -> syscalls.rejectAwakeable(id, reason, cb));
      }
    };
  }
}
