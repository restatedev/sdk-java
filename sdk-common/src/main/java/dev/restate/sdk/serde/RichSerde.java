// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.serde;

import java.nio.ByteBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Richer version of {@link Serde} containing schema information.
 *
 * <p>This API should be considered unstable to implement.
 *
 * <p>You can create one using {@link #withSchema(Object, Serde)}.
 */
public interface RichSerde<T extends @Nullable Object> extends Serde<T> {

  /**
   * @return a Draft 2020-12 Json Schema. It should be self-contained, and MUST not contain refs to
   *     files. If the schema shouldn't be serialized with Jackson, return a {@link String}
   */
  Object jsonSchema();

  static <T> RichSerde<T> withSchema(Object jsonSchema, Serde<T> inner) {
    return new RichSerde<>() {
      @Override
      public byte[] serialize(T value) {
        return inner.serialize(value);
      }

      @Override
      public ByteBuffer serializeToByteBuffer(T value) {
        return inner.serializeToByteBuffer(value);
      }

      @Override
      public T deserialize(ByteBuffer byteBuffer) {
        return inner.deserialize(byteBuffer);
      }

      @Override
      public T deserialize(byte[] value) {
        return inner.deserialize(value);
      }

      @Override
      public String contentType() {
        return inner.contentType();
      }

      @Override
      public Object jsonSchema() {
        return jsonSchema;
      }
    };
  }
}
