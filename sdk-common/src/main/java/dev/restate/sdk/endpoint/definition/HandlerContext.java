// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import dev.restate.common.Output;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.types.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Internal interface to access Restate functionalities. Users can use the ad-hoc RestateContext
 * interfaces provided by the various implementations.
 */
public interface HandlerContext {

  String objectKey();

  Request request();

  // ----- IO
  // Note: These are not supposed to be exposed in the user's facing Context API.

  CompletableFuture<Void> writeOutput(Slice value);

  CompletableFuture<Void> writeOutput(TerminalException exception);

  // ----- State

  CompletableFuture<AsyncResult<Optional<Slice>>> get(String name);

  CompletableFuture<AsyncResult<Collection<String>>> getKeys();

  CompletableFuture<Void> clear(String name);

  CompletableFuture<Void> clearAll();

  CompletableFuture<Void> set(String name, Slice value);

  // ----- Syscalls

  CompletableFuture<AsyncResult<Void>> timer(Duration duration, @Nullable String name);

  record CallResult(
      AsyncResult<String> invocationIdAsyncResult, AsyncResult<Slice> callAsyncResult) {}

  CompletableFuture<CallResult> call(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers);

  CompletableFuture<AsyncResult<String>> send(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay);

  interface RunCompleter {
    void proposeSuccess(Slice toWrite);

    void proposeFailure(Throwable toWrite, @Nullable RetryPolicy retryPolicy);
  }

  CompletableFuture<AsyncResult<Slice>> submitRun(
      @Nullable String name, Consumer<RunCompleter> closure);

  record Awakeable(String id, AsyncResult<Slice> asyncResult) {}

  CompletableFuture<Awakeable> awakeable();

  CompletableFuture<Void> resolveAwakeable(String id, Slice payload);

  CompletableFuture<Void> rejectAwakeable(String id, TerminalException reason);

  CompletableFuture<AsyncResult<Slice>> promise(String key);

  CompletableFuture<AsyncResult<Output<Slice>>> peekPromise(String key);

  CompletableFuture<AsyncResult<Void>> resolvePromise(String key, Slice payload);

  CompletableFuture<AsyncResult<Void>> rejectPromise(String key, TerminalException reason);

  void fail(Throwable cause);

  // ----- Deferred

  AsyncResult<Integer> createAnyAsyncResult(List<AsyncResult<?>> children);

  AsyncResult<Void> createAllAsyncResult(List<AsyncResult<?>> children);
}
