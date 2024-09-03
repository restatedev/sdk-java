// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import dev.restate.sdk.common.Request;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.Target;
import dev.restate.sdk.common.TerminalException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Internal interface to access Restate functionalities. Users can use the ad-hoc RestateContext
 * interfaces provided by the various implementations.
 *
 * <p>When using executor switching wrappers, the method's {@code callback} will be executed in the
 * state machine executor.
 */
public interface Syscalls {

  String objectKey();

  Request request();

  /**
   * @return true if it's inside a side effect block.
   */
  boolean isInsideSideEffect();

  // ----- IO
  // Note: These are not supposed to be exposed to RestateContext, but they should be used through
  // gRPC APIs.

  void writeOutput(ByteBuffer value, SyscallCallback<Void> callback);

  void writeOutput(TerminalException exception, SyscallCallback<Void> callback);

  // ----- State

  void get(String name, SyscallCallback<Deferred<ByteBuffer>> callback);

  void getKeys(SyscallCallback<Deferred<Collection<String>>> callback);

  void clear(String name, SyscallCallback<Void> callback);

  void clearAll(SyscallCallback<Void> callback);

  void set(String name, ByteBuffer value, SyscallCallback<Void> callback);

  // ----- Syscalls

  void sleep(Duration duration, SyscallCallback<Deferred<Void>> callback);

  void call(Target target, ByteBuffer parameter, SyscallCallback<Deferred<ByteBuffer>> callback);

  void send(
      Target target,
      ByteBuffer parameter,
      @Nullable Duration delay,
      SyscallCallback<Void> requestCallback);

  void enterSideEffectBlock(@Nullable String name, EnterSideEffectSyscallCallback callback);

  void exitSideEffectBlock(ByteBuffer toWrite, ExitSideEffectSyscallCallback callback);

  /**
   * @deprecated use {@link #exitSideEffectBlockWithException(Throwable, RetryPolicy,
   *     ExitSideEffectSyscallCallback)} instead.
   */
  @Deprecated(since = "1.1.0", forRemoval = true)
  void exitSideEffectBlockWithTerminalException(
      TerminalException toWrite, ExitSideEffectSyscallCallback callback);

  void exitSideEffectBlockWithException(
      Throwable toWrite, @Nullable RetryPolicy retryPolicy, ExitSideEffectSyscallCallback callback);

  void awakeable(SyscallCallback<Map.Entry<String, Deferred<ByteBuffer>>> callback);

  void resolveAwakeable(String id, ByteBuffer payload, SyscallCallback<Void> requestCallback);

  void rejectAwakeable(String id, String reason, SyscallCallback<Void> requestCallback);

  void promise(String key, SyscallCallback<Deferred<ByteBuffer>> callback);

  void peekPromise(String key, SyscallCallback<Deferred<ByteBuffer>> callback);

  void resolvePromise(String key, ByteBuffer payload, SyscallCallback<Deferred<Void>> callback);

  void rejectPromise(String key, String reason, SyscallCallback<Deferred<Void>> callback);

  void fail(Throwable cause);

  // ----- Deferred

  <T> void resolveDeferred(Deferred<T> deferredToResolve, SyscallCallback<Void> callback);

  Deferred<Integer> createAnyDeferred(List<Deferred<?>> children);

  Deferred<Void> createAllDeferred(List<Deferred<?>> children);
}
