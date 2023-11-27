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
import javax.annotation.Nullable;

/** {@link Serde} implementations for Jackson. */
public final class JacksonSerdes {

  private JacksonSerdes() {}

  private static final ObjectMapper defaultMapper;

  static {
    defaultMapper = new ObjectMapper();
    // Find modules through SPI (e.g. jackson-datatype-jsr310)
    defaultMapper.findAndRegisterModules();
  }

  public static <T> Serde<T> of(Class<T> clazz) {
    return of(defaultMapper, clazz);
  }

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
    };
  }

  public static <T> Serde<T> of(TypeReference<T> typeReference) {
    return of(defaultMapper, typeReference);
  }

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
    };
  }
}
