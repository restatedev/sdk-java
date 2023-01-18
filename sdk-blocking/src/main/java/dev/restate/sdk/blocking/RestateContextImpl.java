package dev.restate.sdk.blocking;

import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.*;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class RestateContextImpl implements RestateContext {

  private final Syscalls syscalls;

  RestateContextImpl(Syscalls syscalls) {
    this.syscalls = syscalls;
  }

  @Override
  public <T> Optional<T> get(StateKey<T> key) {
    DeferredResult<T> deferredResult =
        Util.blockOnSyscall(cb -> syscalls.get(key.name(), key.typeTag(), cb));

    if (!deferredResult.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> syscalls.resolveDeferred(deferredResult, cb));
    }

    return Util.unwrapOptionalReadyResult(deferredResult.toReadyResult());
  }

  @Override
  public void clear(StateKey<?> key) {
    Util.<Void>blockOnSyscall(cb -> syscalls.clear(key.name(), cb));
  }

  @Override
  public <T> void set(StateKey<T> key, T value) {
    Util.<Void>blockOnSyscall(cb -> syscalls.set(key.name(), key.typeTag(), value, cb));
  }

  @Override
  public Awaitable<Void> timer(Duration duration) {
    DeferredResult<Void> result = Util.blockOnSyscall(cb -> syscalls.sleep(duration, cb));
    return new Awaitable<>(syscalls, result);
  }

  @Override
  public <T extends MessageLite, R extends MessageLite> Awaitable<R> call(
      MethodDescriptor<T, R> methodDescriptor, T parameter) {
    DeferredResult<R> result =
        Util.blockOnSyscall(cb -> syscalls.call(methodDescriptor, parameter, cb));
    return new Awaitable<>(syscalls, result);
  }

  @Override
  public <T extends MessageLite> void backgroundCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor, T parameter) {
    Util.<Void>blockOnSyscall(cb -> syscalls.backgroundCall(methodDescriptor, parameter, cb));
  }

  @Override
  public <T> T sideEffect(TypeTag<T> typeTag, Supplier<T> action) {
    CompletableFuture<CompletableFuture<T>> enterFut = new CompletableFuture<>();
    syscalls.enterSideEffectBlock(
        typeTag,
        new EnterSideEffectSyscallCallback<>() {
          @Override
          public void onNotExecuted() {
            enterFut.complete(new CompletableFuture<>());
          }

          @Override
          public void onResult(T result) {
            enterFut.complete(CompletableFuture.completedFuture(result));
          }

          @Override
          public void onFailure(StatusRuntimeException t) {
            enterFut.complete(CompletableFuture.failedFuture(t));
          }

          @Override
          public void onCancel(Throwable t) {
            // TODO log
            enterFut.cancel(true);
          }
        });

    // If a failure was stored, it's simply thrown here
    CompletableFuture<T> exitFut = Util.awaitCompletableFuture(enterFut);
    if (exitFut.isDone()) {
      // We already have a result, we don't need to execute the action
      return Util.awaitCompletableFuture(exitFut);
    }

    ExitSideEffectSyscallCallback<T> exitCallback =
        new ExitSideEffectSyscallCallback<>() {
          @Override
          public void onResult(T result) {
            exitFut.complete(result);
          }

          @Override
          public void onFailure(StatusRuntimeException t) {
            exitFut.completeExceptionally(t);
          }

          @Override
          public void onCancel(@Nullable Throwable t) {
            // TODO log
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
      syscalls.exitSideEffectBlock(typeTag, res, exitCallback);
    }

    return Util.awaitCompletableFuture(exitFut);
  }

  @Override
  public <T> Awaitable<T> callback(TypeTag<T> typeTag, Consumer<CallbackIdentifier> action)
      throws StatusRuntimeException {
    // Retrieve the awakeable
    Map.Entry<CallbackIdentifier, DeferredResult<T>> cbIdAndResult =
        Util.blockOnSyscall(cb -> syscalls.callback(typeTag, cb));

    // Run the side effect
    this.sideEffect(() -> action.accept(cbIdAndResult.getKey()));

    return new Awaitable<>(syscalls, cbIdAndResult.getValue());
  }

  @Override
  public <T> void completeCallback(CallbackIdentifier id, TypeTag<T> typeTag, T payload) {
    Util.<Void>blockOnSyscall(cb -> syscalls.completeCallback(id, typeTag, payload, cb));
  }
}
