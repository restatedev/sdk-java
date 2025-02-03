// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingBiConsumer;
import dev.restate.common.function.ThrowingFunction;
import dev.restate.serde.RichSerde;
import dev.restate.serde.Serde;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Collection of common serializers/deserializers.
 *
 * <p>To ser/de POJOs using JSON, you can use the module {@code sdk-serde-jackson}.
 */
public abstract class JsonSerdes {

  private JsonSerdes() {}

  /** {@link Serde} for {@link String}. This writes and reads {@link String} as JSON value. */
  public static final Serde<@NonNull String> STRING =
      usingJackson(
          "string",
          JsonGenerator::writeString,
          p -> {
            if (p.nextToken() != JsonToken.VALUE_STRING) {
              throw new IllegalStateException(
                  "Expecting token " + JsonToken.VALUE_STRING + ", got " + p.getCurrentToken());
            }
            return p.getText();
          });

  /** {@link Serde} for {@link Boolean}. This writes and reads {@link Boolean} as JSON value. */
  public static final Serde<@NonNull Boolean> BOOLEAN =
      usingJackson(
          "boolean",
          JsonGenerator::writeBoolean,
          p -> {
            p.nextToken();
            return p.getBooleanValue();
          });

  /** {@link Serde} for {@link Byte}. This writes and reads {@link Byte} as JSON value. */
  public static final Serde<@NonNull Byte> BYTE =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getByteValue();
          });

  /** {@link Serde} for {@link Short}. This writes and reads {@link Short} as JSON value. */
  public static final Serde<@NonNull Short> SHORT =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getShortValue();
          });

  /** {@link Serde} for {@link Integer}. This writes and reads {@link Integer} as JSON value. */
  public static final Serde<@NonNull Integer> INT =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getIntValue();
          });

  /** {@link Serde} for {@link Long}. This writes and reads {@link Long} as JSON value. */
  public static final Serde<@NonNull Long> LONG =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getLongValue();
          });

  /** {@link Serde} for {@link Float}. This writes and reads {@link Float} as JSON value. */
  public static final Serde<@NonNull Float> FLOAT =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getFloatValue();
          });

  /** {@link Serde} for {@link Double}. This writes and reads {@link Double} as JSON value. */
  public static final Serde<@NonNull Double> DOUBLE =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getDoubleValue();
          });

  // --- Helpers for jackson-core

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private static <T extends @NonNull Object> Serde<T> usingJackson(
      String type,
      ThrowingBiConsumer<JsonGenerator, T> serializer,
      ThrowingFunction<JsonParser, T> deserializer) {
    return new RichSerde<>() {

      @Override
      public Object jsonSchema() {
        return Map.of("type", type);
      }

      @Override
      public Slice serialize(T value) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(outputStream)) {
          serializer.asBiConsumer().accept(gen, value);
        } catch (IOException e) {
          throw new RuntimeException("Cannot create JsonGenerator", e);
        }
        return Slice.wrap(outputStream.toByteArray());
      }

      @Override
      public T deserialize(Slice value) {
        ByteBufferBackedInputStream inputStream =
            new ByteBufferBackedInputStream(value.asReadOnlyByteBuffer());
        try (JsonParser parser = JSON_FACTORY.createParser(inputStream)) {
          return deserializer.asFunction().apply(parser);
        } catch (IOException e) {
          throw new RuntimeException("Cannot create JsonGenerator", e);
        }
      }

      @Override
      public String contentType() {
        return "application/json";
      }
    };
  }
}
