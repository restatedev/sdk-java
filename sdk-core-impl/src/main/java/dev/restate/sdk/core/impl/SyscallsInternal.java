package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.impl.DeferredResults.DeferredResultInternal;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.Syscalls;
import java.util.List;
import java.util.stream.Collectors;

public interface SyscallsInternal extends Syscalls {

  @Override
  default DeferredResult<Integer> createAnyDeferred(List<DeferredResult<?>> children) {
    return DeferredResults.any(
        children.stream().map(dr -> (DeferredResultInternal<?>) dr).collect(Collectors.toList()));
  }

  @Override
  default DeferredResult<Void> createAllDeferred(List<DeferredResult<?>> children) {
    return DeferredResults.all(
        children.stream().map(dr -> (DeferredResultInternal<?>) dr).collect(Collectors.toList()));
  }

  // -- Lifecycle methods

  void close();

  void fail(Throwable cause);
}
