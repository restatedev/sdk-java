// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde;

/**
 * This interface is used by the serialization stack to carry types runtime information. It is used
 * by the configured {@link SerdeFactory} to construct the concrete {@link Serde}.
 *
 * <p>Subclasses include {@link TypeRef}, {@link Class} and {@link Serde} itself.
 */
public interface TypeTag<T> {

  record Class<T>(java.lang.Class<T> type) implements TypeTag<T> {}

  /**
   * Create a {@link TypeTag} from a {@link java.lang.Class}. If the given type is parametrized, you
   * should use {@link #of(TypeRef)} instead.
   */
  static <T> TypeTag<T> of(java.lang.Class<T> type) {
    return new Class<>(type);
  }

  static <T> TypeTag<T> of(dev.restate.serde.TypeRef<T> type) {
    return type;
  }

  static <T> TypeTag<T> of(dev.restate.serde.Serde<T> serde) {
    return serde;
  }
}
