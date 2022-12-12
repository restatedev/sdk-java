package dev.restate.sdk.core.serde.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.serde.Serde;
import java.io.IOException;

/**
 * Serde implementation using Jackson.
 *
 * <p>Note: This {@link Serde} implementation is a catch-all implementation, meaning it will never
 * throw {@link UnsupportedOperationException} when serializing/deserializing it.
 */
public class JacksonSerde implements Serde {

  private final ObjectMapper mapper;

  private JacksonSerde(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public boolean supportsAnyType() {
    return true;
  }

  @Override
  public <T> T deserialize(Class<T> clazz, byte[] bytes) {
    try {
      return mapper.readValue(bytes, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  public <T> T deserialize(Object typeTag, byte[] value) {
    if (typeTag instanceof TypeReference) {
      return deserialize((TypeReference<? extends T>) typeTag, value);
    }
    if (typeTag instanceof Class) {
      return deserialize((Class<? extends T>) typeTag, value);
    }
    throw new UnsupportedOperationException("Unexpected type tag");
  }

  private <T> T deserialize(TypeReference<T> typeRef, byte[] bytes) {
    try {
      return mapper.readValue(bytes, typeRef);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> byte[] serialize(T value) {
    try {
      return mapper.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /** Creates a type reference {@link TypeTag}. */
  public static <T> TypeTag<T> typeRef(TypeReference<T> typeReference) {
    return () -> typeReference;
  }

  public static Serde usingMapper(ObjectMapper objectMapper) {
    return new JacksonSerde(objectMapper);
  }
}
