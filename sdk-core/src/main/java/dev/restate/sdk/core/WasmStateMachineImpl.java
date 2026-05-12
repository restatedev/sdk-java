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
import dev.restate.sdk.common.*;
import dev.restate.sdk.core.sharedcore.SharedCoreVM;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class WasmStateMachineImpl implements StateMachine {

  private final SharedCoreVM vm;
  private final CompletableFuture<Void> waitForReadyFuture = new CompletableFuture<>();

  private @NonNull Runnable nextEventListener = () -> {};
  private Flow.@Nullable Subscriber<? super Slice> outputSubscriber;
  private Flow.@Nullable Subscription inputSubscription;
  private boolean inputClosed = false;

  WasmStateMachineImpl(
      HeadersAccessor headersAccessor,
      EndpointRequestHandler.LoggingContextSetter loggingContextSetter) {
    this.vm = SharedCoreVM.create(headersAccessor);
  }

  // ---------------------------------------------------------------------------
  // Flow.Processor — input side
  // ---------------------------------------------------------------------------

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.inputSubscription = subscription;
    subscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(Slice slice) {
    try {
      vm.notifyInput(slice.toByteArray());
      checkReadyToExecute();
      triggerExternalProgressSignal();
    } catch (Throwable e) {
      onError(e);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    try {
      String message =
          throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getName();
      vm.notifyError(message, stacktraceToString(throwable), null);
      pumpOutput();
    } catch (Throwable ignored) {
      // ignore errors from notifyError — we're already in error handling
    }
    if (!waitForReadyFuture.isDone()) {
      waitForReadyFuture.completeExceptionally(throwable);
    }
    triggerExternalProgressSignal();
    cancelInputSubscription();
  }

  @Override
  public void onComplete() {
    try {
      vm.notifyInputClosed();
      checkReadyToExecute();
    } catch (Throwable e) {
      onError(e);
      return;
    }
    triggerExternalProgressSignal();
    cancelInputSubscription();
  }

  // ---------------------------------------------------------------------------
  // Flow.Processor — output side
  // ---------------------------------------------------------------------------

  @Override
  public void subscribe(Flow.Subscriber<? super Slice> subscriber) {
    this.outputSubscriber = subscriber;
    subscriber.onSubscribe(
        new Flow.Subscription() {
          @Override
          public void request(long n) {}

          @Override
          public void cancel() {
            end();
          }
        });
  }

  @Override
  public void pumpOutput() {
    if (outputSubscriber == null) {
      return;
    }
    byte[] chunk = vm.takeOutput();
    if (chunk.length == 0) {
      return;
    }
    outputSubscriber.onNext(Slice.wrap(chunk));
  }

  private void checkReadyToExecute() {
    if (!waitForReadyFuture.isDone() && vm.isReadyToExecute()) {
      waitForReadyFuture.complete(null);
    }
  }

  // ---------------------------------------------------------------------------
  // StateMachine — lifecycle
  // ---------------------------------------------------------------------------

  @Override
  public String getResponseContentType() {
    return vm.getResponseContentType();
  }

  @Override
  public CompletableFuture<Void> waitForReady() {
    return waitForReadyFuture;
  }

  @Override
  public void onExternalProgress(Runnable runnable) {
    this.nextEventListener =
        () -> {
          this.nextEventListener.run();
          runnable.run();
        };
  }

  private void triggerExternalProgressSignal() {
    Runnable listener = this.nextEventListener;
    this.nextEventListener = () -> {};
    listener.run();
  }

  private void cancelInputSubscription() {
    this.inputClosed = true;
    if (this.inputSubscription != null) {
      this.inputSubscription.cancel();
      this.inputSubscription = null;
    }
  }

  @Override
  public DoProgressResponse doProgress(List<Integer> handles) {
    int[] arr = handles.stream().mapToInt(Integer::intValue).toArray();
    SharedCoreVM.DoProgressResult result = vm.doProgress(arr);
    if (result instanceof SharedCoreVM.DoProgressResult.AnyCompleted) {
      return DoProgressResponse.AnyCompleted.INSTANCE;
    } else if (result instanceof SharedCoreVM.DoProgressResult.WaitExternalProgress) {
      return DoProgressResponse.WaitExternalProgress.INSTANCE;
    } else if (result instanceof SharedCoreVM.DoProgressResult.CancelSignalReceived) {
      return DoProgressResponse.CancelSignalReceived.INSTANCE;
    } else if (result instanceof SharedCoreVM.DoProgressResult.ExecuteRun r) {
      return new DoProgressResponse.ExecuteRun(r.handle());
    } else if (result instanceof SharedCoreVM.DoProgressResult.Suspended) {
      ExceptionUtils.sneakyThrow(AbortedExecutionException.INSTANCE);
    }
    throw new IllegalStateException("Unknown DoProgressResult: " + result);
  }

  @Override
  public boolean isCompleted(int handle) {
    return vm.isCompleted(handle);
  }

  @Override
  public Optional<NotificationValue> takeNotification(int handle) {
    SharedCoreVM.NotificationValue raw = vm.takeNotification(handle);
    if (raw == null) return Optional.empty();
    return Optional.of(mapNotificationValue(raw));
  }

  private static NotificationValue mapNotificationValue(SharedCoreVM.NotificationValue raw) {
    if (raw instanceof SharedCoreVM.NotificationValue.Void) {
      return NotificationValue.Empty.INSTANCE;
    } else if (raw instanceof SharedCoreVM.NotificationValue.Success s) {
      return new NotificationValue.Success(Slice.wrap(s.value()));
    } else if (raw instanceof SharedCoreVM.NotificationValue.Failure f) {
      Map<String, String> meta = new LinkedHashMap<>();
      if (f.metadata() != null) {
        for (String[] pair : f.metadata()) meta.put(pair[0], pair[1]);
      }
      return new NotificationValue.Failure(new TerminalException(f.code(), f.message(), meta));
    } else if (raw instanceof SharedCoreVM.NotificationValue.StateKeys sk) {
      return new NotificationValue.StateKeys(sk.keys());
    } else if (raw instanceof SharedCoreVM.NotificationValue.InvocationId id) {
      return new NotificationValue.InvocationId(id.id());
    }
    throw new IllegalStateException("Unknown NotificationValue: " + raw);
  }

  // ---------------------------------------------------------------------------
  // StateMachine — commands
  // ---------------------------------------------------------------------------

  @Override
  public @Nullable Input input() {
    SharedCoreVM.Input raw = vm.sysInput();
    if (raw == null) return null;
    // Build a Map<String,String> from the header list
    Map<String, String> headers = new LinkedHashMap<>();
    for (var e : raw.headers()) {
      headers.put(e.getKey(), e.getValue());
    }
    return new Input(
        new InvocationIdImpl(raw.invocationId(), raw.randomSeed()),
        Slice.wrap(raw.body()),
        Collections.unmodifiableMap(headers),
        raw.key().isEmpty() ? null : raw.key());
  }

  @Override
  public int stateGet(String key) {
    return vm.sysStateGet(key);
  }

  @Override
  public int stateGetKeys() {
    return vm.sysStateGetKeys();
  }

  @Override
  public void stateSet(String key, Slice bytes) {
    vm.sysStateSet(key, bytes.toByteArray());
  }

  @Override
  public void stateClear(String key) {
    vm.sysStateClear(key);
  }

  @Override
  public void stateClearAll() {
    vm.sysStateClearAll();
  }

  @Override
  public int sleep(Duration duration, @Nullable String name) {
    return vm.sysSleep(duration.toMillis(), name);
  }

  @Override
  public CallHandle call(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers) {
    SharedCoreVM.CallHandleResult r =
        vm.sysCall(
            target.getService(),
            target.getHandler(),
            target.getKey(),
            payload.toByteArray(),
            idempotencyKey,
            headers != null ? new ArrayList<>(headers) : null);
    return new CallHandle(r.invocationIdHandle(), r.resultHandle());
  }

  @Override
  public int send(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    return vm.sysSend(
        target.getService(),
        target.getHandler(),
        target.getKey(),
        payload.toByteArray(),
        idempotencyKey,
        headers != null ? new ArrayList<>(headers) : null,
        delay != null ? delay.toMillis() : null);
  }

  @Override
  public Awakeable awakeable() {
    SharedCoreVM.AwakeableResult r = vm.sysAwakeable();
    return new Awakeable(r.awakeableId(), r.signalHandle());
  }

  @Override
  public void completeAwakeable(String awakeableId, Slice value) {
    vm.sysCompleteAwakeableSuccess(awakeableId, value.toByteArray());
  }

  @Override
  public void completeAwakeable(String awakeableId, TerminalException exception) {
    vm.sysCompleteAwakeableFailure(
        awakeableId, exception.getCode(), exception.getMessage(), toMetaList(exception));
  }

  @Override
  public int createSignalHandle(String signalName) {
    return vm.sysCreateSignalHandle(signalName);
  }

  @Override
  public void completeSignal(String targetInvocationId, String signalName, Slice value) {
    vm.sysCompleteSignalSuccess(targetInvocationId, signalName, value.toByteArray());
  }

  @Override
  public void completeSignal(
      String targetInvocationId, String signalName, TerminalException exception) {
    vm.sysCompleteSignalFailure(
        targetInvocationId,
        signalName,
        exception.getCode(),
        exception.getMessage(),
        toMetaList(exception));
  }

  @Override
  public int promiseGet(String key) {
    return vm.sysPromiseGet(key);
  }

  @Override
  public int promisePeek(String key) {
    return vm.sysPromisePeek(key);
  }

  @Override
  public int promiseComplete(String key, Slice value) {
    return vm.sysPromiseCompleteSuccess(key, value.toByteArray());
  }

  @Override
  public int promiseComplete(String key, TerminalException exception) {
    return vm.sysPromiseCompleteFailure(
        key, exception.getCode(), exception.getMessage(), toMetaList(exception));
  }

  @Override
  public int run(String name) {
    return vm.sysRun(name);
  }

  @Override
  public void proposeRunCompletion(int handle, Slice value) {
    vm.proposeRunCompletionSuccess(handle, value.toByteArray());
    pumpOutput();
  }

  @Override
  public void proposeRunCompletion(
      int handle,
      Throwable exception,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    SharedCoreVM.WasmRetryPolicy rp = toWasmRetryPolicy(retryPolicy);
    if (exception instanceof TerminalException te) {
      vm.proposeRunCompletionTerminalFailure(handle, te.getCode(), te.getMessage(), toMetaList(te));
    } else {
      vm.proposeRunCompletionRetryableFailure(
          handle,
          500,
          exception.getMessage() != null ? exception.getMessage() : exception.getClass().getName(),
          stacktraceToString(exception),
          attemptDuration.toMillis(),
          rp);
    }
    pumpOutput();
  }

  @Override
  public void cancelInvocation(String targetInvocationId) {
    vm.sysCancelInvocation(targetInvocationId);
  }

  @Override
  public int attachInvocation(String invocationId) {
    return vm.sysAttachInvocation(invocationId);
  }

  @Override
  public int getInvocationOutput(String invocationId) {
    return vm.sysGetInvocationOutput(invocationId);
  }

  @Override
  public void writeOutput(Slice value) {
    vm.sysWriteOutputSuccess(value.toByteArray());
  }

  @Override
  public void writeOutput(TerminalException exception) {
    vm.sysWriteOutputFailure(exception.getCode(), exception.getMessage(), toMetaList(exception));
  }

  @Override
  public void end() {
    try {
      vm.sysEnd();
      this.pumpOutput();
    } finally {
      if (outputSubscriber != null) {
        outputSubscriber.onComplete();
      }
      vm.close();
      cancelInputSubscription();
    }
  }

  // ---------------------------------------------------------------------------
  // Introspection
  // ---------------------------------------------------------------------------

  @Override
  public InvocationState state() {
    if (inputClosed) return InvocationState.CLOSED;
    if (!waitForReadyFuture.isDone()) return InvocationState.WAITING_START;
    return InvocationState.PROCESSING;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static SharedCoreVM.@Nullable WasmRetryPolicy toWasmRetryPolicy(
      @Nullable RetryPolicy retryPolicy) {
    if (retryPolicy == null) return null;
    return new SharedCoreVM.WasmRetryPolicy(
        retryPolicy.getInitialDelay().toMillis(),
        retryPolicy.getExponentiationFactor(),
        retryPolicy.getMaxDelay() != null ? retryPolicy.getMaxDelay().toMillis() : null,
        retryPolicy.getMaxAttempts(),
        retryPolicy.getMaxDuration() != null ? retryPolicy.getMaxDuration().toMillis() : null);
  }

  private static @Nullable List<String[]> toMetaList(TerminalException exception) {
    Map<String, String> meta = exception.getMetadata();
    if (meta == null || meta.isEmpty()) return null;
    List<String[]> r = new ArrayList<>(meta.size());
    for (Map.Entry<String, String> e : meta.entrySet())
      r.add(new String[] {e.getKey(), e.getValue()});
    return r;
  }

  private static String stacktraceToString(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
