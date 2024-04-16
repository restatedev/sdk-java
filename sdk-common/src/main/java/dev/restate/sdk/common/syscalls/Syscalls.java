// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import com.google.protobuf.ByteString;
import dev.restate.sdk.common.Request;
import dev.restate.sdk.common.Target;
import dev.restate.sdk.common.TerminalException;
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

  void writeOutput(ByteString value, SyscallCallback<Void> callback);

  void writeOutput(TerminalException exception, SyscallCallback<Void> callback);

  // ----- State

  void get(String name, SyscallCallback<Deferred<ByteString>> callback);

  void getKeys(SyscallCallback<Deferred<Collection<String>>> callback);

  void clear(String name, SyscallCallback<Void> callback);

  void clearAll(SyscallCallback<Void> callback);

  void set(String name, ByteString value, SyscallCallback<Void> callback);

  // ----- Syscalls

  void sleep(Duration duration, SyscallCallback<Deferred<Void>> callback);

  void call(Target target, ByteString parameter, SyscallCallback<Deferred<ByteString>> callback);

  void send(
      Target target,
      ByteString parameter,
      @Nullable Duration delay,
      SyscallCallback<Void> requestCallback);

  void enterSideEffectBlock(@Nullable String name, EnterSideEffectSyscallCallback callback);

  void exitSideEffectBlock(ByteString toWrite, ExitSideEffectSyscallCallback callback);

  void exitSideEffectBlockWithTerminalException(
      TerminalException toWrite, ExitSideEffectSyscallCallback callback);

  void awakeable(SyscallCallback<Map.Entry<String, Deferred<ByteString>>> callback);

  void resolveAwakeable(String id, ByteString payload, SyscallCallback<Void> requestCallback);

  void rejectAwakeable(String id, String reason, SyscallCallback<Void> requestCallback);

  void fail(Throwable cause);

  // ----- Deferred

  <T> void resolveDeferred(Deferred<T> deferredToResolve, SyscallCallback<Void> callback);

  Deferred<Integer> createAnyDeferred(List<Deferred<?>> children);

  Deferred<Void> createAllDeferred(List<Deferred<?>> children);
}
