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
import dev.restate.sdk.endpoint.definition.HandlerContext;
import java.util.concurrent.Executor;

/**
 * {@link DurableFuture} returned by a call to another service.
 *
 * <p>You can retrieve the call invocation id using {@link #invocationId()}, and you can cancel the
 * invocation using {@link #cancel()}.
 */
public final class CallDurableFuture<T> extends DurableFuture<T> {

  private final HandlerContext context;
  private final AsyncResult<T> asyncResult;
  private final DurableFuture<String> invocationIdDurableFuture;

  CallDurableFuture(
      HandlerContext context,
      AsyncResult<T> callAsyncResult,
      DurableFuture<String> invocationIdDurableFuture) {
    this.context = context;
    this.asyncResult = callAsyncResult;
    this.invocationIdDurableFuture = invocationIdDurableFuture;
  }

  /**
   * @return the invocation id of this call.
   */
  public String invocationId() {
    return this.invocationIdDurableFuture.await();
  }

  /** Cancel this invocation */
  public void cancel() {
    Util.awaitCompletableFuture(context.cancelInvocation(invocationId()));
  }

  @Override
  protected AsyncResult<T> asyncResult() {
    return asyncResult;
  }

  @Override
  protected Executor serviceExecutor() {
    return invocationIdDurableFuture.serviceExecutor();
  }
}
