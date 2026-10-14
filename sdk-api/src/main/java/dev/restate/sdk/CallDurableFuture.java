// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingFunction;
import dev.restate.sdk.endpoint.definition.HandlerContext;

/**
 * {@link DurableFuture} returned by a call to another service.
 *
 * <p>You can retrieve the call invocation id using {@link #invocationId()}, and you can cancel the
 * invocation using {@link #cancel()}.
 */
public final class CallDurableFuture<T> extends DurableFutureImpl.Delegating<T> {

  private final DurableFuture<String> invocationIdDurableFuture;

  CallDurableFuture(
      HandlerContext handlerContext,
      HandlerContext.CallHandles callHandles,
      ThrowingFunction<Slice, T> responseDeserializer) {
    super(
        new DurableFutureImpl.Single<>(
            handlerContext, callHandles.resultHandle(), responseDeserializer));
    this.invocationIdDurableFuture =
        DurableFutureImpl.single(handlerContext, callHandles.invocationIdHandle());
  }

  /**
   * @return the invocation id of this call.
   */
  public String invocationId() {
    return this.invocationIdDurableFuture.await();
  }

  /** Cancel this invocation */
  public void cancel() {
    Util.awaitCompletableFuture(handlerContext().cancelInvocation(invocationId()));
  }
}
