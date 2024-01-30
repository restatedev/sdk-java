// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import dev.restate.sdk.common.function.ThrowingBiConsumer;
import dev.restate.sdk.common.function.ThrowingFunction;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Collection of common serializers/deserializers.
 *
 * <p>To ser/de POJOs using JSON, you can use the module {@code sdk-serde-jackson}.
 */
public abstract class CoreSerdes {

  private CoreSerdes() {}

  /** Noop {@link Serde} for void. */
  public static Serde<Void> VOID =
      new Serde<>() {
        @Override
        public byte[] serialize(Void value) {
          return new byte[0];
        }

        @Override
        public ByteString serializeToByteString(@Nullable Void value) {
          return ByteString.EMPTY;
        }

        @Override
        public Void deserialize(byte[] value) {
          return null;
        }

        @Override
        public Void deserialize(ByteString byteString) {
          return null;
        }
      };

  /** Pass through {@link Serde} for byte array. */
  public static Serde<byte[]> RAW =
      new Serde<>() {
        @Override
        public byte[] serialize(byte[] value) {
          return Objects.requireNonNull(value);
        }

        @Override
        public byte[] deserialize(byte[] value) {
          return value;
        }
      };

  /** {@link Serde} for {@link String}. This writes and reads {@link String} as JSON value. */
  public static Serde<String> JSON_STRING =
      usingJackson(
          JsonGenerator::writeString,
          p -> {
            if (p.nextToken() != JsonToken.VALUE_STRING) {
              throw new IllegalStateException(
                  "Expecting token " + JsonToken.VALUE_STRING + ", got " + p.getCurrentToken());
            }
            return p.getText();
          });

  /** {@link Serde} for {@link Boolean}. This writes and reads {@link Boolean} as JSON value. */
  public static Serde<Boolean> JSON_BOOLEAN =
      usingJackson(
          JsonGenerator::writeBoolean,
          p -> {
            p.nextToken();
            return p.getBooleanValue();
          });

  /** {@link Serde} for {@link Byte}. This writes and reads {@link Byte} as JSON value. */
  public static Serde<Byte> JSON_BYTE =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getByteValue();
          });

  /** {@link Serde} for {@link Short}. This writes and reads {@link Short} as JSON value. */
  public static Serde<Short> JSON_SHORT =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getShortValue();
          });

  /** {@link Serde} for {@link Integer}. This writes and reads {@link Integer} as JSON value. */
  public static Serde<Integer> JSON_INT =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getIntValue();
          });

  /** {@link Serde} for {@link Long}. This writes and reads {@link Long} as JSON value. */
  public static Serde<Long> JSON_LONG =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getLongValue();
          });

  /** {@link Serde} for {@link Float}. This writes and reads {@link Float} as JSON value. */
  public static Serde<Float> JSON_FLOAT =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getFloatValue();
          });

  /** {@link Serde} for {@link Double}. This writes and reads {@link Double} as JSON value. */
  public static Serde<Double> JSON_DOUBLE =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getDoubleValue();
          });

  public static <T extends MessageLite> Serde<T> ofProtobuf(Parser<T> parser) {
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

  // --- Helpers for jackson-core

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private static <T> Serde<T> usingJackson(
      ThrowingBiConsumer<JsonGenerator, T> serializer,
      ThrowingFunction<JsonParser, T> deserializer) {
    return new Serde<>() {
      @Override
      public byte[] serialize(@Nullable T value) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(outputStream)) {
          serializer.asBiConsumer().accept(gen, value);
        } catch (IOException e) {
          throw new RuntimeException("Cannot create JsonGenerator", e);
        }
        return outputStream.toByteArray();
      }

      @Override
      public T deserialize(byte[] value) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(value);
        try (JsonParser parser = JSON_FACTORY.createParser(inputStream)) {
          return deserializer.asFunction().apply(parser);
        } catch (IOException e) {
          throw new RuntimeException("Cannot create JsonGenerator", e);
        }
      }
    };
  }
}
