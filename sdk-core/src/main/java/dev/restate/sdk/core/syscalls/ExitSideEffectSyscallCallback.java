package dev.restate.sdk.core.syscalls;

import com.google.protobuf.ByteString;
import dev.restate.sdk.core.TerminalException;

public interface ExitSideEffectSyscallCallback extends SyscallCallback<ByteString> {

  /** This is user failure. */
  void onFailure(TerminalException t);
}
