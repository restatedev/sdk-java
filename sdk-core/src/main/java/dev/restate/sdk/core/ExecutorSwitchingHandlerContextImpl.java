// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.Output;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.common.*;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceType;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

final class ExecutorSwitchingHandlerContextImpl extends HandlerContextImpl {

  private final Executor coreExecutor;

  ExecutorSwitchingHandlerContextImpl(
      StateMachine vm,
      ExternalProgressChannel externalProgressChannel,
      Consumer<Slice> outputSink,
      String serviceName,
      String handlerName,
      ServiceType serviceType,
      @Nullable HandlerType handlerType,
      Context otelContext,
      HeadersAccessor attemptHeaders,
      StateMachine.Input input,
      Executor coreExecutor) {
    super(
        vm,
        externalProgressChannel,
        outputSink,
        serviceName,
        handlerName,
        serviceType,
        handlerType,
        otelContext,
        attemptHeaders,
        input);
    this.coreExecutor = coreExecutor;
  }

  @Override
  public CompletableFuture<AsyncResult<Optional<Slice>>> get(String name) {
    return CompletableFuture.supplyAsync(() -> super.get(name), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<AsyncResult<Collection<String>>> getKeys() {
    return CompletableFuture.supplyAsync(super::getKeys, coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<Void> clear(String name) {
    return CompletableFuture.supplyAsync(() -> super.clear(name), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<Void> clearAll() {
    return CompletableFuture.supplyAsync(super::clearAll, coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<Void> set(String name, Slice value) {
    return CompletableFuture.supplyAsync(() -> super.set(name, value), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> timer(Duration duration, String name) {
    return CompletableFuture.supplyAsync(() -> super.timer(duration, name), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<CallResult> call(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers) {
    return CompletableFuture.supplyAsync(
            () -> super.call(target, parameter, idempotencyKey, limitKey, headers), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<AsyncResult<String>> send(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    return CompletableFuture.supplyAsync(
            () -> super.send(target, parameter, idempotencyKey, limitKey, headers, delay),
            coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> submitRun(
      @Nullable String name, Consumer<RunCompleter> closure) {
    return CompletableFuture.supplyAsync(() -> super.submitRun(name, closure), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<Awakeable> awakeable() {
    return CompletableFuture.supplyAsync(super::awakeable, coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<Void> resolveAwakeable(String id, Slice payload) {
    return CompletableFuture.supplyAsync(() -> super.resolveAwakeable(id, payload), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<Void> rejectAwakeable(String id, TerminalException reason) {
    return CompletableFuture.supplyAsync(() -> super.rejectAwakeable(id, reason), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> promise(String key) {
    return CompletableFuture.supplyAsync(() -> super.promise(key), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> peekPromise(String key) {
    return CompletableFuture.supplyAsync(() -> super.peekPromise(key), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> resolvePromise(String key, Slice payload) {
    return CompletableFuture.supplyAsync(() -> super.resolvePromise(key, payload), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> rejectPromise(String key, TerminalException reason) {
    return CompletableFuture.supplyAsync(() -> super.rejectPromise(key, reason), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> signal(String name) {
    return CompletableFuture.supplyAsync(() -> super.signal(name), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<Void> resolveSignal(String invocationId, String name, Slice payload) {
    return CompletableFuture.supplyAsync(
            () -> super.resolveSignal(invocationId, name, payload), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public CompletableFuture<Void> rejectSignal(
      String invocationId, String name, TerminalException reason) {
    return CompletableFuture.supplyAsync(
            () -> super.rejectSignal(invocationId, name, reason), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public void proposeRunSuccess(int runHandle, Slice toWrite) {
    coreExecutor.execute(() -> super.proposeRunSuccess(runHandle, toWrite));
  }

  @Override
  public void proposeRunFailure(
      int runHandle,
      Throwable toWrite,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    coreExecutor.execute(
        () -> super.proposeRunFailure(runHandle, toWrite, attemptDuration, retryPolicy));
  }

  @Override
  public void pollAsyncResult(AsyncResults.AsyncResultInternal<?> asyncResult) {
    coreExecutor.execute(() -> super.pollAsyncResult(asyncResult));
  }

  @Deprecated
  @Override
  public CompletableFuture<Void> writeOutput(Slice value) {
    return CompletableFuture.supplyAsync(() -> super.writeOutput(value), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Deprecated
  @Override
  public CompletableFuture<Void> writeOutput(TerminalException throwable) {
    return CompletableFuture.supplyAsync(() -> super.writeOutput(throwable), coreExecutor)
        .thenCompose(Function.identity());
  }

  @Override
  public void fail(Throwable cause) {
    coreExecutor.execute(() -> super.fail(cause));
  }
}
