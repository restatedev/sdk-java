// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.serde.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.sdk.common.Serde;
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
public final class JacksonSerdes {

  private JacksonSerdes() {}

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
          throw new RuntimeException("Cannot serialize value", e);
        }
      }

      @Override
      public T deserialize(byte[] value) {
        try {
          return mapper.readValue(value, clazz);
        } catch (IOException e) {
          throw new RuntimeException("Cannot deserialize value", e);
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
          throw new RuntimeException("Cannot serialize value", e);
        }
      }

      @Override
      public T deserialize(byte[] value) {
        try {
          return mapper.readValue(value, typeReference);
        } catch (IOException e) {
          throw new RuntimeException("Cannot deserialize value", e);
        }
      }

      @Override
      public String contentType() {
        return "application/json";
      }
    };
  }
}
