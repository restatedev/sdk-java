package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.ReadyResult;
import dev.restate.sdk.core.syscalls.Syscalls;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

public class SyscallsMock implements Syscalls {

  @Override
  public <T extends MessageLite> void pollInput(
      Function<ByteString, T> mapper,
      SyscallDeferredResultCallback<T> deferredResultCallback,
      Consumer<Throwable> failureCallback) {}

  @Override
  public <T extends MessageLite> void writeOutput(
      T value, Runnable okCallback, Consumer<Throwable> failureCallback) {}

  @Override
  public void writeOutput(
      Throwable throwable, Runnable okCallback, Consumer<Throwable> failureCallback) {}

  @Override
  public <T> void get(
      String name,
      TypeTag<T> ty,
      SyscallDeferredResultCallback<T> deferredResultCallback,
      Consumer<Throwable> failureCallback) {}

  @Override
  public void clear(String name, Runnable okCallback, Consumer<Throwable> failureCallback) {}

  @Override
  public <T> void set(
      String name, T value, Runnable okCallback, Consumer<Throwable> failureCallback) {}

  @Override
  public void sleep(
      Duration duration,
      SyscallDeferredResultCallback<Void> deferredResultCallback,
      Consumer<Throwable> failureCallback) {}

  @Override
  public <T extends MessageLite, R extends MessageLite> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallDeferredResultCallback<R> deferredResultCallback,
      Consumer<Throwable> failureCallback) {}

  @Override
  public <T extends MessageLite> void backgroundCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor,
      T parameter,
      Runnable okCallback,
      Consumer<Throwable> failureCallback) {}

  @Override
  public <T> void sideEffect(
      TypeTag<T> typeTag,
      SideEffectClosure<T> closure,
      Consumer<T> successResultCallback,
      Consumer<StatusRuntimeException> errorResultCallback,
      Consumer<Throwable> failureCallback) {}

  @Override
  public <T> void callback(
      TypeTag<T> typeTag,
      CallbackClosure closure,
      SyscallDeferredResultCallback<T> deferredResultCallback,
      Consumer<Throwable> failureCallback) {}

  @Override
  public void completeCallback(
      CallbackIdentifier id,
      Object payload,
      Runnable okCallback,
      Consumer<Throwable> failureCallback) {}

  @Override
  public <T> void resolveDeferred(
      DeferredResult<T> deferredToResolve,
      Consumer<ReadyResult<T>> resultCallback,
      Consumer<Throwable> failureCallback) {}
}
