package dev.restate.sdk.core.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.core.AwakeableIdentifier;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.*;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.annotation.Nonnull;

class ExecutorSwitchingWrappers {

  private ExecutorSwitchingWrappers() {}

  static ServerCall.Listener<MessageLite> serverCallListener(
      ServerCall.Listener<MessageLite> sc, Executor userExecutor) {
    return new ExecutorSwitchingServerCallListener(sc, userExecutor);
  }

  static SyscallsInternal syscalls(SyscallsInternal sc, Executor syscallsExecutor) {
    return new ExecutorSwitchingSyscalls(sc, syscallsExecutor);
  }

  private static class ExecutorSwitchingServerCallListener
      extends ServerCall.Listener<MessageLite> {

    private final ServerCall.Listener<MessageLite> listener;
    private final Executor userExecutor;

    private ExecutorSwitchingServerCallListener(
        ServerCall.Listener<MessageLite> listener, Executor userExecutor) {
      this.listener = listener;
      this.userExecutor = userExecutor;
    }

    @Override
    public void onMessage(MessageLite message) {
      userExecutor.execute(() -> listener.onMessage(message));
    }

    @Override
    public void onHalfClose() {
      userExecutor.execute(listener::onHalfClose);
    }

    @Override
    public void onCancel() {
      userExecutor.execute(listener::onCancel);
    }

    @Override
    public void onComplete() {
      userExecutor.execute(listener::onComplete);
    }

    @Override
    public void onReady() {
      userExecutor.execute(listener::onReady);
    }
  }

  private static class ExecutorSwitchingSyscalls implements SyscallsInternal {

    private final SyscallsInternal syscalls;
    private final Executor syscallsExecutor;

    private ExecutorSwitchingSyscalls(SyscallsInternal syscalls, Executor syscallsExecutor) {
      this.syscalls = syscalls;
      this.syscallsExecutor =
          r ->
              syscallsExecutor.execute(
                  () -> {
                    try {
                      r.run();
                    } catch (Throwable e) {
                      syscalls.fail(e);
                      throw e;
                    }
                  });
    }

    @Override
    public <T extends MessageLite> void pollInput(
        Function<ByteString, T> mapper, SyscallCallback<DeferredResult<T>> callback) {
      syscallsExecutor.execute(() -> syscalls.pollInput(mapper, callback));
    }

    @Override
    public <T extends MessageLite> void writeOutput(T value, SyscallCallback<Void> callback) {
      syscallsExecutor.execute(() -> syscalls.writeOutput(value, callback));
    }

    @Override
    public void writeOutput(Throwable throwable, SyscallCallback<Void> callback) {
      syscallsExecutor.execute(() -> syscalls.writeOutput(throwable, callback));
    }

    @Override
    public <T> void get(String name, TypeTag<T> ty, SyscallCallback<DeferredResult<T>> callback) {
      syscallsExecutor.execute(() -> syscalls.get(name, ty, callback));
    }

    @Override
    public void clear(String name, SyscallCallback<Void> callback) {
      syscallsExecutor.execute(() -> syscalls.clear(name, callback));
    }

    @Override
    public <T> void set(
        String name, TypeTag<T> typeTag, @Nonnull T value, SyscallCallback<Void> callback) {
      syscallsExecutor.execute(() -> syscalls.set(name, typeTag, value, callback));
    }

    @Override
    public void sleep(Duration duration, SyscallCallback<DeferredResult<Void>> callback) {
      syscallsExecutor.execute(() -> syscalls.sleep(duration, callback));
    }

    @Override
    public <T extends MessageLite, R extends MessageLite> void call(
        MethodDescriptor<T, R> methodDescriptor,
        T parameter,
        SyscallCallback<DeferredResult<R>> callback) {
      syscallsExecutor.execute(() -> syscalls.call(methodDescriptor, parameter, callback));
    }

    @Override
    public <T extends MessageLite> void backgroundCall(
        MethodDescriptor<T, ? extends MessageLite> methodDescriptor,
        T parameter,
        SyscallCallback<Void> requestCallback) {
      syscallsExecutor.execute(
          () -> syscalls.backgroundCall(methodDescriptor, parameter, requestCallback));
    }

    @Override
    public <T> void enterSideEffectBlock(
        TypeTag<T> typeTag, EnterSideEffectSyscallCallback<T> callback) {
      syscallsExecutor.execute(() -> syscalls.enterSideEffectBlock(typeTag, callback));
    }

    @Override
    public <T> void exitSideEffectBlock(
        TypeTag<T> typeTag, T toWrite, ExitSideEffectSyscallCallback<T> callback) {
      syscallsExecutor.execute(() -> syscalls.exitSideEffectBlock(typeTag, toWrite, callback));
    }

    @Override
    public void exitSideEffectBlockWithException(
        Throwable toWrite, ExitSideEffectSyscallCallback<?> callback) {
      syscallsExecutor.execute(() -> syscalls.exitSideEffectBlockWithException(toWrite, callback));
    }

    @Override
    public <T> void awakeable(
        TypeTag<T> typeTag,
        SyscallCallback<Map.Entry<AwakeableIdentifier, DeferredResult<T>>> callback) {
      syscallsExecutor.execute(() -> syscalls.awakeable(typeTag, callback));
    }

    @Override
    public <T> void completeAwakeable(
        AwakeableIdentifier id,
        TypeTag<T> typeTag,
        @Nonnull T payload,
        SyscallCallback<Void> requestCallback) {
      syscallsExecutor.execute(
          () -> syscalls.completeAwakeable(id, typeTag, payload, requestCallback));
    }

    @Override
    public <T> void resolveDeferred(
        DeferredResult<T> deferredToResolve, SyscallCallback<Void> callback) {
      syscallsExecutor.execute(() -> syscalls.resolveDeferred(deferredToResolve, callback));
    }

    @Override
    public void close() {
      syscallsExecutor.execute(syscalls::close);
    }

    @Override
    public void fail(Throwable cause) {
      syscallsExecutor.execute(() -> syscalls.fail(cause));
    }
  }
}
