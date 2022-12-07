package dev.restate.sdk.core.serde.protobuf;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.serde.Serde;
import java.lang.reflect.InvocationTargetException;

/**
 * Serde implementation using Protobuf. Please note that only classes implementing {@link
 * MessageLite} can be ser/de.
 */
public class ProtobufSerde implements Serde {

  @Override
  @SuppressWarnings("unchecked")
  public <T> T deserialize(Class<T> clazz, byte[] bytes) {
    if (MessageLite.class.isAssignableFrom(clazz)) {
      try {
        return (T) clazz.getMethod("parseFrom", byte[].class).invoke(null, (Object) bytes);
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new RuntimeException("Unexpected error when deserializing using class " + clazz, e);
      }
    }
    throw new UnsupportedOperationException(
        "Class "
            + clazz
            + " is not supported by the protobuf serde, as it doesn't implement MessageLite.");
  }

  @Override
  public <T> byte[] serialize(T value) {
    if (value instanceof MessageLite) {
      return ((MessageLite) value).toByteArray();
    }
    throw new UnsupportedOperationException(
        "Class "
            + value.getClass()
            + " is not supported by the protobuf serde, as it doesn't implement MessageLite.");
  }
}
