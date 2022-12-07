package dev.restate.sdk.blocking;

import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.Syscalls;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RestateContextImpl implements RestateContext {

  private final Syscalls syscalls;

  RestateContextImpl(Syscalls syscalls) {
    this.syscalls = syscalls;
  }

  @Override
  public <T> Optional<T> get(StateKey<T> key) {
    CompletableFuture<DeferredResult<T>> fut = new CompletableFuture<>();

    syscalls.get(key.name(), key.typeTag(), fut::complete, fut::completeExceptionally);

    DeferredResult<T> deferredResult = Util.awaitCompletableFuture(fut);

    return Optional.ofNullable(new Awaitable<>(syscalls, deferredResult).await());
  }

  @Override
  public void clear(StateKey<?> key) {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    syscalls.clear(key.name(), () -> fut.complete(null), fut::completeExceptionally);
    Util.awaitCompletableFuture(fut);
  }

  @Override
  public <T> void set(StateKey<T> key, T value) {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    syscalls.set(key.name(), value, () -> fut.complete(null), fut::completeExceptionally);
    Util.awaitCompletableFuture(fut);
  }

  @Override
  public Awaitable<Void> timer(Duration duration) {
    CompletableFuture<DeferredResult<Void>> fut = new CompletableFuture<>();

    syscalls.sleep(duration, fut::complete, fut::completeExceptionally);

    DeferredResult<Void> result = Util.awaitCompletableFuture(fut);

    return new Awaitable<>(syscalls, result);
  }

  @Override
  public <T extends MessageLite, R extends MessageLite> Awaitable<R> call(
      MethodDescriptor<T, R> methodDescriptor, T parameter) {
    CompletableFuture<DeferredResult<R>> fut = new CompletableFuture<>();

    syscalls.call(methodDescriptor, parameter, fut::complete, fut::completeExceptionally);

    DeferredResult<R> result = Util.awaitCompletableFuture(fut);

    return new Awaitable<>(syscalls, result);
  }

  @Override
  public <T extends MessageLite> void backgroundCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor, T parameter) {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    syscalls.backgroundCall(
        methodDescriptor, parameter, () -> fut.complete(null), fut::completeExceptionally);
    Util.awaitCompletableFuture(fut);
  }

  @Override
  public <T> T sideEffect(TypeTag<T> typeTag, Supplier<T> action) {
    CompletableFuture<T> fut = new CompletableFuture<>();

    syscalls.sideEffect(
        typeTag,
        (resultCallback, errorCallback) -> {
          T res;
          try {
            res = action.get();
          } catch (Throwable e) {
            errorCallback.accept(e);
            return;
          }
          resultCallback.accept(res);
        },
        fut::complete,
        fut::completeExceptionally,
        fut::completeExceptionally);
    return Util.awaitCompletableFuture(fut);
  }

  @Override
  public <T> Awaitable<T> callback(TypeTag<T> typeTag, Consumer<CallbackIdentifier> action)
      throws StatusRuntimeException {
    CompletableFuture<DeferredResult<T>> fut = new CompletableFuture<>();

    syscalls.callback(
        typeTag,
        (identifier, okCallback, errorCallback) -> {
          try {
            action.accept(identifier);
          } catch (Throwable e) {
            errorCallback.accept(e);
            return;
          }
          okCallback.run();
        },
        fut::complete,
        fut::completeExceptionally);

    DeferredResult<T> result = Util.awaitCompletableFuture(fut);

    return new Awaitable<>(syscalls, result);
  }

  @Override
  public void completeCallback(CallbackIdentifier id, Object payload) {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    syscalls.completeCallback(id, payload, () -> fut.complete(null), fut::completeExceptionally);
    Util.awaitCompletableFuture(fut);
  }
}
