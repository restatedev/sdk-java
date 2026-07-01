// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.StateKey;
import dev.restate.serde.Serde;
import org.jspecify.annotations.NonNull;

/**
 * This interface can be used only within exclusive handlers of virtual objects. It extends {@link
 * Context} adding access to the virtual object instance key-value state storage.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 *
 * @see Context
 * @deprecated The {@code Context}-parameter programming model is superseded by the reflection-based
 *     API. Rather than accepting an {@code ObjectContext} parameter, use {@code Restate.state()}
 *     (which supports both reads and writes) inside the handler. See the <a
 *     href="https://github.com/restatedev/sdk-java/blob/main/MIGRATION.md">migration guide</a>.
 */
@Deprecated(since = "2.9", forRemoval = true)
public interface ObjectContext extends SharedObjectContext {
  /**
   * Clears the state stored under key.
   *
   * @param key identifying the state to clear.
   */
  void clear(StateKey<?> key);

  /** Clears all the state of this virtual object instance key-value state storage */
  void clearAll();

  /**
   * Sets the given value under the given key, serializing the value using the {@link Serde} in the
   * {@link StateKey}.
   *
   * @param key identifying the value to store and its type.
   * @param value to store under the given key. MUST NOT be null.
   */
  <T> void set(StateKey<T> key, @NonNull T value);
}
