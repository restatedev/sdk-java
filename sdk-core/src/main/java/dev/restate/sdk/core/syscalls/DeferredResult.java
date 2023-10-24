package dev.restate.sdk.core.syscalls;

import javax.annotation.Nullable;

public interface DeferredResult<T> {

  boolean isCompleted();

  /**
   * @return {@code null} if {@link #isCompleted()} is false.
   */
  @Nullable
  ReadyResult<T> toReadyResult();
}
