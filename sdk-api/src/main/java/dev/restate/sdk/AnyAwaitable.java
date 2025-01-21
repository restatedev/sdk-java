// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.endpoint.AsyncResult;
import dev.restate.sdk.endpoint.HandlerContext;
import dev.restate.sdk.endpoint.Result;

import java.util.List;

public final class AnyAwaitable extends Awaitable.MappedAwaitable<Integer, Object> {

  @SuppressWarnings({"unchecked", "rawtypes"})
  AnyAwaitable(HandlerContext handlerContext, AsyncResult<Integer> asyncResult, List<Awaitable<?>> nested) {
    super(
        new SingleAwaitable<>(handlerContext, asyncResult),
        res ->
            res.isSuccess()
                ? (Result<Object>) nested.get(res.getValue()).awaitResult()
                : (Result) res);
  }

  /** Same as {@link #await()}, but returns the index. */
  public int awaitIndex() {
    // This cast is safe b/c of the constructor
    return (int) Util.blockOnResolve(this.handlerContext, this.deferred());
  }
}
