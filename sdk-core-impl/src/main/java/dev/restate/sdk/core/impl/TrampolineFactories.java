package dev.restate.sdk.core.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.core.CallbackIdentifier;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.ReadyResult;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

public class TrampolineFactories {

  private TrampolineFactories() {}

  public static Function<ServerCall.Listener<MessageLite>, ServerCall.Listener<MessageLite>>
      serverCallListener(Executor userExecutor) {
    return sc -> new TrampoliningServerCallListener(sc, userExecutor);
  }

  public static Function<SyscallsInternal, SyscallsInternal> syscalls(Executor syscallsExecutor) {
    return sc -> new TrampoliningSyscalls(sc, syscallsExecutor);
  }

  private static class TrampoliningServerCallListener extends ServerCall.Listener<MessageLite> {

    private final ServerCall.Listener<MessageLite> listener;
    private final Executor userExecutor;

    private TrampoliningServerCallListener(
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

  private static class TrampoliningSyscalls implements SyscallsInternal {

    private final SyscallsInternal syscalls;
    private final Executor syscallsExecutor;

    private TrampoliningSyscalls(SyscallsInternal syscalls, Executor syscallsExecutor) {
      this.syscalls = syscalls;
      this.syscallsExecutor = syscallsExecutor;
    }

    @Override
    public InvocationStateMachine getStateMachine() {
      return syscalls.getStateMachine();
    }

    @Override
    public <T extends MessageLite> void pollInput(
        Function<ByteString, T> mapper,
        SyscallDeferredResultCallback<T> deferredCallback,
        Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(() -> syscalls.pollInput(mapper, deferredCallback, failureCallback));
    }

    @Override
    public <T extends MessageLite> void writeOutput(
        T value, Runnable okCallback, Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(() -> syscalls.writeOutput(value, okCallback, failureCallback));
    }

    @Override
    public void writeOutput(
        Throwable throwable, Runnable okCallback, Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(() -> syscalls.writeOutput(throwable, okCallback, failureCallback));
    }

    @Override
    public <T> void get(
        String name,
        TypeTag<T> ty,
        SyscallDeferredResultCallback<T> deferredCallback,
        Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(() -> syscalls.get(name, ty, deferredCallback, failureCallback));
    }

    @Override
    public void clear(String name, Runnable okCallback, Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(() -> syscalls.clear(name, okCallback, failureCallback));
    }

    @Override
    public <T> void set(
        String name, T value, Runnable okCallback, Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(() -> syscalls.set(name, value, okCallback, failureCallback));
    }

    @Override
    public void sleep(
        Duration duration,
        SyscallDeferredResultCallback<Void> deferredCallback,
        Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(() -> syscalls.sleep(duration, deferredCallback, failureCallback));
    }

    @Override
    public <T extends MessageLite, R extends MessageLite> void call(
        MethodDescriptor<T, R> methodDescriptor,
        T parameter,
        SyscallDeferredResultCallback<R> deferredCallback,
        Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(
          () -> syscalls.call(methodDescriptor, parameter, deferredCallback, failureCallback));
    }

    @Override
    public <T extends MessageLite> void backgroundCall(
        MethodDescriptor<T, ? extends MessageLite> methodDescriptor,
        T parameter,
        Runnable okCallback,
        Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(
          () -> syscalls.backgroundCall(methodDescriptor, parameter, okCallback, failureCallback));
    }

    @Override
    public <T> void sideEffect(
        TypeTag<T> typeTag,
        SideEffectClosure<T> closure,
        Consumer<T> successResultCallback,
        Consumer<StatusRuntimeException> errorResultCallback,
        Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(
          () ->
              syscalls.sideEffect(
                  typeTag, closure, successResultCallback, errorResultCallback, failureCallback));
    }

    @Override
    public <T> void callback(
        TypeTag<T> typeTag,
        CallbackClosure closure,
        SyscallDeferredResultCallback<T> deferredCallback,
        Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(
          () -> syscalls.callback(typeTag, closure, deferredCallback, failureCallback));
    }

    @Override
    public void completeCallback(
        CallbackIdentifier id,
        Object payload,
        Runnable okCallback,
        Consumer<Throwable> failureCallback) {
      syscallsExecutor.execute(
          () -> syscalls.completeCallback(id, payload, okCallback, failureCallback));
    }

    @Override
    public <T> void resolveDeferred(
        DeferredResult<T> deferredToResolve,
        Consumer<ReadyResult<T>> resultCallback,
        Consumer<Throwable> failureCallback) {
      // No need to switch executor for this
      if (deferredToResolve instanceof ReadyResult) {
        resultCallback.accept((ReadyResult<T>) deferredToResolve);
        return;
      }

      syscallsExecutor.execute(
          () -> syscalls.resolveDeferred(deferredToResolve, resultCallback, failureCallback));
    }

    @Override
    public void close() {
      syscallsExecutor.execute(syscalls::close);
    }

    @Override
    public void fail(ProtocolException cause) {
      syscallsExecutor.execute(() -> syscalls.fail(cause));
    }
  }
}
