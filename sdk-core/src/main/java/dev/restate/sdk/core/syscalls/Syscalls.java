package dev.restate.sdk.core.syscalls;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.sdk.core.TypeTag;
import io.grpc.Context;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Internal interface to access Restate functionalities. Users can use the ad-hoc RestateContext
 * interfaces provided by the various implementations.
 */
public interface Syscalls {

  Context.Key<Syscalls> SYSCALLS_KEY = Context.key("restate.dev/syscalls");

  /** Retrieves the current context. */
  static Syscalls current() {
    return SYSCALLS_KEY.get();
  }

  // ----- IO
  // Note: These are not supposed to be exposed to RestateContext, but they should be used through
  // gRPC APIs.

  <T extends MessageLite> void pollInput(
      Function<ByteString, T> mapper,
      SyscallDeferredResultCallback<T> deferredResultCallback,
      Consumer<Throwable> failureCallback);

  <T extends MessageLite> void writeOutput(
      T value, Runnable okCallback, Consumer<Throwable> failureCallback);

  void writeOutput(Throwable throwable, Runnable okCallback, Consumer<Throwable> failureCallback);

  // ----- State

  <T> void get(
      String name,
      TypeTag<T> ty,
      SyscallDeferredResultCallback<T> deferredResultCallback,
      Consumer<Throwable> failureCallback);

  void clear(String name, Runnable okCallback, Consumer<Throwable> failureCallback);

  <T> void set(String name, T value, Runnable okCallback, Consumer<Throwable> failureCallback);

  // ----- Syscalls

  void sleep(
      Duration duration,
      SyscallDeferredResultCallback<Void> deferredResultCallback,
      Consumer<Throwable> failureCallback);

  <T extends MessageLite, R extends MessageLite> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallDeferredResultCallback<R> deferredResultCallback,
      Consumer<Throwable> failureCallback);

  <T extends MessageLite> void backgroundCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor,
      T parameter,
      Runnable okCallback,
      Consumer<Throwable> failureCallback);

  <T> void sideEffect(
      TypeTag<T> typeTag,
      SideEffectClosure<T> closure,
      Consumer<T> successResultCallback,
      Consumer<StatusRuntimeException> errorResultCallback,
      Consumer<Throwable> failureCallback);

  <T> void callback(
      TypeTag<T> typeTag,
      CallbackClosure closure,
      SyscallDeferredResultCallback<T> deferredResultCallback,
      Consumer<Throwable> failureCallback);

  void completeCallback(
      CallbackIdentifier id,
      Object payload,
      Runnable okCallback,
      Consumer<Throwable> failureCallback);

  <T> void resolveDeferred(
      DeferredResult<T> deferredToResolve,
      Consumer<ReadyResult<T>> resultCallback,
      Consumer<Throwable> failureCallback);

  @FunctionalInterface
  interface SideEffectClosure<T> {

    void execute(Consumer<T> resultCallback, Consumer<Throwable> errorCallback);
  }

  @FunctionalInterface
  interface CallbackClosure {

    void execute(
        CallbackIdentifier identifier, Runnable okCallback, Consumer<Throwable> errorCallback);
  }

  @FunctionalInterface
  interface SyscallDeferredResultCallback<T> extends Consumer<DeferredResult<T>> {}
}
