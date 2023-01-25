package dev.restate.sdk.core.syscalls;

import javax.annotation.Nullable;

public interface DeferredResult<T> {

  boolean isCompleted();

  /** Null if {@link #isCompleted()} is false. */
  @Nullable
  ReadyResult<T> toReadyResult();
}
