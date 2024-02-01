// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.syscalls.Deferred;
import dev.restate.sdk.common.syscalls.Result;
import dev.restate.sdk.common.syscalls.Syscalls;
import java.util.List;

public final class AnyAwaitable extends Awaitable.MappedAwaitable<Integer, Object> {

  @SuppressWarnings({"unchecked", "rawtypes"})
  AnyAwaitable(Syscalls syscalls, Deferred<Integer> deferred, List<Awaitable<?>> nested) {
    super(
        new SingleAwaitable<>(syscalls, deferred),
        res ->
            res.isSuccess()
                ? (Result<Object>) nested.get(res.getValue()).awaitResult()
                : (Result) res);
  }

  /** Same as {@link #await()}, but returns the index. */
  public int awaitIndex() {
    // This cast is safe b/c of the constructor
    return (int) Util.blockOnResolve(this.syscalls, this.deferred());
  }
}
