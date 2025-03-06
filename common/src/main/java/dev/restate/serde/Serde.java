// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde;

import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingFunction;
import java.util.Objects;
import org.jspecify.annotations.*;

/**
 * Interface defining serialization and deserialization of concrete types.
 *
 * <p>For more info on the serialization stack, and how to customize it, see {@link SerdeFactory}.
 *
 * <p>Implementations <b>MUST</b> be thread safe.
 *
 * <p>You can create a custom one using {@link #using(String, ThrowingFunction, ThrowingFunction)}.
 *
 * @see SerdeFactory
 */
@NullMarked
public interface Serde<T extends @Nullable Object> extends TypeTag<T> {

  Slice serialize(T value);

  T deserialize(Slice value);

  // --- Metadata about the serialized/deserialized content

  /**
   * Content-type to use in request/responses.
   *
   * <p>If null, the SDK assumes the produced output is empty. This might change in the future.
   */
  default @Nullable String contentType() {
    return "application/octet-stream";
  }

  /**
   * @return a Draft 2020-12 Json Schema. It should be self-contained, and MUST not contain refs to
   *     files or HTTP. The schema is currently used by Restate to introspect the service contract
   *     and generate an OpenAPI definition.
   */
  default @Nullable Schema jsonSchema() {
    return null;
  }

  sealed interface Schema {}

  /** Schema to be serialized using internal Jackson mapper. */
  record JsonSchema(Object schema) implements Schema {}

  /** Schema already serialized to String. The string should be a valid json schema. */
  record StringifiedJsonSchema(String schema) implements Schema {}

  /**
   * Like {@link #using(String, ThrowingFunction, ThrowingFunction)}, using content-type {@code
   * application/octet-stream}.
   */
  static <T extends @NonNull Object> Serde<@NonNull T> using(
      ThrowingFunction<T, byte[]> serializer, ThrowingFunction<byte[], T> deserializer) {
    return new Serde<>() {
      @Override
      public Slice serialize(T value) {
        return Slice.wrap(serializer.asFunction().apply(Objects.requireNonNull(value)));
      }

      @Override
      public T deserialize(Slice value) {
        return deserializer.asFunction().apply(value.toByteArray());
      }
    };
  }

  /**
   * Create a {@link Serde} from {@code serializer}/{@code deserializer} lambdas, tagging with
   * {@code contentType}. Before invoking the serializer, we check that {@code value} is non-null.
   */
  static <T extends @NonNull Object> Serde<@NonNull T> using(
      String contentType,
      ThrowingFunction<T, byte[]> serializer,
      ThrowingFunction<byte[], T> deserializer) {
    return new Serde<>() {
      @Override
      public Slice serialize(T value) {
        return Slice.wrap(serializer.asFunction().apply(Objects.requireNonNull(value)));
      }

      @Override
      public T deserialize(Slice value) {
        return deserializer.asFunction().apply(value.toByteArray());
      }

      @Override
      public String contentType() {
        return contentType;
      }
    };
  }

  static <T> Serde<T> withContentType(String contentType, Serde<T> inner) {
    return new Serde<>() {
      @Override
      public Slice serialize(T value) {
        return inner.serialize(value);
      }

      @Override
      public T deserialize(Slice value) {
        return inner.deserialize(value);
      }

      @Override
      public String contentType() {
        return contentType;
      }
    };
  }

  /** Noop {@link Serde} for void. */
  Serde<@Nullable Void> VOID =
      new Serde<>() {
        @Override
        public Slice serialize(Void value) {
          return Slice.EMPTY;
        }

        @Override
        public Void deserialize(Slice value) {
          return null;
        }

        @Override
        public String contentType() {
          return null;
        }
      };

  /** Pass through {@link Serde} for byte array. */
  Serde<byte[]> RAW =
      new Serde<>() {
        @Override
        public Slice serialize(byte[] value) {
          return Slice.wrap(Objects.requireNonNull(value));
        }

        @Override
        public byte[] deserialize(Slice value) {
          return value.toByteArray();
        }
      };

  /** Passthrough serializer/deserializer */
  Serde<Slice> SLICE =
      new Serde<>() {
        @Override
        public Slice serialize(Slice value) {
          return value;
        }

        @Override
        public Slice deserialize(Slice value) {
          return value;
        }
      };
}
