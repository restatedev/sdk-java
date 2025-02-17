// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde;

public interface SerdeInfo<T> {

  record Class<T>(java.lang.Class<T> type) implements SerdeInfo<T> {}

  record TypeRef<T>(dev.restate.serde.TypeRef<T> typeRef) implements SerdeInfo<T> {}

  static <T> SerdeInfo<T> of(java.lang.Class<T> type) {
    return new Class<>(type);
  }

  static <T> SerdeInfo<T> of(dev.restate.serde.TypeRef<T> type) {
    return new TypeRef<>(type);
  }

  static <T> SerdeInfo<T> of(dev.restate.serde.Serde<T> serde) {
    return serde;
  }
}
