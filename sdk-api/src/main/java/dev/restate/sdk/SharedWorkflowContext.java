// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.DurablePromiseKey;

/**
 * This interface can be used only within shared handlers of workflow. It extends {@link Context}
 * adding access to the workflow instance key-value state storage and to the {@link DurablePromise}
 * API.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 *
 * @see Context
 * @see SharedObjectContext
 */
public interface SharedWorkflowContext extends SharedObjectContext {
  /**
   * Create a {@link DurablePromise} for the given key.
   *
   * <p>You can use this feature to implement interaction between different workflow handlers, e.g.
   * to send a signal from a shared handler to the workflow handler.
   *
   * @return the {@link DurablePromise}.
   * @see DurablePromise
   */
  <T> DurablePromise<T> promise(DurablePromiseKey<T> key);

  /**
   * Create a new {@link DurablePromiseHandle} for the provided key. You can use it to {@link
   * DurablePromiseHandle#resolve(Object)} or {@link DurablePromiseHandle#reject(String)} the given
   * {@link DurablePromise}.
   *
   * @see DurablePromise
   */
  <T> DurablePromiseHandle<T> promiseHandle(DurablePromiseKey<T> key);
}
