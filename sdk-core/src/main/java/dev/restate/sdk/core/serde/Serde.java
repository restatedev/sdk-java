package dev.restate.sdk.core.serde;

/**
 * Interface to define serialization and deserialization to be used to store state and data into the
 * journal.
 */
public interface Serde {

  // TODO add priority system for SPI (additional iface for SPI)

  /**
   * Deserialize {@code bytes} into {@code T}.
   *
   * @param clazz the deserialized value class
   * @param bytes the bytes to deserialize
   * @return the deserialized value
   * @param <T> the deserialized value type
   * @throws UnsupportedOperationException if this cannot deserialize to the provided class
   */
  <T> T deserialize(Class<T> clazz, byte[] bytes);

  /**
   * Same as {@link #deserialize(Class, byte[])}, but with a raw type tag, allowing to use it with
   * Jackson's TypeReference.
   *
   * @see #deserialize(Class, byte[])
   */
  // TODO https://github.com/restatedev/java-sdk/issues/37
  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  default <T> T deserialize(Object typeTag, byte[] value) {
    if (typeTag instanceof Class<?>) {
      return deserialize((Class<? extends T>) typeTag, value);
    }
    throw new UnsupportedOperationException("Type tag " + typeTag + " is not supported");
  }

  /**
   * Serialize {@code value} into bytes.
   *
   * @param value the value to serialize
   * @return the serialized value
   * @param <T> the type of the value to serialize
   * @throws UnsupportedOperationException if this cannot serialize the provided value because it
   *     doesn't support it
   */
  <T> byte[] serialize(T value);
}
