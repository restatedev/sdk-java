// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import dev.restate.common.Slice;
import dev.restate.serde.Serde;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public interface HandlerRunner<REQ, RES> {
  /**
   * Thread local to store {@link HandlerContext}.
   *
   * <p>Implementations of {@link HandlerRunner} should correctly propagate this thread local in
   * order for logging to work correctly. Could be improved if ScopedContext <a
   * href="https://github.com/apache/logging-log4j2/pull/2438">will ever be introduced in
   * log4j2</a>.
   */
  ThreadLocal<HandlerContext> HANDLER_CONTEXT_THREAD_LOCAL = new ThreadLocal<>();

  /** Marker interface of runner options. */
  interface Options {}

  CompletableFuture<Slice> run(
      HandlerContext handlerContext,
      Serde<REQ> requestSerde,
      Serde<RES> responseSerde,
      AtomicReference<Runnable> onClosedInvocationStreamHook);
}
