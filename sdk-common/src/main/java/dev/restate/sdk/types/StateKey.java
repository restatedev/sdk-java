// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.types;

import dev.restate.sdk.serde.Serde;

/**
 * This class holds information about state's name and its type tag to be used for serializing and
 * deserializing it.
 *
 * @param <T> the generic type of the state.
 */
public final class StateKey<T> {

  private final String name;
  private final Serde<T> serde;

  private StateKey(String name, Serde<T> serde) {
    this.name = name;
    this.serde = serde;
  }

  /** Create a new {@link StateKey}. */
  public static <T> StateKey<T> of(String name, Serde<T> serde) {
    return new StateKey<>(name, serde);
  }

  /** Create a new {@link StateKey} for bytes state. */
  public static StateKey<byte[]> bytes(String name) {
    return new StateKey<>(name, Serde.RAW);
  }

  public String name() {
    return name;
  }

  public Serde<T> serde() {
    return serde;
  }
}
