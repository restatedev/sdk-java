package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.*;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.function.Function;

public class SyscallsMock implements Syscalls {

  @Override
  public <T extends MessageLite> void pollInput(
      Function<ByteString, T> mapper,
      SyscallCallback<Void> requestCallback,
      DeferredResultCallback<T> deferredResultCallback) {}

  @Override
  public <T extends MessageLite> void writeOutput(T value, SyscallCallback<Void> callback) {}

  @Override
  public void writeOutput(Throwable throwable, SyscallCallback<Void> callback) {}

  @Override
  public <T> void get(
      String name,
      TypeTag<T> ty,
      SyscallCallback<Void> requestCallback,
      DeferredResultCallback<T> deferredResultCallback) {}

  @Override
  public void clear(String name, SyscallCallback<Void> callback) {}

  @Override
  public <T> void set(String name, TypeTag<T> ty, T value, SyscallCallback<Void> callback) {}

  @Override
  public void sleep(
      Duration duration,
      SyscallCallback<Void> requestCallback,
      DeferredResultCallback<Void> deferredResultCallback) {}

  @Override
  public <T extends MessageLite, R extends MessageLite> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallCallback<Void> requestCallback,
      DeferredResultCallback<R> deferredResultCallback) {}

  @Override
  public <T extends MessageLite> void backgroundCall(
      MethodDescriptor<T, ? extends MessageLite> methodDescriptor,
      T parameter,
      SyscallCallback<Void> requestCallback) {}

  @Override
  public <T> void enterSideEffectBlock(
      TypeTag<T> typeTag, EnterSideEffectSyscallCallback<T> callback) {}

  @Override
  public <T> void exitSideEffectBlock(
      TypeTag<T> typeTag, T toWrite, ExitSideEffectSyscallCallback<T> callback) {}

  @Override
  public void exitSideEffectBlockWithException(
      Throwable toWrite, ExitSideEffectSyscallCallback<?> callback) {}

  @Override
  public <T> void callback(
      TypeTag<T> typeTag,
      SyscallCallback<CallbackIdentifier> requestCallback,
      DeferredResultCallback<T> deferredResultCallback) {}

  @Override
  public <T> void completeCallback(
      CallbackIdentifier id, TypeTag<T> ty, T payload, SyscallCallback<Void> requestCallback) {}
}
