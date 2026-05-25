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
import dev.restate.common.function.ThrowingRunnable;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.common.*;
import dev.restate.sdk.core.AsyncResults.AsyncResultInternal;
import dev.restate.sdk.core.sharedcore.StateMachine;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceType;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

class HandlerContextImpl implements HandlerContextInternal {

  private static final Logger LOG = LogManager.getLogger(HandlerContextImpl.class);

  private final StateMachine stateMachine;
  private final ExternalProgressChannel externalProgressChannel;
  private final Consumer<Slice> outputSink;

  private final HandlerRequest handlerRequest;
  private final @Nullable String objectKey;
  private final String fullyQualifiedHandlerName;
  private final ServiceType serviceType;
  private final @Nullable HandlerType handlerType;

  private final HashMap<Integer, Consumer<RunCompleter>> scheduledRuns;

  HandlerContextImpl(
      StateMachine stateMachine,
      ExternalProgressChannel externalProgressChannel,
      Consumer<Slice> outputSink,
      String fullyQualifiedHandlerName,
      ServiceType serviceType,
      @Nullable HandlerType handlerType,
      Context otelContext,
      StateMachine.Input input) {
    this.stateMachine = stateMachine;
    this.externalProgressChannel = externalProgressChannel;
    this.outputSink = outputSink;

    this.handlerRequest =
        new HandlerRequest(
            new InvocationIdImpl(input.invocationId(), input.randomSeed()),
            otelContext,
            Slice.wrap(input.input()),
            input.headersAsMap());
    this.objectKey = input.key() != null && !input.key().isEmpty() ? input.key() : null;
    this.fullyQualifiedHandlerName = fullyQualifiedHandlerName;
    this.serviceType = serviceType;
    this.handlerType = handlerType;
    this.scheduledRuns = new HashMap<>();
  }

  private static void parseSuccessOrFailure(
      StateMachine.NotificationValue s, CompletableFuture<Slice> cf) {
    if (s instanceof StateMachine.NotificationValue.Success success) {
      cf.complete(success.slice());
    } else if (s instanceof StateMachine.NotificationValue.Failure failure) {
      cf.completeExceptionally(failure.terminalException());
    } else {
      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
    }
  }

  private static void parseEmptyOrSuccessOrFailure(
      StateMachine.NotificationValue s, CompletableFuture<Output<Slice>> cf) {
    if (s instanceof StateMachine.NotificationValue.Void) {
      cf.complete(Output.notReady());
    } else if (s instanceof StateMachine.NotificationValue.Success success) {
      cf.complete(Output.ready(success.slice()));
    } else if (s instanceof StateMachine.NotificationValue.Failure failure) {
      cf.completeExceptionally(failure.terminalException());
    } else {
      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
    }
  }

  private static void parseEmptyOrFailure(
      StateMachine.NotificationValue s, CompletableFuture<Void> cf) {
    if (s instanceof StateMachine.NotificationValue.Void) {
      cf.complete(null);
    } else if (s instanceof StateMachine.NotificationValue.Failure failure) {
      cf.completeExceptionally(failure.terminalException());
    } else {
      throw ProtocolException.unexpectedNotificationVariant(s.getClass());
    }
  }

  @Override
  public String objectKey() {
    return this.objectKey;
  }

  @Override
  public HandlerRequest request() {
    return this.handlerRequest;
  }

  @Override
  public boolean canReadState() {
    return serviceType == ServiceType.VIRTUAL_OBJECT || serviceType == ServiceType.WORKFLOW;
  }

  @Override
  public boolean canWriteState() {
    return handlerType == HandlerType.EXCLUSIVE || handlerType == HandlerType.WORKFLOW;
  }

  @Override
  public boolean canReadPromises() {
    return serviceType == ServiceType.WORKFLOW;
  }

  @Override
  public boolean canWritePromises() {
    return serviceType == ServiceType.WORKFLOW;
  }

  @Override
  public String getFullyQualifiedMethodName() {
    return this.fullyQualifiedHandlerName;
  }

  @Override
  public InvocationState getInvocationState() {
    return this.stateMachine.state();
  }

  @Override
  public Executor stateMachineExecutor() {
    return Runnable::run;
  }

