package dev.restate.sdk.core.syscalls;

import javax.annotation.Nullable;

/**
 * Interface to define interaction with deferred results.
 *
 * <p>Implementations of this class are provided by {@link Syscalls} and should not be
 * overriden/wrapped.
 *
 * <p>To resolve a {@link DeferredResult}, check {@link Syscalls#resolveDeferred(DeferredResult,
 * SyscallCallback)}
 */
public interface DeferredResult<T> {

  boolean isCompleted();

  /**
   * @return {@code null} if {@link #isCompleted()} is false.
   */
  @Nullable
  ReadyResult<T> toReadyResult();
}
