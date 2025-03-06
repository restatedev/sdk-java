// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.serde.jackson;

import static dev.restate.sdk.serde.jackson.JacksonSerdes.sneakyThrow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import dev.restate.common.Slice;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.TypeRef;
import java.io.IOException;
import java.lang.reflect.Type;
import org.jspecify.annotations.NonNull;

/**
 * This class implements {@link SerdeFactory} using Jackson's {@link ObjectMapper}.
 *
 * <p>If you want to customize the {@link ObjectMapper} used in your service, it is recommended to
 * subclass this class using the constructor {@link #JacksonSerdeFactory(ObjectMapper)}, and then
 * register it using the {@link dev.restate.sdk.annotation.CustomSerdeFactory} annotation.
 */
public class JacksonSerdeFactory implements SerdeFactory {

  public static final JacksonSerdeFactory DEFAULT = new JacksonSerdeFactory();

  private final ObjectMapper mapper;
  private final SchemaGenerator schemaGenerator;

  public JacksonSerdeFactory() {
    this(JacksonSerdes.defaultMapper);
  }

  public JacksonSerdeFactory(ObjectMapper mapper) {
    this(mapper, JacksonSerdes.schemaGenerator);
  }

  public JacksonSerdeFactory(ObjectMapper mapper, SchemaGenerator schemaGenerator) {
    this.mapper = mapper;
    this.schemaGenerator = schemaGenerator;
  }

  @Override
  public <T> Serde<T> create(TypeRef<T> typeRef) {
    return create(
        mapper.constructType(typeRef.getType()), typeRef.getType(), schemaGenerator, mapper);
  }

  @Override
  public <T> Serde<T> create(Class<T> clazz) {
    return create(mapper.constructType(clazz), clazz, schemaGenerator, mapper);
  }

  static <T> Serde<T> create(
      JavaType constructedType,
      Type originalType,
      SchemaGenerator schemaGenerator,
      ObjectMapper mapper) {
    return new Serde<>() {
      @Override
      public Schema jsonSchema() {
        return new Serde.JsonSchema(schemaGenerator.generateSchema(originalType));
      }

      @Override
      public Slice serialize(T value) {
        try {
          return Slice.wrap(mapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
          sneakyThrow(e);
          return null;
        }
      }

      @Override
      public T deserialize(@NonNull Slice value) {
        try {
          return mapper.readValue(value.toByteArray(), constructedType);
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
}
