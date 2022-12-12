package dev.restate.sdk.core.serde;

import dev.restate.sdk.core.TypeTag;
import java.util.function.Function;

public class CustomSerdeFunctionsTypeTag<T> implements TypeTag<T> {

  private final Function<T, byte[]> serializer;
  private final Function<byte[], T> deserializer;

  public CustomSerdeFunctionsTypeTag(
      Function<T, byte[]> serializer, Function<byte[], T> deserializer) {
    this.serializer = serializer;
    this.deserializer = deserializer;
  }

  public Function<T, byte[]> getSerializer() {
    return serializer;
  }

  public Function<byte[], T> getDeserializer() {
    return deserializer;
  }

  @Override
  public Object get() {
    throw new IllegalStateException("Unexpected call to this method");
  }
}
