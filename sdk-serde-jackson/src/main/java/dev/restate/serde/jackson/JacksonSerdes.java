// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import dev.restate.serde.Serde;
import java.util.stream.StreamSupport;

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

  static final ObjectMapper defaultMapper;
  static final SchemaGenerator schemaGenerator;

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

  /** Serialize/Deserialize class using the default object mapper. */
  public static <T> Serde<T> of(Class<T> clazz) {
    return of(defaultMapper, clazz);
  }

  /** Serialize/Deserialize class using a custom object mapper. */
  public static <T> Serde<T> of(ObjectMapper mapper, Class<T> clazz) {
    return JacksonSerdeFactory.create(mapper.constructType(clazz), clazz, schemaGenerator, mapper);
  }

  /** Serialize/Deserialize {@link TypeReference} using the default object mapper. */
  public static <T> Serde<T> of(TypeReference<T> typeReference) {
    return of(defaultMapper, typeReference);
  }

  /** Serialize/Deserialize {@link TypeReference} using a custom object mapper. */
  public static <T> Serde<T> of(ObjectMapper mapper, TypeReference<T> typeReference) {
    return JacksonSerdeFactory.create(
        mapper.constructType(typeReference), typeReference.getType(), schemaGenerator, mapper);
  }

  @SuppressWarnings("unchecked")
  static <E extends Throwable> void sneakyThrow(Object exception) throws E {
    throw (E) exception;
  }
}
