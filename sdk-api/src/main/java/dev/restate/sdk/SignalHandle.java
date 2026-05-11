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

/**
 * Handle to resolve or reject a named signal on a target invocation. Acquired via {@link
 * InvocationHandle#signal(String)}.
 *
 * <p>Unlike awakeables, signals are identified by {@code (invocationId, name)} and do not need to
 * be pre-registered: the resolution can arrive before or after the handler starts waiting on the
 * signal.
 */
public interface SignalHandle {

  /**
   * Resolve the signal with the given value.
   *
   * @param typeTag used to serialize the result payload.
   * @param payload the result payload. MUST NOT be null.
   */
  <T> void resolve(TypeTag<T> typeTag, T payload);

  /**
   * Resolve the signal with the given value.
   *
   * @param clazz used to serialize the result payload.
   * @param payload the result payload. MUST NOT be null.
   */
  default <T> void resolve(Class<T> clazz, T payload) {
    resolve(TypeTag.of(clazz), payload);
  }

  /**
   * Reject the signal with the given reason. The handler awaiting the signal will receive a
   * terminal error with {@code reason} as the message.
   *
   * @param reason the rejection reason. MUST NOT be null.
   */
  void reject(String reason);
}
