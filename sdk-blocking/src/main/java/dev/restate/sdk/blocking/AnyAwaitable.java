package dev.restate.sdk.blocking;

import dev.restate.sdk.core.syscalls.AnyDeferredResult;
import dev.restate.sdk.core.syscalls.Syscalls;

public final class AnyAwaitable extends Awaitable<Object> {

  AnyAwaitable(Syscalls syscalls, AnyDeferredResult deferredResult) {
    super(syscalls, deferredResult);
  }

  /** Same as {@link #await()}, but returns the index. */
  public int awaitIndex() {
    if (!this.deferredResult.isCompleted()) {
      Util.<Void>blockOnSyscall(cb -> this.syscalls.resolveDeferred(this.deferredResult, cb));
    }

    return ((AnyDeferredResult) this.deferredResult).completedIndex();
  }
}
