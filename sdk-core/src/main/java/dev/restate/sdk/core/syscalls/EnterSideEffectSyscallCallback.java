package dev.restate.sdk.core.syscalls;

public interface EnterSideEffectSyscallCallback extends ExitSideEffectSyscallCallback {

  void onNotExecuted();
}
