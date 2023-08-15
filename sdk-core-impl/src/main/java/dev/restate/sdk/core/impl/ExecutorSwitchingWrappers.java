package dev.restate.sdk.core.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.*;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.annotation.Nonnull;

class ExecutorSwitchingWrappers {

  private ExecutorSwitchingWrappers() {}

  static RestateServerCallListener<MessageLite> serverCallListener(
      RestateServerCallListener<MessageLite> sc, Executor userExecutor) {
    return new ExecutorSwitchingServerCallListener(sc, userExecutor);
  }

  static SyscallsInternal syscalls(SyscallsInternal sc, Executor syscallsExecutor) {
    return new ExecutorSwitchingSyscalls(sc, syscallsExecutor);
  }

  private static class ExecutorSwitchingServerCallListener
      implements RestateServerCallListener<MessageLite> {

    private final RestateServerCallListener<MessageLite> listener;
    private final Executor userExecutor;

    private ExecutorSwitchingServerCallListener(
        RestateServerCallListener<MessageLite> listener, Executor userExecutor) {
      this.listener = listener;
      this.userExecutor = userExecutor;
    }

    @Override
    public void onMessageAndHalfClose(MessageLite message) {
      userExecutor.execute(() -> listener.onMessageAndHalfClose(message));
    }

    // A bit of explanation why the following methods are not executed on the user executor.
    //
    // The listener methods onReady/onCancel/onComplete are used purely for notification reasons,
    // they don't execute any user code.
    //
    // Running them in the userExecutor can also be problematic if the listener
    // mutates some thread local and runs tasks in parallel.
    // This is the case when using Vertx.executeBlocking with ordered = false and mutating the
    // Vert.x Context, which is shared among every task running in the executeBlocking thread pool
    // as thread local.

    @Override
    public void onCancel() {
      listener.onCancel();
    }

    @Override
    public void onComplete() {
      listener.onComplete();
    }

    @Override
    public void onReady() {
      listener.onReady();
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
        Duration delay,
        SyscallCallback<Void> requestCallback) {
      syscallsExecutor.execute(
          () -> syscalls.backgroundCall(methodDescriptor, parameter, delay, requestCallback));
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
        TypeTag<T> typeTag, SyscallCallback<Map.Entry<String, DeferredResult<T>>> callback) {
      syscallsExecutor.execute(() -> syscalls.awakeable(typeTag, callback));
    }

    @Override
    public <T> void completeAwakeable(
        String id, TypeTag<T> typeTag, @Nonnull T payload, SyscallCallback<Void> requestCallback) {
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
