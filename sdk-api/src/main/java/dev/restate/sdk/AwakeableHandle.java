// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.serde.TypeTag;

/** This class represents a handle to an {@link Awakeable} created in another service. */
public interface AwakeableHandle {

  /**
   * Complete with success the {@link Awakeable}.
   *
   * @param typeTag used to serialize the {@link Awakeable} result payload.
   * @param payload the result payload. MUST NOT be null.
   * @see Awakeable
   */
  <T> void resolve(TypeTag<T> typeTag, T payload);

  /**
   * Complete with success the {@link Awakeable}.
   *
   * @param clazz used to serialize the {@link Awakeable} result payload.
   * @param payload the result payload. MUST NOT be null.
   * @see Awakeable
   */
  default <T> void resolve(Class<T> clazz, T payload) {
    resolve(TypeTag.of(clazz), payload);
  }

  /**
   * Complete with failure the {@link Awakeable}.
   *
   * @param reason the rejection reason. MUST NOT be null.
   * @see Awakeable
   */
  void reject(String reason);
}
