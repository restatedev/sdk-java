// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.*;
import dev.restate.sdk.common.syscalls.Syscalls;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * This interface extends {@link UnkeyedContext} adding access to the service instance key-value
 * state storage
 *
 * @see UnkeyedContext
 */
@NotThreadSafe
public interface KeyedContext extends UnkeyedContext {

  /**
   * Gets the state stored under key, deserializing the raw value using the {@link Serde} in the
   * {@link StateKey}.
   *
   * @param key identifying the state to get and its type.
   * @return an {@link Optional} containing the stored state deserialized or an empty {@link
   *     Optional} if not set yet.
   * @throws RuntimeException when the state cannot be deserialized.
   */
  <T> Optional<T> get(StateKey<T> key);

  /**
   * Clears the state stored under key.
   *
   * @param key identifying the state to clear.
   */
  void clear(StateKey<?> key);

  /** Clears all the state of this service instance key-value state storage */
  void clearAll();

  /**
   * Sets the given value under the given key, serializing the value using the {@link Serde} in the
   * {@link StateKey}.
   *
   * @param key identifying the value to store and its type.
   * @param value to store under the given key. MUST NOT be null.
   */
  <T> void set(StateKey<T> key, @Nonnull T value);

  /**
   * Create a {@link KeyedContext}. This will look up the thread-local/async-context storage for the
   * underlying context implementation, so make sure to call it always from the same context where
   * the service is executed.
   */
  static KeyedContext current() {
    return fromSyscalls(Syscalls.current());
  }

  /** Build a RestateContext from the underlying {@link Syscalls} object. */
  static KeyedContext fromSyscalls(Syscalls syscalls) {
    return new ContextImpl(syscalls);
  }
}
