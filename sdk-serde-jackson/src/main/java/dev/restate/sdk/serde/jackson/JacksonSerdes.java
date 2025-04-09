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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import dev.restate.common.Slice;
import dev.restate.serde.Serde;
import dev.restate.serde.jackson.JacksonSerdeFactory;
import java.io.IOException;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.NonNull;

/**
 * @deprecated This will be removed in the next release, please check the individual methods for
 *     more details.
 */
@Deprecated(forRemoval = true, since = "2.0")
public final class JacksonSerdes {

  private JacksonSerdes() {}

  private static final ObjectMapper defaultMapper;
  private static final SchemaGenerator schemaGenerator;

  static {
    defaultMapper = new ObjectMapper();
    // Find modules through SPI (e.g. jackson-datatype-jsr310)
    defaultMapper.findAndRegisterModules();

    JacksonModule module =
        new JacksonModule(
            JacksonOption.RESPECT_JSONPROPERTY_REQUIRED, JacksonOption.INLINE_TRANSFORMED_SUBTYPES);
    SchemaGeneratorConfigBuilder configBuilder =
        new SchemaGeneratorConfigBuilder(
                defaultMapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
            .with(module);

    // Make sure we use `title` for types
    configBuilder
        .forTypesInGeneral()
        .withTypeAttributeOverride(
            (schema, scope, context) -> {
              if (schema.isObject()
                  && !schema.hasNonNull(
                      SchemaKeyword.TAG_TITLE.forVersion(
                          context.getGeneratorConfig().getSchemaVersion()))) {
                JsonNode typeKeyword =
                    schema.get(
                        SchemaKeyword.TAG_TYPE.forVersion(
                            context.getGeneratorConfig().getSchemaVersion()));
                boolean isObjectSchema =
                    typeKeyword != null
                        && ((typeKeyword.isTextual() && "object".equals(typeKeyword.textValue()))
                            || (typeKeyword.isArray()
                                && StreamSupport.stream(typeKeyword.spliterator(), false)
                                    .anyMatch(
                                        el -> el.isTextual() && "object".equals(el.textValue()))));
                if (isObjectSchema) {
                  schema.put(
                      SchemaKeyword.TAG_TITLE.forVersion(
                          context.getGeneratorConfig().getSchemaVersion()),
                      scope.getSimpleTypeDescription());
                }
              }
            });

    schemaGenerator = new SchemaGenerator(configBuilder.build());
  }

  /**
   * @deprecated Pass a {@link Class} to the context method requiring serialization/deserialization,
   *     instead of providing a serde using JacksonSerdes. This method will be removed in the next
   *     release.
   */
  @Deprecated(forRemoval = true, since = "2.0")
  public static <T> Serde<T> of(Class<T> clazz) {
    return of(defaultMapper, clazz);
  }

  /**
   * @deprecated Pass a {@link Class} to the context method requiring serialization/deserialization,
   *     instead of providing a serde using JacksonSerdes. If you need to customize the object
   *     mapper, look at {@link JacksonSerdeFactory} This method will be removed in the next
   *     release.
   */
  @Deprecated(forRemoval = true, since = "2.0")
  public static <T> Serde<T> of(ObjectMapper mapper, Class<T> clazz) {
    return new Serde<>() {
      @Override
      public Schema jsonSchema() {
        return new JsonSchema(schemaGenerator.generateSchema(clazz));
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
          return mapper.readValue(value.toByteArray(), clazz);
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

  /**
   * @deprecated Pass a {@link dev.restate.serde.TypeRef} to the context method requiring
   *     serialization/deserialization, instead of providing a serde using JacksonSerdes. This
   *     method will be removed in the next release.
   */
  @Deprecated(forRemoval = true, since = "2.0")
  public static <T> Serde<T> of(TypeReference<T> typeReference) {
    return of(defaultMapper, typeReference);
  }

  /**
   * @deprecated Pass a {@link dev.restate.serde.TypeRef} to the context method requiring
   *     serialization/deserialization, instead of providing a serde using JacksonSerdes. If you
   *     need to customize the object mapper, look at {@link JacksonSerdeFactory} This method will
   *     be removed in the next release.
   */
  @Deprecated(forRemoval = true, since = "2.0")
  public static <T> Serde<T> of(ObjectMapper mapper, TypeReference<T> typeReference) {
    return new Serde<>() {
      @Override
      public Schema jsonSchema() {
        return new JsonSchema(typeReference.getType());
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
          return mapper.readValue(value.toByteArray(), typeReference);
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
