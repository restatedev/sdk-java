package dev.restate.sdk.blocking;

import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.Syscalls;
import java.util.List;

public final class AnyAwaitable extends Awaitable<Object> {

  AnyAwaitable(
      Syscalls syscalls, DeferredResult<Integer> deferredResult, List<Awaitable<?>> mappers) {
    super(syscalls, deferredResult, i -> mappers.get(i).await());
  }

  /** Same as {@link #await()}, but returns the index. */
  public int awaitIndex() {
    // This cast is safe b/c of the constructor
    return (int) Util.blockOnResolve(this.syscalls, this.resultHolder.getDeferredResult());
  }
}
