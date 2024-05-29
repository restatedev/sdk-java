// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

/** This class represents a handle to an {@link DurablePromise} created in another handler. */
public interface DurablePromiseHandle<T> {

  /**
   * Complete with success the {@link DurablePromise}.
   *
   * @param payload the result payload.
   * @see DurablePromise
   */
  void resolve(T payload) throws IllegalStateException;

  /**
   * Complete with failure the {@link DurablePromise}.
   *
   * @param reason the rejection reason. MUST NOT be null.
   * @see DurablePromise
   */
  void reject(String reason) throws IllegalStateException;
}
