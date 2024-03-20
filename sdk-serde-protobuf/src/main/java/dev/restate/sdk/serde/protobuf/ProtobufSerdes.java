// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.serde.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import dev.restate.sdk.common.Serde;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Collection of serializers/deserializers for Protobuf */
public abstract class ProtobufSerdes {

  private ProtobufSerdes() {}

  public static <T extends MessageLite> Serde<T> of(Parser<T> parser) {
    return new Serde<>() {
      @Override
      public byte[] serialize(@Nullable T value) {
        return Objects.requireNonNull(value).toByteArray();
      }

      @Override
      public T deserialize(byte[] value) {
        try {
          return parser.parseFrom(value);
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException("Cannot deserialize Protobuf object", e);
        }
      }

      // -- We reimplement the ByteString variants here as it might be more efficient to use them.
      @Override
      public ByteString serializeToByteString(@Nullable T value) {
        return Objects.requireNonNull(value).toByteString();
      }

      @Override
      public T deserialize(ByteString byteString) {
        try {
          return parser.parseFrom(byteString);
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException("Cannot deserialize Protobuf object", e);
        }
      }
    };
  }
}
