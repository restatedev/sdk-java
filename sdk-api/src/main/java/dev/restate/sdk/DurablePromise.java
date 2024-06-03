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
import dev.restate.sdk.common.Output;

/**
 * A {@link DurablePromise} is a durable, distributed version of a {@link
 * java.util.concurrent.CompletableFuture}. Restate keeps track of the {@link DurablePromise} across
 * restarts/failures.
 *
 * <p>You can use this feature to implement interaction between different workflow handlers, e.g. to
 * send a signal from a shared handler to the workflow handler.
 *
 * <p>Use {@link SharedWorkflowContext#promiseHandle(DurablePromiseKey)} to complete a durable
 * promise, either by {@link DurablePromiseHandle#resolve(Object)} or {@link
 * DurablePromiseHandle#reject(String)}.
 *
 * <p>A {@link DurablePromise} is tied to a single workflow execution and can only be resolved or
 * rejected while the workflow run is still ongoing. Once the workflow is cleaned up, all its
 * associated promises with their completions will be cleaned up as well.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 */
public interface DurablePromise<T> {
  /**
   * @return the awaitable to await the promise on.
   */
  Awaitable<T> awaitable();

  /**
   * @return the value, if already present, otherwise returns an empty optional.
   */
  Output<T> peek();
}
