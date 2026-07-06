// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Canonical state-machine contract driving a single Restate invocation.
 *
 * <p>This is shaped after the {@code restate-sdk-shared-core} VM trait.
 *
 * <p>It is implemented twice, selected at runtime by {@link StateMachineFactory}:
 *
 * <ul>
 *   <li>a Panama/FFM implementation calling the native {@code restate-sdk-shared-core} library,
 *       used on JDK 23+ — the path that supports the latest Restate features;
 *   <li>a pure-Java implementation (the legacy state machine) used on JDK &lt; 23, or when the
 *       native library is unavailable/disabled — a deprecated fallback that will be removed in a
 *       future release.
 * </ul>
 *
 * <p>Instances of this interface are not thread-safe.
 */
public interface StateMachine extends AutoCloseable {

  // --- Response metadata

  String getResponseContentType();

  // --- Input / output
  //
  // Imperative replacement for main's Flow.Processor + waitForReady/onNextEvent: bytes are fed in
  // with notifyInput, drained out with takeOutput, and isReadyToExecute gates handler start.

  /** Feed the next chunk of wire input. */
  void notifyInput(Slice bytes);

  void notifyInputClosed();

  void notifyError(Throwable throwable);

  /**
   * Drain the next chunk of serialized output ready for the wire, or {@link Slice#EMPTY} when
   * nothing is buffered.
   */
  Slice takeOutput();

  boolean isReadyToExecute();

  // --- Async results

  sealed interface AwaitResult {
    AwaitResult ANY_COMPLETED = new AnyCompleted();
    AwaitResult WAIT_EXTERNAL_PROGRESS = new WaitExternalProgress();
    AwaitResult CANCEL_SIGNAL_RECEIVED = new CancelSignalReceived();

    record AnyCompleted() implements AwaitResult {}

    record WaitExternalProgress() implements AwaitResult {}

    record ExecuteRun(int handle) implements AwaitResult {}

    record CancelSignalReceived() implements AwaitResult {}
  }

  /**
   * Make progress on the still-uncompleted await tree rooted at {@code future}, or return {@code
   * null} when nothing is left to await (every node already resolved).
   *
   * <p>On suspension this does not return a value: it sneaky-throws {@code
   * AbortedExecutionException}, which should be treated as a clean abort of the user code, not a
   * failure to re-report.
   */
   AwaitResult doAwait(UnresolvedFuture future);

  sealed interface NotificationValue {

    record Empty() implements NotificationValue {
      public static Empty INSTANCE = new Empty();
    }

    record Success(Slice slice) implements NotificationValue {}

    record Failure(TerminalException exception) implements NotificationValue {}

    record StateKeys(List<String> stateKeys) implements NotificationValue {}

    record InvocationId(String invocationId) implements NotificationValue {}
  }

  @Nullable NotificationValue takeNotification(int handle);

  // --- Commands. The int return value is the handle of the operation.

  record Input(
      String invocationId,
      String key,
      List<String[]> headers,
      Slice input,
      long randomSeed,
      // V7 optionals, propagated from the native core; null when absent. Surfaced to the public API
      // separately at a later point.
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable String idempotencyKey) {
    public Map<String, String> headersAsMap() {
      Map<String, String> orderedHeaders = new LinkedHashMap<>();
      if (this.headers() != null) {
        for (var e : this.headers()) orderedHeaders.put(e[0], e[1]);
      }
      return Collections.unmodifiableMap(orderedHeaders);
    }
  }

  Input input();

  int stateGet(String key);

  int stateGetKeys();

  void stateSet(String key, Slice bytes);

  void stateClear(String key);

  void stateClearAll();

  int sleep(Duration duration, @Nullable String name);

  record CallHandle(int invocationIdHandle, int resultHandle) {}

  CallHandle call(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers);

  int send(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
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

  record RunResultHandle(boolean replayed, int handle) {}

  RunResultHandle run(String name);

  void proposeRunCompletion(int handle, Slice value);

  void proposeRunCompletion(int handle, TerminalException terminalException);

  void proposeRunCompletion(
      int handle, Throwable exception, Duration attemptDuration, @Nullable RetryPolicy retryPolicy);

  void cancelInvocation(String targetInvocationId);

  int attachInvocation(String invocationId);

  int getInvocationOutput(String invocationId);

  void writeOutput(Slice value);

  void writeOutput(TerminalException exception);

  void end();

  @Override
  void close();

  // --- Introspection

  enum InvocationState {
    WAITING_START,
    REPLAYING,
    PROCESSING,
    CLOSED
  }

  InvocationState state();
}
