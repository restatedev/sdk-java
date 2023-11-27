// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.syscalls.DeferredResult;
import dev.restate.sdk.common.syscalls.Syscalls;
import java.util.List;

public final class AnyAwaitable extends Awaitable<Object> {

  AnyAwaitable(
      Syscalls syscalls, DeferredResult<Integer> deferredResult, List<Awaitable<?>> mappers) {
    super(syscalls, deferredResult, i -> mappers.get(i).await());
  }

  /** Same as {@link #await()}, but returns the index. */
  public int awaitIndex() {
    // This cast is safe b/c of the constructor
    return (int) Util.blockOnResolve(this.syscalls, this.resultHolder.getDeferredResult());
  }
}
