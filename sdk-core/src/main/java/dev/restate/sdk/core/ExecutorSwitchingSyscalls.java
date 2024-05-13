// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.ByteString;
import dev.restate.sdk.common.Request;
import dev.restate.sdk.common.Target;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.Deferred;
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

class ExecutorSwitchingSyscalls implements SyscallsInternal {

  private final SyscallsInternal syscalls;
  private final Executor syscallsExecutor;

  ExecutorSwitchingSyscalls(SyscallsInternal syscalls, Executor syscallsExecutor) {
    this.syscalls = syscalls;
    this.syscallsExecutor = syscallsExecutor;
  }

  @Override
  public void writeOutput(ByteString value, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.writeOutput(value, callback));
  }

  @Override
  public void writeOutput(TerminalException throwable, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.writeOutput(throwable, callback));
  }

  @Override
  public void get(String name, SyscallCallback<Deferred<ByteString>> callback) {
    syscallsExecutor.execute(() -> syscalls.get(name, callback));
  }

  @Override
  public void getKeys(SyscallCallback<Deferred<Collection<String>>> callback) {
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
  public void set(String name, ByteString value, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.set(name, value, callback));
  }

  @Override
  public void sleep(Duration duration, SyscallCallback<Deferred<Void>> callback) {
    syscallsExecutor.execute(() -> syscalls.sleep(duration, callback));
  }

  @Override
  public void call(
      Target target, ByteString parameter, SyscallCallback<Deferred<ByteString>> callback) {
    syscallsExecutor.execute(() -> syscalls.call(target, parameter, callback));
  }

  @Override
  public void send(
      Target target,
      ByteString parameter,
      @Nullable Duration delay,
      SyscallCallback<Void> requestCallback) {
    syscallsExecutor.execute(() -> syscalls.send(target, parameter, delay, requestCallback));
  }

  @Override
  public void enterSideEffectBlock(String name, EnterSideEffectSyscallCallback callback) {
    syscallsExecutor.execute(() -> syscalls.enterSideEffectBlock(name, callback));
  }

  @Override
  public void exitSideEffectBlock(ByteString toWrite, ExitSideEffectSyscallCallback callback) {
    syscallsExecutor.execute(() -> syscalls.exitSideEffectBlock(toWrite, callback));
  }

  @Override
  public void exitSideEffectBlockWithTerminalException(
      TerminalException toWrite, ExitSideEffectSyscallCallback callback) {
    syscallsExecutor.execute(
        () -> syscalls.exitSideEffectBlockWithTerminalException(toWrite, callback));
  }

  @Override
  public void awakeable(SyscallCallback<Map.Entry<String, Deferred<ByteString>>> callback) {
    syscallsExecutor.execute(() -> syscalls.awakeable(callback));
  }

  @Override
  public void resolveAwakeable(
      String id, ByteString payload, SyscallCallback<Void> requestCallback) {
    syscallsExecutor.execute(() -> syscalls.resolveAwakeable(id, payload, requestCallback));
  }

  @Override
  public void rejectAwakeable(String id, String reason, SyscallCallback<Void> requestCallback) {
    syscallsExecutor.execute(() -> syscalls.rejectAwakeable(id, reason, requestCallback));
  }

  @Override
  public void promise(String key, SyscallCallback<Deferred<ByteString>> callback) {
    syscallsExecutor.execute(() -> syscalls.promise(key, callback));
  }

  @Override
  public void peekPromise(String key, SyscallCallback<Deferred<ByteString>> callback) {
    syscallsExecutor.execute(() -> syscalls.peekPromise(key, callback));
  }

  @Override
  public void resolvePromise(
      String key, ByteString payload, SyscallCallback<Deferred<Void>> callback) {
    syscallsExecutor.execute(() -> syscalls.resolvePromise(key, payload, callback));
  }

  @Override
  public void rejectPromise(String key, String reason, SyscallCallback<Deferred<Void>> callback) {
    syscallsExecutor.execute(() -> syscalls.rejectPromise(key, reason, callback));
  }

  @Override
  public <T> void resolveDeferred(Deferred<T> deferredToResolve, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.resolveDeferred(deferredToResolve, callback));
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
