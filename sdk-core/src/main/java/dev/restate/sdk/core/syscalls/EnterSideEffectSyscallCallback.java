package dev.restate.sdk.core.syscalls;

public interface EnterSideEffectSyscallCallback<T> extends ExitSideEffectSyscallCallback<T> {

  void onNotExecuted();
}
