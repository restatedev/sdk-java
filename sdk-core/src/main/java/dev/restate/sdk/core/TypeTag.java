package dev.restate.sdk.core;

/**
 * Marker interface to carry non-class type tags and generic at the same time.
 *
 * <p>This interface can be used by implementations of {@link dev.restate.sdk.core.serde.Serde} to
 * support type tags different from {@link Class}, for example to circumvent type erasure (like
 * Jackson's TypeReference).
 */
public interface TypeTag<T> {

  TypeTag<Void> VOID = TypeTag.ofClass(Void.TYPE);
  TypeTag<byte[]> BYTES = TypeTag.ofClass(byte[].class);

  Object get();

  static <T> TypeTag<T> ofClass(Class<T> clazz) {
    return () -> clazz;
  }
}
