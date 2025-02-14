// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.types;

import dev.restate.serde.Serde;
import dev.restate.serde.SerdeInfo;
import dev.restate.serde.TypeRef;

/**
 * This class holds information about durable promise's name and its type tag to be used for
 * serializing and deserializing it.
 *
 * @param <T> the generic type of the signal.
 */
public final class DurablePromiseKey<T> {

  private final String name;
  private final SerdeInfo<T> serdeInfo;

  private DurablePromiseKey(String name, SerdeInfo<T> serdeInfo) {
    this.name = name;
    this.serdeInfo = serdeInfo;
  }

  /** Create a new {@link DurablePromiseKey}. */
  public static <T> DurablePromiseKey<T> of(String name, SerdeInfo<T> serdeInfo) {
    return new DurablePromiseKey<>(name, serdeInfo);
  }

  /** Create a new {@link DurablePromiseKey}. */
  public static <T> DurablePromiseKey<T> of(String name, Class<T> clazz) {
    return new DurablePromiseKey<>(name, SerdeInfo.of(clazz));
  }

  /** Create a new {@link DurablePromiseKey}. */
  public static <T> DurablePromiseKey<T> of(String name, TypeRef<T> typeRef) {
    return new DurablePromiseKey<>(name, SerdeInfo.of(typeRef));
  }

  /** Create a new {@link DurablePromiseKey} for bytes state. */
  public static DurablePromiseKey<byte[]> bytes(String name) {
    return new DurablePromiseKey<>(name, Serde.RAW);
  }

  public String name() {
    return name;
  }

  public SerdeInfo<T> serdeInfo() {
    return serdeInfo;
  }
}
