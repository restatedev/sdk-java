// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingBiConsumer;
import dev.restate.common.function.ThrowingFunction;
import dev.restate.serde.Serde;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * This is a copy of TestSerdes in sdk-api, we need it because otherwise we have a circular dep with
 * sdk-api
 */
public abstract class TestSerdes {

  private TestSerdes() {}

  /** {@link Serde} for {@link String}. This writes and reads {@link String} as JSON value. */
  public static final Serde<String> STRING =
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
  public static final Serde<Boolean> BOOLEAN =
      usingJackson(
          JsonGenerator::writeBoolean,
          p -> {
            p.nextToken();
            return p.getBooleanValue();
          });

  /** {@link Serde} for {@link Byte}. This writes and reads {@link Byte} as JSON value. */
  public static Serde<Byte> BYTE =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getByteValue();
          });

  /** {@link Serde} for {@link Short}. This writes and reads {@link Short} as JSON value. */
  public static Serde<Short> SHORT =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getShortValue();
          });

  /** {@link Serde} for {@link Integer}. This writes and reads {@link Integer} as JSON value. */
  public static final Serde<Integer> INT =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getIntValue();
          });

  /** {@link Serde} for {@link Long}. This writes and reads {@link Long} as JSON value. */
  public static Serde<Long> LONG =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getLongValue();
          });

  /** {@link Serde} for {@link Float}. This writes and reads {@link Float} as JSON value. */
  public static Serde<Float> FLOAT =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getFloatValue();
          });

  /** {@link Serde} for {@link Double}. This writes and reads {@link Double} as JSON value. */
  public static Serde<Double> DOUBLE =
      usingJackson(
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getDoubleValue();
          });

  // --- Helpers for jackson-core

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private static <T> Serde<T> usingJackson(
      ThrowingBiConsumer<JsonGenerator, T> serializer,
      ThrowingFunction<JsonParser, T> deserializer) {
    return new Serde<>() {
      @Override
      public Slice serialize(@Nullable T value) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(outputStream)) {
          serializer.asBiConsumer().accept(gen, value);
        } catch (IOException e) {
          throw new RuntimeException("Cannot create JsonGenerator", e);
        }
        return Slice.wrap(outputStream.toByteArray());
      }

      @Override
      public T deserialize(@NotNull Slice value) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(value.toByteArray());
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
