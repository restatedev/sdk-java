package dev.restate.sdk.core;

import dev.restate.sdk.core.serde.CustomSerdeFunctionsTypeTag;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

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
  TypeTag<String> STRING_UTF8 =
      TypeTag.using(
          s -> s.getBytes(StandardCharsets.UTF_8), b -> new String(b, StandardCharsets.UTF_8));

  Object get();

  static <T> TypeTag<T> ofClass(Class<T> clazz) {
    return () -> clazz;
  }

  static <T> TypeTag<T> using(Function<T, byte[]> serializer, Function<byte[], T> deserializer) {
    return new CustomSerdeFunctionsTypeTag<>(serializer, deserializer);
  }
}
