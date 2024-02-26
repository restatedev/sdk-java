// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import org.jspecify.annotations.Nullable;

/**
 * Interface to define interaction with deferred results.
 *
 * <p>Implementations of this class are provided by {@link Syscalls} and should not be
 * overriden/wrapped.
 *
 * <p>To resolve a {@link Deferred}, use {@link Syscalls#resolveDeferred(Deferred, SyscallCallback)}
 */
public interface Deferred<T> {

  boolean isCompleted();

  /**
   * @return {@code null} if {@link #isCompleted()} is false.
   */
  @Nullable Result<T> toResult();
}