  @Override
  public CompletableFuture<AsyncResult<Optional<Slice>>> get(String name) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysStateGet(name),
                (s, cf) -> {
                  if (s instanceof StateMachine.NotificationValue.Void) {
                    cf.complete(Optional.empty());
                  } else if (s instanceof StateMachine.NotificationValue.Success success) {
                    cf.complete(Optional.of(success.slice()));
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<AsyncResult<Collection<String>>> getKeys() {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysStateGetKeys(),
                (s, cf) -> {
                  if (s instanceof StateMachine.NotificationValue.StateKeys stateKeys) {
                    cf.complete(stateKeys.keys());
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<Void> clear(String name) {
    return this.catchExceptions(() -> this.stateMachine.sysStateClear(name));
  }

  @Override
  public CompletableFuture<Void> clearAll() {
    return this.catchExceptions(this.stateMachine::sysStateClearAll);
  }

  @Override
  public CompletableFuture<Void> set(String name, Slice value) {
    return this.catchExceptions(() -> this.stateMachine.sysStateSet(name, value));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> timer(Duration duration, String name) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysSleep(duration, name),
                (s, cf) -> {
                  if (s instanceof StateMachine.NotificationValue.Void) {
                    cf.complete(null);
                  } else {
                    throw ProtocolException.unexpectedNotificationVariant(s.getClass());
                  }
                }));
  }

  @Override
  public CompletableFuture<CallResult> call(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers) {
    return catchExceptions(
        () -> {
          StateMachine.SysCallReturn.Ok callHandle =
              this.stateMachine.sysCall(target, parameter, idempotencyKey, headers);

          AsyncResultInternal<String> invocationIdAsyncResult =
              AsyncResults.single(this, callHandle.invocationIdHandle(), invocationIdCompleter());

          AsyncResult<Slice> callAsyncResult =
              AsyncResults.single(
                  this, callHandle.resultHandle(), HandlerContextImpl::parseSuccessOrFailure);

          return new CallResult(invocationIdAsyncResult, callAsyncResult);
        });
  }

  @Override
  public CompletableFuture<AsyncResult<String>> send(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    return catchExceptions(
        () -> {
          int sendHandle =
              this.stateMachine.sysSend(target, parameter, idempotencyKey, headers, delay);

          return AsyncResults.single(this, sendHandle, invocationIdCompleter());
        });
  }

  private static AsyncResults.Completer<String> invocationIdCompleter() {
    return (s, cf) -> {
      if (s instanceof StateMachine.NotificationValue.InvocationId invocationId) {
        cf.complete(invocationId.id());
      } else {
        throw ProtocolException.unexpectedNotificationVariant(s.getClass());
      }
    };
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> submitRun(
      @Nullable String name, Consumer<RunCompleter> closure) {
    return catchExceptions(
        () -> {
          int runHandle = this.stateMachine.sysRun(name);
          this.scheduledRuns.put(runHandle, closure);
          return AsyncResults.single(this, runHandle, HandlerContextImpl::parseSuccessOrFailure);
        });
  }

  @Override
  public CompletableFuture<Awakeable> awakeable() {
    return catchExceptions(
        () -> {
          StateMachine.AwakeableReturn.Ok awakeable = this.stateMachine.sysAwakeable();
          return new Awakeable(
              awakeable.id(),
              AsyncResults.single(
                  this, awakeable.handle(), HandlerContextImpl::parseSuccessOrFailure));
        });
  }

  @Override
  public CompletableFuture<Void> resolveAwakeable(String id, Slice payload) {
    return this.catchExceptions(
        () -> this.stateMachine.sysCompleteAwakeableWithSuccess(id, payload));
  }

  @Override
  public CompletableFuture<Void> rejectAwakeable(String id, TerminalException reason) {
    return this.catchExceptions(
        () -> this.stateMachine.sysCompleteAwakeableWithFailure(id, reason));
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> promise(String key) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysPromiseGet(key),
                HandlerContextImpl::parseSuccessOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> peekPromise(String key) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysPromisePeek(key),
                HandlerContextImpl::parseEmptyOrSuccessOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> resolvePromise(String key, Slice payload) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysPromiseCompleteWithSuccess(key, payload),
                HandlerContextImpl::parseEmptyOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> rejectPromise(String key, TerminalException reason) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysPromiseCompleteWithFailure(key, reason),
                HandlerContextImpl::parseEmptyOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> signal(String name) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysCreateSignalHandle(name),
                HandlerContextImpl::parseSuccessOrFailure));
  }

  @Override
  public CompletableFuture<Void> resolveSignal(String invocationId, String name, Slice payload) {
    return this.catchExceptions(
        () -> this.stateMachine.sysCompleteSignalWithSuccess(invocationId, name, payload));
  }

  @Override
  public CompletableFuture<Void> rejectSignal(
      String invocationId, String name, TerminalException reason) {
    return this.catchExceptions(
        () -> this.stateMachine.sysCompleteSignalWithFailure(invocationId, name, reason));
  }

  @Override
  public CompletableFuture<Void> cancelInvocation(String invocationId) {
    return this.catchExceptions(() -> this.stateMachine.sysCancelInvocation(invocationId));
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> attachInvocation(String invocationId) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysAttachInvocation(invocationId),
                HandlerContextImpl::parseSuccessOrFailure));
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> getInvocationOutput(String invocationId) {
    return catchExceptions(
        () ->
            AsyncResults.single(
                this,
                this.stateMachine.sysGetInvocationOutput(invocationId),
                HandlerContextImpl::parseEmptyOrSuccessOrFailure));
  }

  @SuppressWarnings("removal")
  @Deprecated
  @Override
  public CompletableFuture<Void> writeOutput(Slice value) {
    return this.catchExceptions(() -> this.stateMachine.sysWriteOutputWithSuccess(value));
  }

  @SuppressWarnings("removal")
  @Deprecated
  @Override
  public CompletableFuture<Void> writeOutput(TerminalException throwable) {
    return this.catchExceptions(() -> this.stateMachine.sysWriteOutputWithFailure(throwable));
  }

  @Override
  public void pollAsyncResult(AsyncResultInternal<?> asyncResult) {
    try {
      this.pumpOutput();
      this.pollAsyncResultInner(asyncResult);
    } catch (Exception e) {
      this.failWithoutContextSwitch(e);
    }
  }

  private void pollAsyncResultInner(AsyncResultInternal<?> asyncResult) {
    while (true) {
      if (this.stateMachine.state() == InvocationState.CLOSED) {
        asyncResult.publicFuture().completeExceptionally(AbortedExecutionException.INSTANCE);
        return;
      }
      if (asyncResult.isDone()) {
        return;
      }

      // Let's start by trying to complete it
      try {
      asyncResult.tryComplete(this::takeNotification);
    } catch (Throwable e) {
        // This can happen if the state machine was closed in the meantime.
      failWithoutContextSwitch(e);
      asyncResult.publicFuture().completeExceptionally(AbortedExecutionException.INSTANCE);
      return;
    }

      // Build the tree of what we're still awaiting on
      StateMachine.UnresolvedFuture future = asyncResult.uncompletedFuture();
      if (future == null) {
        // Nothing else to do!
        return;
      }

      // Not ready yet, let's try to do some progress
      StateMachine.AwaitResult response;
      try {
        response = this.stateMachine.doAwait(future);
      } catch (Throwable e) {
        this.failWithoutContextSwitch(e);
        asyncResult.publicFuture().completeExceptionally(AbortedExecutionException.INSTANCE);
        return;
      }

      if (response instanceof StateMachine.AwaitResult.AnyCompleted) {
        // Let it loop now
      } else if (response instanceof StateMachine.AwaitResult.WaitExternalProgress) {
        this.pumpOutput();
        this.externalProgressChannel.awaitNext(() -> this.pollAsyncResultInner(asyncResult));
        return;
      } else if (response instanceof StateMachine.AwaitResult.CancelSignalReceived) {
        asyncResult.tryCancel();
        return;
      } else if (response instanceof StateMachine.AwaitResult.ExecuteRun) {
        triggerScheduledRun(((StateMachine.AwaitResult.ExecuteRun) response).handle());
        // Let it loop now
      }
    }
  }

  Optional<StateMachine.NotificationValue> takeNotification(int handle) {
    return Optional.ofNullable(this.stateMachine.takeNotification(handle));
  }

  @Override
  public void proposeRunSuccess(int runHandle, Slice toWrite) {
    try {
      // TODO this fails here when you propose a run but the state machine was already closed!!!
      this.stateMachine.proposeRunCompletionWithSuccess(runHandle, toWrite);
    } catch (Exception e) {
      this.failWithoutContextSwitch(e);
    }
    this.pumpOutput();
    this.externalProgressChannel.signal();
  }

  @Override
  public void proposeRunFailure(
      int runHandle,
      Throwable throwable,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    try {
      if (throwable instanceof TerminalException) {
        this.stateMachine.proposeRunCompletionTerminalFailure(
            runHandle, (TerminalException) throwable);
      } else {
        this.stateMachine.proposeRunCompletionRetryableFailure(
            runHandle, throwable, attemptDuration, retryPolicy);
      }
    } catch (Exception e) {
      this.failWithoutContextSwitch(e);
    }
    this.pumpOutput();
    this.externalProgressChannel.signal();
  }

  private void triggerScheduledRun(int handle) {
    var consumer =
        Objects.requireNonNull(
            this.scheduledRuns.get(handle), "The given handle doesn't exist, this is an SDK bug");
    var startTime = Instant.now();
    consumer.accept(
        new RunCompleter() {
          @Override
          public void proposeSuccess(Slice toWrite) {
            proposeRunSuccess(handle, toWrite);
          }

          @Override
          public void proposeFailure(Throwable toWrite, @Nullable RetryPolicy retryPolicy) {
            proposeRunFailure(
                handle, toWrite, Duration.between(startTime, Instant.now()), retryPolicy);
          }
        });
  }

  private void pumpOutput() {
    byte[] chunk = stateMachine.takeOutput();
    if (chunk.length > 0) outputSink.accept(Slice.wrap(chunk));
  }

  @Override
  public void fail(Throwable cause) {
    this.failWithoutContextSwitch(cause);
  }

  @Override
  public void failWithoutContextSwitch(Throwable cause) {
    this.stateMachine.notifyError(cause);
  }

  // -- Wrapper for failure propagation

  private CompletableFuture<Void> catchExceptions(ThrowingRunnable r) {
    try {
      r.run();
      return CompletableFuture.completedFuture(null);
    } catch (Throwable e) {
      this.failWithoutContextSwitch(e);
      return CompletableFuture.failedFuture(AbortedExecutionException.INSTANCE);
    }
  }

  private <T> CompletableFuture<T> catchExceptions(ThrowingSupplier<T> r) {
    try {
      return CompletableFuture.completedFuture(r.get());
    } catch (Throwable e) {
      this.failWithoutContextSwitch(e);
      return CompletableFuture.failedFuture(AbortedExecutionException.INSTANCE);
    }
  }
}
