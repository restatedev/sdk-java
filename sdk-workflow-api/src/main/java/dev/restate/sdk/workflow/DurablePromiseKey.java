// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow;

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.Serde;

/**
 * This class holds information about durable promise's name and its type tag to be used for
 * serializing and deserializing it.
 *
 * @param <T> the generic type of the signal.
 */
public final class DurablePromiseKey<T> {

  private final String name;
  private final Serde<T> serde;

  private DurablePromiseKey(String name, Serde<T> serde) {
    this.name = name;
    this.serde = serde;
  }

  /** Create a new {@link DurablePromiseKey}. */
  public static <T> DurablePromiseKey<T> of(String name, Serde<T> serde) {
    return new DurablePromiseKey<>(name, serde);
  }

  /** Create a new {@link DurablePromiseKey} for {@link String} state. */
  public static DurablePromiseKey<String> string(String name) {
    return new DurablePromiseKey<>(name, CoreSerdes.JSON_STRING);
  }

  /** Create a new {@link DurablePromiseKey} for bytes state. */
  public static DurablePromiseKey<byte[]> raw(String name) {
    return new DurablePromiseKey<>(name, CoreSerdes.RAW);
  }

  public String name() {
    return name;
  }

  public Serde<T> serde() {
    return serde;
  }
}
