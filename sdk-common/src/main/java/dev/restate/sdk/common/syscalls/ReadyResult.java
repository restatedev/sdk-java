// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import dev.restate.sdk.common.TerminalException;
import java.util.function.Function;
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

  <U> ReadyResult<U> map(Function<T, U> mapper);

  @Nullable
  TerminalException getFailure();
}
