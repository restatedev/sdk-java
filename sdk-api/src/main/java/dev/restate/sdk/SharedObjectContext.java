// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.serde.Serde;
import dev.restate.sdk.types.StateKey;
import java.util.Collection;
import java.util.Optional;

/**
 * This interface can be used only within shared handlers of virtual objects. It extends {@link
 * Context} adding access to the virtual object instance key-value state storage.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 *
 * @see Context
 */
public interface SharedObjectContext extends Context {
  /**
   * @return the key of this object
   */
  String key();

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
   * Gets all the known state keys for this virtual object instance.
   *
   * @return the immutable collection of known state keys.
   */
  Collection<String> stateKeys();
}
