// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde;

public interface SerdeFactory {

  <T> Serde<T> create(TypeRef<T> typeRef);

  <T> Serde<T> create(Class<T> clazz);

  default <T> Serde<T> create(SerdeInfo<T> serdeInfo) {
    if (serdeInfo instanceof SerdeInfo.Class<T> tClass) {
      return this.create(tClass.type());
    } else if (serdeInfo instanceof SerdeInfo.TypeRef<T> tTypeRef) {
      return this.create(tTypeRef.typeRef());
    } else {
      return ((Serde<T>) serdeInfo);
    }
  }

  SerdeFactory NOOP =
      new SerdeFactory() {
        @Override
        public <T> Serde<T> create(TypeRef<T> typeRef) {
          throw new UnsupportedOperationException(
              "No SerdeFactory class was configured. Please configure one.");
        }

        @Override
        public <T> Serde<T> create(Class<T> clazz) {
          throw new UnsupportedOperationException(
              "No SerdeFactory class was configured. Please configure one.");
        }
      };
}
