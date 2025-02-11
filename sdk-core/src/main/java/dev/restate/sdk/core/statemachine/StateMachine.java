// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.core.EndpointRequestHandler;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.types.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.jspecify.annotations.Nullable;

/**
 * More or less same as the <a
 * href="https://github.com/restatedev/sdk-shared-core/blob/main/src/lib.rs">VM trait</a>
 */
public interface StateMachine extends Flow.Processor<Slice, Slice> {

  static StateMachine init(
      HeadersAccessor headersAccessor,
      EndpointRequestHandler.LoggingContextSetter loggingContextSetter) {
    return new StateMachineImpl(headersAccessor, loggingContextSetter);
  }

  // --- Response metadata

  String getResponseContentType();

  // --- Execution starting point

  CompletableFuture<Void> waitForReady();

  // --- Await next input

  CompletableFuture<Void> waitNextInputSignal();

  // --- Async results

  sealed interface DoProgressResponse {
    record AnyCompleted() implements DoProgressResponse {
      static AnyCompleted INSTANCE = new AnyCompleted();
    }

    record ReadFromInput() implements DoProgressResponse {
      static ReadFromInput INSTANCE = new ReadFromInput();
    }

    record ExecuteRun(int handle) implements DoProgressResponse {}

    record WaitingPendingRun() implements DoProgressResponse {
      static WaitingPendingRun INSTANCE = new WaitingPendingRun();
    }
  }

  DoProgressResponse doProgress(List<Integer> anyHandle);

  boolean isCompleted(int handle);

  Optional<NotificationValue> takeNotification(int handle);

  // --- Commands. The int return value is the handle of the operation.

  record Input(
      InvocationId invocationId, Slice body, Map<String, String> headers, @Nullable String key) {}

  @Nullable Input input();

  int stateGet(String key);

  int stateGetKeys();

  void stateSet(String key, Slice bytes);

  void stateClear(String key);

  void stateClearAll();

  int sleep(Duration duration, String name);

  record CallHandle(int invocationIdHandle, int resultHandle) {}

  CallHandle call(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable List<Map.Entry<String, String>> headers);

  int send(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable List<Map.Entry<String, String>> headers,
      @Nullable Duration delay);

  record Awakeable(String awakeableId, int handle) {}

  Awakeable awakeable();

  void completeAwakeable(String awakeableId, Slice value);

  void completeAwakeable(String awakeableId, TerminalException exception);

  int createSignalHandle(String signalName);

  void completeSignal(String targetInvocationId, String signalName, Slice value);

  void completeSignal(String targetInvocationId, String signalName, TerminalException exception);

  int promiseGet(String key);

  int promisePeek(String key);

  int promiseComplete(String key, Slice value);

  int promiseComplete(String key, TerminalException exception);

  int run(String name);

  void proposeRunCompletion(int handle, Slice value);

  void proposeRunCompletion(
      int handle, Throwable exception, Duration attemptDuration, RetryPolicy retryPolicy);

  void cancelInvocation(String targetInvocationId);

  void writeOutput(Slice value);

  void writeOutput(TerminalException exception);

  void end();

  // -- Introspection

  InvocationState state();
}
