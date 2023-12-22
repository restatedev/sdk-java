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
import com.google.protobuf.MessageLite;
import dev.restate.sdk.common.TerminalException;
import io.grpc.Context;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Internal interface to access Restate functionalities. Users can use the ad-hoc RestateContext
 * interfaces provided by the various implementations.
 *
 * <p>When using executor switching wrappers, the method's {@code callback} will be executed in the
 * state machine executor.
 */
public interface Syscalls {

  Context.Key<Syscalls> SYSCALLS_KEY = Context.key("restate.dev/syscalls");

  /** Retrieves the current context. */
  static Syscalls current() {
    return Objects.requireNonNull(
        SYSCALLS_KEY.get(),
        "Syscalls MUST be non-null. "
            + "Make sure you're creating the RestateContext within the same thread/executor where the method handler is executed. "
            + "Current thread: "
            + Thread.currentThread().getName());
  }

  /**
   * @return true if it's inside a side effect block.
   */
  boolean isInsideSideEffect();

  // ----- IO
  // Note: These are not supposed to be exposed to RestateContext, but they should be used through
  // gRPC APIs.

  <T extends MessageLite> void pollInput(
      Function<ByteString, T> mapper, SyscallCallback<DeferredResult<T>> callback);

  <T extends MessageLite> void writeOutput(T value, SyscallCallback<Void> callback);

  void writeOutput(TerminalException exception, SyscallCallback<Void> callback);

  // ----- State

  void get(String name, SyscallCallback<DeferredResult<ByteString>> callback);

  void clear(String name, SyscallCallback<Void> callback);

  void set(String name, ByteString value, SyscallCallback<Void> callback);

  // ----- Syscalls

  void sleep(Duration duration, SyscallCallback<DeferredResult<Void>> callback);

  <T, R> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallCallback<DeferredResult<R>> callback);

  <T> void backgroundCall(
      MethodDescriptor<T, ?> methodDescriptor,
      T parameter,
      @Nullable Duration delay,
      SyscallCallback<Void> requestCallback);

  void enterSideEffectBlock(EnterSideEffectSyscallCallback callback);

  void exitSideEffectBlock(ByteString toWrite, ExitSideEffectSyscallCallback callback);

  void exitSideEffectBlockWithTerminalException(
      TerminalException toWrite, ExitSideEffectSyscallCallback callback);

  void awakeable(SyscallCallback<Map.Entry<String, DeferredResult<ByteString>>> callback);

  void resolveAwakeable(String id, ByteString payload, SyscallCallback<Void> requestCallback);

  void rejectAwakeable(String id, String reason, SyscallCallback<Void> requestCallback);

  void fail(Throwable cause);

  // ----- Deferred

  <T> void resolveDeferred(DeferredResult<T> deferredToResolve, SyscallCallback<Void> callback);

  DeferredResult<Integer> createAnyDeferred(List<DeferredResult<?>> children);

  DeferredResult<Void> createAllDeferred(List<DeferredResult<?>> children);
}
