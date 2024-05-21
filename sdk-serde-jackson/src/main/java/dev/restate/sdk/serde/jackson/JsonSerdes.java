// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.serde.jackson;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.function.ThrowingBiConsumer;
import dev.restate.sdk.common.function.ThrowingFunction;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * {@link Serde} implementations for Jackson.
 *
 * <p>You can use these serdes for serializing and deserializing state, side effects results and
 * awakeables using Jackson's {@link ObjectMapper}.
 *
 * <p>For example:
 *
 * <pre>{@code
 * private static final StateKey<Person> PERSON = StateKey.of("person", JacksonSerdes.of(Person.class));
 * }</pre>
 *
 * Or using Jackson's {@link TypeReference} to encapsulate generics:
 *
 * <pre>{@code
 * private static final StateKey<List<Person>> PEOPLE = StateKey.of("people", JacksonSerdes.of(new TypeReference<>() {}));
 * }</pre>
 *
 * When no object mapper is provided, a default one is used, using the default {@link
 * com.fasterxml.jackson.core.JsonFactory} and discovering SPI modules.
 */
public final class JsonSerdes {

  private JsonSerdes() {}

  private static final ObjectMapper defaultMapper;

  static {
    defaultMapper = new ObjectMapper();
    // Find modules through SPI (e.g. jackson-datatype-jsr310)
    defaultMapper.findAndRegisterModules();
  }

  /** Serialize/Deserialize class using the default object mapper. */
  public static <T> Serde<T> of(Class<T> clazz) {
    return of(defaultMapper, clazz);
  }

  /** Serialize/Deserialize class using the provided object mapper. */
  public static <T> Serde<T> of(ObjectMapper mapper, Class<T> clazz) {
    return new Serde<>() {
      @Override
      public byte[] serialize(@Nullable T value) {
        try {
          return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
          sneakyThrow(e);
          return null;
        }
      }

      @Override
      public T deserialize(byte[] value) {
        try {
          return mapper.readValue(value, clazz);
        } catch (IOException e) {
          sneakyThrow(e);
          return null;
        }
      }

      @Override
      public String contentType() {
        return "application/json";
      }
    };
  }

  /** Serialize/Deserialize {@link TypeReference} using the default object mapper. */
  public static <T> Serde<T> of(TypeReference<T> typeReference) {
    return of(defaultMapper, typeReference);
  }

  /** Serialize/Deserialize {@link TypeReference} using the default object mapper. */
  public static <T> Serde<T> of(ObjectMapper mapper, TypeReference<T> typeReference) {
    return new Serde<>() {
      @Override
      public byte[] serialize(@Nullable T value) {
        try {
          return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
          sneakyThrow(e);
          return null;
        }
      }

      @Override
      public T deserialize(byte[] value) {
        try {
          return mapper.readValue(value, typeReference);
        } catch (IOException e) {
          sneakyThrow(e);
          return null;
        }
      }

      @Override
      public String contentType() {
        return "application/json";
      }
    };
  }

  /** {@link Serde} for {@link String}. This writes and reads {@link String} as JSON value. */
  public static Serde<String> STRING =
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
  public static Serde<Boolean> BOOLEAN =
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
  public static Serde<Integer> INT =
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
      public byte[] serialize(@Nullable T value) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(outputStream)) {
          serializer.asBiConsumer().accept(gen, value);
        } catch (IOException e) {
          sneakyThrow(e);
          return null;
        }
        return outputStream.toByteArray();
      }

      @Override
      public T deserialize(byte[] value) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(value);
        try (JsonParser parser = JSON_FACTORY.createParser(inputStream)) {
          return deserializer.asFunction().apply(parser);
        } catch (IOException e) {
          sneakyThrow(e);
          return null;
        }
      }

      @Override
      public String contentType() {
        return "application/json";
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Object exception) throws E {
    throw (E) exception;
  }
}
