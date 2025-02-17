// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.endpoint.definition.AsyncResult;
import java.util.concurrent.Executor;

/** {@link Awaitable} returned by a call to another service. */
public final class CallAwaitable<T> extends Awaitable<T> {

  private final AsyncResult<T> asyncResult;
  private final Awaitable<String> invocationIdAwaitable;

  CallAwaitable(AsyncResult<T> callAsyncResult, Awaitable<String> invocationIdAwaitable) {
    this.asyncResult = callAsyncResult;
    this.invocationIdAwaitable = invocationIdAwaitable;
  }

  /**
   * @return the unique identifier of this {@link CallAwaitable} instance.
   */
  public String invocationId() {
    return this.invocationIdAwaitable.await();
  }

  @Override
  protected AsyncResult<T> asyncResult() {
    return asyncResult;
  }

  @Override
  protected Executor serviceExecutor() {
    return invocationIdAwaitable.serviceExecutor();
  }
}
