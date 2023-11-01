package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.impl.DeferredResults.DeferredResultInternal;
import dev.restate.sdk.core.syscalls.AnyDeferredResult;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.SyscallCallback;
import dev.restate.sdk.core.syscalls.Syscalls;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

abstract class SyscallsInternal implements Syscalls {

  // There's no need to make this data structure thread safe as both registerCompensation and
  // cleanCompensations are executed on the user thread.
  private List<Consumer<SyscallCallback<Void>>> compensations = new ArrayList<>();

  // -- Deferred

  @Override
  public AnyDeferredResult createAnyDeferred(List<DeferredResult<?>> children) {
    return DeferredResults.any(
        children.stream().map(dr -> (DeferredResultInternal<?>) dr).collect(Collectors.toList()));
  }

  @Override
  public DeferredResult<Void> createAllDeferred(List<DeferredResult<?>> children) {
    return DeferredResults.all(
        children.stream().map(dr -> (DeferredResultInternal<?>) dr).collect(Collectors.toList()));
  }

  // -- Compensations

  @Override
  public void registerCompensation(Consumer<SyscallCallback<Void>> compensation) {
    this.compensations.add(compensation);
  }

  Iterator<Consumer<SyscallCallback<Void>>> drainCompensations() {
    this.startCompensating();

    Iterator<Consumer<SyscallCallback<Void>>> compensationsIterator = this.compensations.iterator();

    // Replace it with emptyList, this will generate UnsupportedOperationException if
    // the user tries to register a compensation within a compensation
    this.compensations = Collections.emptyList();

    return compensationsIterator;
  }

  // -- Lifecycle methods

  // Notify the state machine we're starting the compensations
  abstract void startCompensating();

  abstract void close();

  abstract void fail(Throwable cause);
}
