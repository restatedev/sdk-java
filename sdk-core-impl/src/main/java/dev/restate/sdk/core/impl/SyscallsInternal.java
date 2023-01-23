package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.syscalls.Syscalls;

public interface SyscallsInternal extends Syscalls {

  // -- Lifecycle methods

  void close();

  void fail(ProtocolException cause);
}
