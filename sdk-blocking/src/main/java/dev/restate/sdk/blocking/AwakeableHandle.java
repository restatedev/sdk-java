package dev.restate.sdk.blocking;

import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.serde.Serde;
import javax.annotation.Nonnull;

/** This class represents a handle to an {@link Awakeable} created in another service. */
public interface AwakeableHandle {

  /**
   * Complete with success the {@link Awakeable}.
   *
   * @param payload the payload of the response. This can be either {@code byte[]}, {@link
   *     com.google.protobuf.ByteString}, or any object, which will be serialized by using the
   *     configured {@link Serde}. MUST NOT be null.
   * @see Awakeable
   */
  <T> void resolve(TypeTag<T> typeTag, @Nonnull T payload);

  /**
   * Complete with failure the {@link Awakeable}.
   *
   * @param reason the rejection reason.
   * @see Awakeable
   */
  void reject(String reason);
}
