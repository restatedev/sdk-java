package dev.restate.sdk.core.serde;

/**
 * Interface to define serialization and deserialization to be used to store state and data into the
 * journal.
 */
public interface Serde {

  /**
   * @return {@code true} when this implementation can try to ser/de every type, independently of
   *     the fact that they're tagged or not for this specific serde. In other words, when this
   *     method returns true, {@link #deserialize(Class, byte[])} and {@link #serialize(Object)}
   *     should never throw {@link UnsupportedOperationException}. For example, Jackson serde will
   *     return true for this method, because it can deserialize every object, even when not tagged
   *     with Jackson annotations. Protobuf serde will return false, as it can ser/de only types
   *     extending the protobuf {@link com.google.protobuf.MessageLite} interface.
   *     <p>When chaining multiple {@link Serde}, only one instance can support any type.
   */
  boolean supportsAnyType();

  /**
   * Deserialize {@code bytes} into {@code T}.
   *
   * @param clazz the deserialized value class
   * @param bytes the bytes to deserialize
   * @return the deserialized value
   * @param <T> the deserialized value type
   * @throws UnsupportedOperationException if this implementation cannot deserialize using the
   *     provided class
   */
  <T> T deserialize(Class<T> clazz, byte[] bytes);

  /**
   * Same as {@link #deserialize(Class, byte[])}, but with a raw type tag, allowing to use it with
   * Jackson's TypeReference.
   *
   * @see #deserialize(Class, byte[])
   */
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
   * @throws UnsupportedOperationException if this implementation cannot serialize the provided
   *     value
   */
  <T> byte[] serialize(T value);
}
