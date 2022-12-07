package dev.restate.sdk.core.serde.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.serde.ChainedSerde;
import dev.restate.sdk.core.serde.Serde;
import java.io.IOException;

/**
 * Serde implementation using Jackson.
 *
 * <p>Note: This {@link Serde} implementation is a catch-all implementation, meaning it will never
 * throw {@link UnsupportedOperationException} when serializing/deserializing it. Be careful to
 * always use it as last {@link Serde}, when used in conjunction with {@link ChainedSerde}.
 */
public class JacksonSerde implements Serde {

  // TODO implement SPI to inject JacksonSerde with CBOR factory

  private final ObjectMapper mapper;

  public JacksonSerde(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public <T> T deserialize(TypeReference<T> typeRef, byte[] bytes) {
    try {
      return mapper.readValue(bytes, typeRef);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
    return Serde.super.deserialize(typeTag, value);
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
}
