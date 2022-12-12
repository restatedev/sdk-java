package dev.restate.sdk.blocking;

import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.*;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
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
    CompletableFuture<Optional<T>> resultFut = new CompletableFuture<>();
    CompletableFuture<Void> callFut = new CompletableFuture<>();

    syscalls.get(
        key.name(),
        key.typeTag(),
        SyscallCallback.completingFuture(callFut),
        DeferredResultCallback.completingOptionalFuture(resultFut));

    Util.awaitCompletableFuture(callFut);
    return Util.awaitCompletableFuture(resultFut);
  }

  @Override
  public void clear(StateKey<?> key) {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    syscalls.clear(key.name(), SyscallCallback.completingFuture(fut));
    Util.awaitCompletableFuture(fut);
  }

  @Override
  public <T> void set(StateKey<T> key, T value) {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    syscalls.set(key.name(), key.typeTag(), value, SyscallCallback.completingFuture(fut));
    Util.awaitCompletableFuture(fut);
  }

  @Override
  public Awaitable<Void> timer(Duration duration) {
    CompletableFuture<Void> resultFut = new CompletableFuture<>();
    CompletableFuture<Void> callFut = new CompletableFuture<>();

    syscalls.sleep(
        duration,
        SyscallCallback.completingFuture(callFut),
        DeferredResultCallback.completingFuture(resultFut));

    Util.awaitCompletableFuture(callFut);
    return new Awaitable<>(resultFut);
  }

  @Override
  public <T extends MessageLite, R extends MessageLite> Awaitable<R> call(
      MethodDescriptor<T, R> methodDescriptor, T parameter) {
    CompletableFuture<R> resultFut = new CompletableFuture<>();
    CompletableFuture<Void> callFut = new CompletableFuture<>();

    syscalls.call(
        methodDescriptor,
        parameter,
        SyscallCallback.completingFuture(callFut),
        DeferredResultCallback.completingFuture(resultFut));

    Util.awaitCompletableFuture(callFut);
    return new Awaitable<>(resultFut);
  }

  @Override
  public <T extends MessageLite> void backgroundCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor, T parameter) {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    syscalls.backgroundCall(methodDescriptor, parameter, SyscallCallback.completingFuture(fut));
    Util.awaitCompletableFuture(fut);
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
          public void onFailure(Throwable t) {
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
          public void onFailure(Throwable t) {
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
    CompletableFuture<T> awakeable = new CompletableFuture<>();
    CompletableFuture<CallbackIdentifier> enterFut = new CompletableFuture<>();
    syscalls.callback(
        typeTag,
        SyscallCallback.completingFuture(enterFut),
        DeferredResultCallback.completingFuture(awakeable));

    CallbackIdentifier id = Util.awaitCompletableFuture(enterFut);

    // Run the side effect
    this.sideEffect(() -> action.accept(id));

    return new Awaitable<>(awakeable);
  }

  @Override
  public <T> void completeCallback(CallbackIdentifier id, TypeTag<T> typeTag, T payload) {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    syscalls.completeCallback(id, typeTag, payload, SyscallCallback.completingFuture(fut));
    Util.awaitCompletableFuture(fut);
  }
}
