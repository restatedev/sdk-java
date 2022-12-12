package dev.restate.sdk.core.syscalls;

public interface EnterSideEffectSyscallCallback<T> extends NonEmptyDeferredResultCallback<T> {

  void onNotExecuted();
}
