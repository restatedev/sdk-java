// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint;

import dev.restate.sdk.types.Request;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.Target;
import dev.restate.sdk.types.TerminalException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.restate.sdk.types.Slice;
import org.jspecify.annotations.Nullable;

/**
 * Internal interface to access Restate functionalities. Users can use the ad-hoc RestateContext
 * interfaces provided by the various implementations.
 *
 * <p>When using executor switching wrappers, the method's {@code callback} will be executed in the
 * state machine executor.
 */
public interface HandlerContext {

  String objectKey();

  Request request();

  // ----- IO
  // Note: These are not supposed to be exposed in the user's facing Context API.

  CompletableFuture<Void> writeOutput(Slice value);

  CompletableFuture<Void> writeOutput(TerminalException exception);

  // ----- State

  CompletableFuture<AsyncResult<Slice>> get(String name);

  CompletableFuture<AsyncResult<Collection<String>>> getKeys();

  CompletableFuture<Void> clear(String name);

  CompletableFuture<Void> clearAll();

  CompletableFuture<Void> set(String name, Slice value);

  // ----- Syscalls

  CompletableFuture<AsyncResult<Void>> sleep(Duration duration);

  CompletableFuture<AsyncResult<Slice>> call(Target target, ByteBuffer parameter);

  CompletableFuture<Void> send(
      Target target,
      ByteBuffer parameter,
      @Nullable Duration delay);

  CompletableFuture<AsyncResult<Slice>> run(@Nullable String name);

  void proposeRunCompletion(Slice toWrite);

  void proposeRunCompletion(Throwable toWrite, @Nullable RetryPolicy retryPolicy);

  record Awakeable(String id, AsyncResult<Slice> asyncResult) {}

  CompletableFuture<Awakeable> awakeable();

  CompletableFuture<Void> resolveAwakeable(String id, Slice payload);

  CompletableFuture<Void> rejectAwakeable(String id, String reason);

  CompletableFuture<AsyncResult<Slice>> promise(String key);

  CompletableFuture<AsyncResult<Optional<Slice>>> peekPromise(String key);

  CompletableFuture<AsyncResult<Void>> resolvePromise(String key, ByteBuffer payload);

  CompletableFuture<AsyncResult<Void>> rejectPromise(String key, String reason);

  void fail(Throwable cause);

  // ----- Deferred

  AsyncResult<Integer> createAnyDeferred(List<AsyncResult<?>> children);

  AsyncResult<Void> createAllDeferred(List<AsyncResult<?>> children);
}
