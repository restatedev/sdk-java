package dev.restate.sdk.core.syscalls;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.TypeTag;
import io.grpc.Context;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Internal interface to access Restate functionalities. Users can use the ad-hoc RestateContext
 * interfaces provided by the various implementations.
 *
 * <p>When using executor switching wrappers, the method's {@code callback} will be executed in the
 * state machine executor.
 */
public interface Syscalls {

  Context.Key<Syscalls> SYSCALLS_KEY = Context.key("restate.dev/syscalls");

  /** Retrieves the current context. */
  static Syscalls current() {
    return Objects.requireNonNull(
        SYSCALLS_KEY.get(),
        "Syscalls MUST be non-null. "
            + "Make sure you're creating the RestateContext within the same thread/executor where the method handler is executed. "
            + "Current thread: "
            + Thread.currentThread().getName());
  }

  // ----- IO
  // Note: These are not supposed to be exposed to RestateContext, but they should be used through
  // gRPC APIs.

  <T extends MessageLite> void pollInput(
      Function<ByteString, T> mapper, SyscallCallback<DeferredResult<T>> callback);

  <T extends MessageLite> void writeOutput(T value, SyscallCallback<Void> callback);

  void writeOutput(Throwable throwable, SyscallCallback<Void> callback);

  // ----- State

  <T> void get(String name, TypeTag<T> ty, SyscallCallback<DeferredResult<T>> callback);

  void clear(String name, SyscallCallback<Void> callback);

  <T> void set(String name, TypeTag<T> ty, @Nonnull T value, SyscallCallback<Void> callback);

  // ----- Syscalls

  void sleep(Duration duration, SyscallCallback<DeferredResult<Void>> callback);

  <T, R> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallCallback<DeferredResult<R>> callback);

  <T> void backgroundCall(
      MethodDescriptor<T, ?> methodDescriptor,
      T parameter,
      @Nullable Duration delay,
      SyscallCallback<Void> requestCallback);

  <T> void enterSideEffectBlock(TypeTag<T> typeTag, EnterSideEffectSyscallCallback<T> callback);

  <T> void exitSideEffectBlock(
      TypeTag<T> typeTag, T toWrite, ExitSideEffectSyscallCallback<T> callback);

  void exitSideEffectBlockWithException(
      Throwable toWrite, ExitSideEffectSyscallCallback<?> callback);

  <T> void awakeable(
      TypeTag<T> typeTag, SyscallCallback<Map.Entry<String, DeferredResult<T>>> callback);

  <T> void resolveAwakeable(
      String id, TypeTag<T> ty, @Nonnull T payload, SyscallCallback<Void> requestCallback);

  void rejectAwakeable(String id, String reason, SyscallCallback<Void> requestCallback);

  // ----- Deferred

  <T> void resolveDeferred(DeferredResult<T> deferredToResolve, SyscallCallback<Void> callback);

  AnyDeferredResult createAnyDeferred(List<DeferredResult<?>> children);

  DeferredResult<Void> createAllDeferred(List<DeferredResult<?>> children);
}
