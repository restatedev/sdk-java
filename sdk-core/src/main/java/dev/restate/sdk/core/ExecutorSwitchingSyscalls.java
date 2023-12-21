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
import com.google.protobuf.MessageLite;
import dev.restate.sdk.common.InvocationId;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.DeferredResult;
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback;
import dev.restate.sdk.common.syscalls.SyscallCallback;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

class ExecutorSwitchingSyscalls implements SyscallsInternal {

  private final SyscallsInternal syscalls;
  private final Executor syscallsExecutor;

  ExecutorSwitchingSyscalls(SyscallsInternal syscalls, Executor syscallsExecutor) {
    this.syscalls = syscalls;
    this.syscallsExecutor = syscallsExecutor;
  }

  @Override
  public <T extends MessageLite> void pollInput(
      Function<ByteString, T> mapper, SyscallCallback<DeferredResult<T>> callback) {
    syscallsExecutor.execute(() -> syscalls.pollInput(mapper, callback));
  }

  @Override
  public <T extends MessageLite> void writeOutput(T value, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.writeOutput(value, callback));
  }

  @Override
  public void writeOutput(TerminalException throwable, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.writeOutput(throwable, callback));
  }

  @Override
  public void get(String name, SyscallCallback<DeferredResult<ByteString>> callback) {
    syscallsExecutor.execute(() -> syscalls.get(name, callback));
  }

  @Override
  public void clear(String name, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.clear(name, callback));
  }

  @Override
  public void set(String name, ByteString value, SyscallCallback<Void> callback) {
    syscallsExecutor.execute(() -> syscalls.set(name, value, callback));
  }

  @Override
  public void sleep(Duration duration, SyscallCallback<DeferredResult<Void>> callback) {
    syscallsExecutor.execute(() -> syscalls.sleep(duration, callback));
  }

  @Override
  public <T, R> void call(
      MethodDescriptor<T, R> methodDescriptor,
      T parameter,
      SyscallCallback<DeferredResult<R>> callback) {
    syscallsExecutor.execute(() -> syscalls.call(methodDescriptor, parameter, callback));
  }

  @Override
  public <T> void backgroundCall(
      MethodDescriptor<T, ?> methodDescriptor,
      T parameter,
      Duration delay,
      SyscallCallback<Void> requestCallback) {
    syscallsExecutor.execute(
        () -> syscalls.backgroundCall(methodDescriptor, parameter, delay, requestCallback));
  }

  @Override
  public void enterSideEffectBlock(EnterSideEffectSyscallCallback callback) {
    syscallsExecutor.execute(() -> syscalls.enterSideEffectBlock(callback));
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
  public void awakeable(SyscallCallback<Map.Entry<String, DeferredResult<ByteString>>> callback) {
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
  public <T> void resolveDeferred(
      DeferredResult<T> deferredToResolve, SyscallCallback<Void> callback) {
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
  public InvocationId invocationId() {
    // This is immutable once set
    return syscalls.invocationId();
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
