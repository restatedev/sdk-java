// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

/**
 * Strategy for upcasting an {@link InvocationInput} before it is processed by the state machine.
 * Implementations may transform the payloads in the underlying protocol messages (e.g. to migrate
 * from an older schema version to a newer one) while keeping the semantics intact.
 *
 * @author Milan Savic
 */
public interface InvocationInputUpcaster {

  /**
   * Upcasts the given {@link InvocationInput}. Implementations should be tolerant of {@code null}
   * inputs and return {@code null} in that case. If no change is required, the same input instance
   * may be returned.
   *
   * @param input the original invocation input, may be {@code null}
   * @return the upcasted invocation input, or the original input if no changes were applied
   */
  InvocationInput upcast(InvocationInput input);
}
