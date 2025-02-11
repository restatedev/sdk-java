// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.serde.protobuf;

import com.google.protobuf.*;
import dev.restate.common.Slice;
import dev.restate.serde.Serde;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Collection of serializers/deserializers for Protobuf */
public abstract class ProtobufSerdes {

  private ProtobufSerdes() {}

  public static <T extends MessageLite> Serde<T> of(Parser<T> parser) {
    return new Serde<>() {
      @Override
      public Slice serialize(@Nullable T value) {
        return Slice.wrap(Objects.requireNonNull(value).toByteArray());
      }

      @Override
      public T deserialize(Slice value) {
        try {
          return parser.parseFrom(value.toByteArray());
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException("Cannot deserialize Protobuf object", e);
        }
      }
    };
  }
}
