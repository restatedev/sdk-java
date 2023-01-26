package dev.restate.sdk.core.syscalls;

import io.grpc.StatusRuntimeException;
import javax.annotation.Nullable;

/**
 * Result can be 3 valued:
 *
 * <ul>
 *   <li>Empty
 *   <li>Result
 *   <li>Failure
 * </ul>
 *
 * Empty and Result are used to distinguish the logical empty with the null result.
 *
 * <p>Failure in a ready result is always a user failure, and never a syscall failure, as opposed to
 * {@link SyscallCallback#onCancel(Throwable)}.
 *
 * @param <T> result type
 */
public interface ReadyResult<T> {

  /**
   * @return true if there is no failure.
   */
  boolean isSuccess();

  boolean isEmpty();

  @Nullable
  T getResult();

  @Nullable
  StatusRuntimeException getFailure();
}
