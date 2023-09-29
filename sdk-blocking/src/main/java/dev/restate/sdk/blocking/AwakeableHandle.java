package dev.restate.sdk.blocking;

import dev.restate.sdk.core.TypeTag;
import javax.annotation.Nonnull;

/** This class represents a handle to an {@link Awakeable} created in another service. */
public interface AwakeableHandle {

  /**
   * Complete with success the {@link Awakeable}.
   *
   * @param typeTag used to serialize the {@link Awakeable} result payload.
   * @param payload the result payload. MUST NOT be null.
   * @see Awakeable
   */
  <T> void resolve(TypeTag<T> typeTag, @Nonnull T payload);

  /**
   * Complete with failure the {@link Awakeable}.
   *
   * @param reason the rejection reason. MUST NOT be null.
   * @see Awakeable
   */
  void reject(String reason);
}
