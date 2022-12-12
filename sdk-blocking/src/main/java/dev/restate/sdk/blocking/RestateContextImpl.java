package dev.restate.sdk.blocking;

import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.ReadyResult;
import dev.restate.sdk.core.syscalls.Syscalls;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
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
    syscalls.set(
        key.name(), key.typeTag(), value, () -> fut.complete(null), fut::completeExceptionally);
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
    CompletableFuture<Optional<ReadyResult<T>>> enterFut = new CompletableFuture<>();
    syscalls.enterSideEffectBlock(
        typeTag,
        () -> enterFut.complete(Optional.empty()),
        storedResult -> enterFut.complete(Optional.of(storedResult)),
        enterFut::completeExceptionally);

    Optional<ReadyResult<T>> readyResult = Util.awaitCompletableFuture(enterFut);
    if (readyResult.isPresent()) {
      // We already have a result, we don't need to execute the action
      return Util.unwrapReadyResult(readyResult.get());
    }

    T res = null;
    Throwable failure = null;
    try {
      res = action.get();
    } catch (Throwable e) {
      failure = e;
    }

    CompletableFuture<ReadyResult<T>> exitFut = new CompletableFuture<>();

    if (failure != null) {
      syscalls.exitSideEffectBlockWithException(
          failure, exitFut::completeExceptionally, exitFut::completeExceptionally);
    } else {
      syscalls.exitSideEffectBlock(typeTag, res, exitFut::complete, exitFut::completeExceptionally);
    }

    return Util.unwrapReadyResult(Util.awaitCompletableFuture(exitFut));
  }

  @Override
  public <T> Awaitable<T> callback(TypeTag<T> typeTag, Consumer<CallbackIdentifier> action)
      throws StatusRuntimeException {
    // Retrieve the awakeable
    CompletableFuture<Map.Entry<CallbackIdentifier, DeferredResult<T>>> enterFut =
        new CompletableFuture<>();
    syscalls.callback(
        typeTag,
        (cid, deferred) -> enterFut.complete(new SimpleImmutableEntry<>(cid, deferred)),
        enterFut::completeExceptionally);
    Map.Entry<CallbackIdentifier, DeferredResult<T>> awakeable =
        Util.awaitCompletableFuture(enterFut);

    // Run the side effect
    this.sideEffect(() -> action.accept(awakeable.getKey()));

    return new Awaitable<>(syscalls, awakeable.getValue());
  }

  @Override
  public <T> void completeCallback(CallbackIdentifier id, TypeTag<T> typeTag, T payload) {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    syscalls.completeCallback(
        id, typeTag, payload, () -> fut.complete(null), fut::completeExceptionally);
    Util.awaitCompletableFuture(fut);
  }
}
