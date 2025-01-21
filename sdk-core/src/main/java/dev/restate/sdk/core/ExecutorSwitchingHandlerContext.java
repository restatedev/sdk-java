// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.types.Request;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.Target;
import dev.restate.sdk.types.TerminalException;
import dev.restate.sdk.endpoint.AsyncResult;
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

class ExecutorSwitchingHandlerContext implements HandlerContextInternal {

  private final HandlerContextInternal syscalls;
  private final Executor syscallsExecutor;

  ExecutorSwitchingHandlerContext(HandlerContextInternal syscalls, Executor syscallsExecutor) {
    this.syscalls = syscalls;
    this.syscallsExecutor = syscallsExecutor;
  }

  @Override
  public void writeOutput(ByteBuffer value, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.writeOutput(value, callback));
  }

  @Override
  public void writeOutput(TerminalException throwable, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.writeOutput(throwable, callback));
  }

  @Override
  public void get(String name, SyscallCallback<AsyncResult<ByteBuffer>> callback) {
    syscallsExecutor.execute(() -> syscalls.get(name, callback));
  }

  @Override
  public void getKeys(SyscallCallback<AsyncResult<Collection<String>>> callback) {
    syscallsExecutor.execute(() -> syscalls.getKeys(callback));
  }

  @Override
  public void clear(String name, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.clear(name, callback));
  }

  @Override
  public void clearAll(SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.clearAll(callback));
  }

  @Override
  public void set(String name, ByteBuffer value, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.set(name, value, callback));
  }

  @Override
  public void sleep(Duration duration, SyscallCallback<AsyncResult<Void>> callback) {
    syscallsExecutor.execute(() -> syscalls.sleep(duration, callback));
  }

  @Override
  public void call(
      Target target, ByteBuffer parameter, SyscallCallback<AsyncResult<ByteBuffer>> callback) {
    syscallsExecutor.execute(() -> syscalls.call(target, parameter, callback));
  }

  @Override
  public void send(
      Target target,
      ByteBuffer parameter,
      @Nullable Duration delay,
      SyscallCallback<Void> requestCallback) {
    syscallsExecutor.execute(() -> syscalls.send(target, parameter, delay, requestCallback));
  }

  @Override
  public void enterSideEffectBlock(String name, EnterSideEffectSyscallCallback callback) {
    syscallsExecutor.execute(() -> syscalls.enterSideEffectBlock(name, callback));
  }

  @Override
  public void exitSideEffectBlock(ByteBuffer toWrite, ExitSideEffectSyscallCallback callback) {
    syscallsExecutor.execute(() -> syscalls.exitSideEffectBlock(toWrite, callback));
  }

  @Override
  public void exitSideEffectBlockWithTerminalException(
      TerminalException toWrite, ExitSideEffectSyscallCallback callback) {
    syscallsExecutor.execute(
        () -> syscalls.exitSideEffectBlockWithTerminalException(toWrite, callback));
  }

  @Override
  public void exitSideEffectBlockWithException(
      Throwable toWrite,
      @Nullable RetryPolicy retryPolicy,
      ExitSideEffectSyscallCallback callback) {
    syscallsExecutor.execute(
        () -> syscalls.exitSideEffectBlockWithException(toWrite, retryPolicy, callback));
  }

  @Override
  public void awakeable(SyscallCallback<Map.Entry<String, AsyncResult<ByteBuffer>>> callback) {
    syscallsExecutor.execute(() -> syscalls.awakeable(callback));
  }

  @Override
  public void resolveAwakeable(
      String id, ByteBuffer payload, SyscallCallback<Void> requestCallback) {
    syscallsExecutor.execute(() -> syscalls.resolveAwakeable(id, payload, requestCallback));
  }

  @Override
  public void rejectAwakeable(String id, String reason, SyscallCallback<Void> requestCallback) {
    syscallsExecutor.execute(() -> syscalls.rejectAwakeable(id, reason, requestCallback));
  }

  @Override
  public void promise(String key, SyscallCallback<AsyncResult<ByteBuffer>> callback) {
    syscallsExecutor.execute(() -> syscalls.promise(key, callback));
  }

  @Override
  public void peekPromise(String key, SyscallCallback<AsyncResult<ByteBuffer>> callback) {
    syscallsExecutor.execute(() -> syscalls.peekPromise(key, callback));
  }

  @Override
  public void resolvePromise(
      String key, ByteBuffer payload, SyscallCallback<AsyncResult<Void>> callback) {
    syscallsExecutor.execute(() -> syscalls.resolvePromise(key, payload, callback));
  }

  @Override
  public void rejectPromise(String key, String reason, SyscallCallback<AsyncResult<Void>> callback) {
    syscallsExecutor.execute(() -> syscalls.rejectPromise(key, reason, callback));
  }

  @Override
  public <T> void resolveDeferred(AsyncResult<T> asyncResultToResolve, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.resolveDeferred(asyncResultToResolve, callback));
  }

  @Override
  public String getFullyQualifiedMethodName() {
    // We can read this from another thread
    return syscalls.getFullyQualifiedMethodName();
  }

  @Override
  public InvocationState getInvocationState() {
    // We can read this from another thread
    return syscalls.getInvocationState();
  }

  @Override
  public String objectKey() {
    // This is immutable once set
    return syscalls.objectKey();
  }

  @Override
  public Request request() {
    // This is immutable once set
    return syscalls.request();
  }

  @Override
  public boolean isInsideSideEffect() {
    // We can read this from another thread
    return syscalls.isInsideSideEffect();
  }

  @Override
  public void close() {
    syscallsExecutor.execute(syscalls::close);
  }

  @Override
  public void fail(Throwable cause) {
    syscallsExecutor.execute(() -> syscalls.fail(cause));
  }
}
