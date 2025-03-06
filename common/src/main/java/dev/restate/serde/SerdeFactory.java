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
 * This factory creates {@link Serde} that are used in various places of the SDK, notably:
 *
 * <ul>
 *   <li>To deserialize handler's input and deserialize the output
 *   <li>To serialize and deserialize state, awakeables, promises and so on
 * </ul>
 *
 * When using the Java APIs, a Jackson based implementation is used by default, see {@link
 * dev.restate.sdk.serde.jackson.JacksonSerdeFactory} in {@code sdk-serde-jackson} module.
 *
 * <p>When using the Kotlin APIs, a Kotlin Serialization implementation is used by default, see
 * {@link dev.restate.sdk.kotlin.serialization.KotlinSerializationSerdeFactory} in {@code
 * sdk-api-kotlin} module.
 *
 * <p>You can override the default factory used for a given service by adding the annotation {@link
 * dev.restate.sdk.annotation.CustomSerdeFactory} on the interface/class annotated with {@link
 * dev.restate.sdk.annotation.Service}/{@link dev.restate.sdk.annotation.VirtualObject}/{@link
 * dev.restate.sdk.annotation.Workflow}
 *
 * <p>Implementations <b>MUST</b> be thread safe.
 */
public interface SerdeFactory {

  <T> Serde<T> create(TypeRef<T> typeRef);

  <T> Serde<T> create(Class<T> clazz);

  default <T> Serde<T> create(TypeTag<T> typeTag) {
    if (typeTag instanceof TypeTag.Class<T> tClass) {
      return this.create(tClass.type());
    } else if (typeTag instanceof TypeRef<T> tTypeRef) {
      return this.create(tTypeRef);
    } else {
      return ((Serde<T>) typeTag);
    }
  }

  /** Noop factory, that will throw {@link UnsupportedOperationException} when called. */
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
