// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.sharedcore;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.InvocationState;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Java wrapper around the Rust {@code restate-sdk-shared-core} VM, embedded as a Chicory WASM
 * module.
 *
 * <p>Mirrors sdk-go/internal/statemachine/wasm.go. Every WASM function returns {@code u64 = (ptr <<
 * 32) | len} pointing to CBOR. Response types match the Rust output DTOs 1:1.
 */
public final class StateMachine implements AutoCloseable {

  private static final Logger LOG = LogManager.getLogger(StateMachine.class);

  private final SharedCoreInstance instance;
  private final int vmPtr;
  private boolean freed;

  private StateMachine(SharedCoreInstance instance, int vmPtr) {
    this.instance = instance;
    this.vmPtr = vmPtr;
    this.freed = false;
  }

  // -------------------------------------------------------------------------
  // Factory
  // -------------------------------------------------------------------------

  public static StateMachine create(HeadersAccessor headersAccessor) {
    LOG.trace("create()");
    SharedCoreInstance instance = SharedCoreInstance.get();

    var newVmReturn =
        instance.callCborVmFunction(
            SharedCoreWasm_ModuleExports::vmNew,
            new VmNewParameters(
                StreamSupport.stream(headersAccessor.keys().spliterator(), false)
                    .map(key -> new String[] {key, headersAccessor.get(key)})
                    .filter(arr -> arr[1] != null)
                    .collect(Collectors.toList())),
            VmNewReturn.class);
    if (newVmReturn instanceof VmNewReturn.Failure f) {
      throw new ProtocolException("Failed to create state machine: " + f.message(), f.code);
    }
    int vmPtr = ((VmNewReturn.Ok) newVmReturn).pointer();
    return new StateMachine(instance, vmPtr);
  }

  @Override
  public void close() {
    if (!freed) {
      LOG.trace("[vm=0x{}] close()", Integer.toHexString(vmPtr));
      instance.getExports().vmFree(vmPtr);
      freed = true;
    }
  }

  private void verifyNotFreed() {
    if (freed) {
      AbortedExecutionException.sneakyThrow();
    }
  }

  public void notifyInput(byte[] bytes) {
    if (freed) {
      return;
    }
    LOG.trace("[vm=0x{}] notifyInput()", Integer.toHexString(vmPtr));
    var bufferPtr = instance.write(bytes);
    instance.getExports().vmNotifyInput(vmPtr, bufferPtr, bytes.length);
  }

  public void notifyInputClosed() {
    if (freed) {
      return;
    }
    LOG.trace("[vm=0x{}] notifyInputClosed()", Integer.toHexString(vmPtr));
    instance.getExports().vmNotifyInputClosed(vmPtr);
  }

  public void notifyError(Throwable throwable) {
    if (freed) {
      return;
    }
    LOG.trace("[vm=0x{}] notifyError()", Integer.toHexString(vmPtr));
    instance.callCborVmFunction(
        (exports, ptr, len) -> exports.vmNotifyError(vmPtr, ptr, len),
        new VmNotifyError(throwable.getMessage(), stacktraceToString(throwable)));
  }

  public byte[] takeOutput() {
    if (freed) {
      return new byte[0];
    }
    LOG.trace("[vm=0x{}] takeOutput()", Integer.toHexString(vmPtr));

    var ptr = instance.getExports().vmTakeOutput(vmPtr);
    return instance.readAndFree(ptr);
  }

  public String getResponseContentType() {
    LOG.trace("[vm=0x{}] getResponseContentType()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    var ret =
        instance.callCborVmFunction(
            exports -> exports.vmGetResponseHead(vmPtr), ResponseHeadReturn.class);
    if (ret.headers() == null) return "";
    for (String[] pair : ret.headers()) {
      if ("content-type".equalsIgnoreCase(pair[0])) return pair[1];
    }
    return "";
  }

  public boolean isReadyToExecute() {
    LOG.trace("[vm=0x{}] isReadyToExecute()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    var ret =
        instance.callCborVmFunction(
            exports -> exports.vmIsReadyToExecute(vmPtr), IsReadyReturn.class);
    if (ret instanceof IsReadyReturn.Failure f) throw vmError(f.code, f.message);
    return ((IsReadyReturn.Ok) ret).ready();
  }

  public InvocationState state() {
    if (freed) {
      return InvocationState.CLOSED;
    }
    int ordinal = instance.getExports().vmState(vmPtr);
    return InvocationState.values()[ordinal];
  }

  public AwaitResult doAwait(UnresolvedFuture future) {
    LOG.trace("[vm=0x{}] doAwait()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    var ret =
        instance.callCborVmFunction(
            (exports, ptr, len) -> exports.vmDoProgress(vmPtr, ptr, len),
            new VmDoProgressParameters(future),
            DoProgressReturn.class);
    if (ret instanceof DoProgressReturn.AnyCompleted) return AwaitResult.ANY_COMPLETED;
    if (ret instanceof DoProgressReturn.WaitingExternalProgress)
      return AwaitResult.WAIT_EXTERNAL_PROGRESS;
    if (ret instanceof DoProgressReturn.CancelSignalReceived)
      return AwaitResult.CANCEL_SIGNAL_RECEIVED;
    if (ret instanceof DoProgressReturn.ExecuteRun r) return new AwaitResult.ExecuteRun(r.handle());
    if (ret instanceof DoProgressReturn.Suspended) return AwaitResult.SUSPENDED;
    DoProgressReturn.Failure f = (DoProgressReturn.Failure) ret;
    throw vmError(f.code, f.message);
  }

  public @Nullable NotificationValue takeNotification(int handle) {
    LOG.trace("[vm=0x{}] takeNotification()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    var ret =
        instance.callCborVmFunction(
            exports -> exports.vmTakeNotification(vmPtr, handle), TakeNotificationReturn.class);
    if (ret instanceof TakeNotificationReturn.NotReady) return null;
    if (ret instanceof TakeNotificationReturn.Value v) return v.value();
    TakeNotificationReturn.Failure f = (TakeNotificationReturn.Failure) ret;
    throw vmError(f.code, f.message);
  }

  public Input sysInput() {
    LOG.trace("[vm=0x{}] sysInput()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    var ret =
        instance.callCborVmFunction(exports -> exports.vmSysInput(vmPtr), SysInputReturn.class);
    if (ret instanceof SysInputReturn.Failure f) throw vmError(f.code(), f.message());
    return ((SysInputReturn.Ok) ret).input();
  }

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------

  public int sysStateGet(String key) {
    LOG.trace("[vm=0x{}] sysStateGet()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysStateGet, new VmSysStateGetParameters(key));
  }

  public int sysStateGetKeys() {
    LOG.trace("[vm=0x{}] sysStateGetKeys()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    return callWithHandleReturn(SharedCoreWasm_ModuleExports::vmSysStateGetKeys);
  }

  public void sysStateSet(String key, Slice value) {
    LOG.trace("[vm=0x{}] sysStateSet()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysStateSet,
        new VmSysStateSetParameters(key, value.toByteArray()));
  }

  public void sysStateClear(String key) {
    LOG.trace("[vm=0x{}] sysStateClear()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysStateClear, new VmSysStateClearParameters(key));
  }

  public void sysStateClearAll() {
    LOG.trace("[vm=0x{}] sysStateClearAll()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(SharedCoreWasm_ModuleExports::vmSysStateClearAll);
  }

  // -------------------------------------------------------------------------
  // Sleep
  // -------------------------------------------------------------------------

  public int sysSleep(Duration duration, @Nullable String name) {
    LOG.trace("[vm=0x{}] sysSleep()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    long now = System.currentTimeMillis();
    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysSleep,
        new VmSysSleepParameters(name != null ? name : "", now + duration.toMillis(), now));
  }

  // -------------------------------------------------------------------------
  // Call / Send
  // -------------------------------------------------------------------------

  public SysCallReturn.Ok sysCall(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers) {
    LOG.trace("[vm=0x{}] sysCall()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    var ret =
        instance.callCborVmFunction(
            (exports, ptr, len) -> exports.vmSysCall(vmPtr, ptr, len),
            new VmSysCallParameters(
                target.getService(),
                target.getHandler(),
                target.getKey(),
                idempotencyKey,
                toHeaderList(headers),
                payload.toByteArray()),
            SysCallReturn.class);
    if (ret instanceof SysCallReturn.Failure f) {
      throw vmError(f.code(), f.message());
    }
    return (SysCallReturn.Ok) ret;
  }

  public int sysSend(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    LOG.trace("[vm=0x{}] sysSend()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    Long executionTime =
        (delay != null && !delay.isZero()) ? System.currentTimeMillis() + delay.toMillis() : null;
    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysSend,
        new VmSysSendParameters(
            target.getService(),
            target.getHandler(),
            target.getKey(),
            idempotencyKey,
            toHeaderList(headers),
            payload.toByteArray(),
            executionTime));
  }

  // -------------------------------------------------------------------------
  // Awakeables
  // -------------------------------------------------------------------------

  public AwakeableReturn.Ok sysAwakeable() {
    LOG.trace("[vm=0x{}] sysAwakeable()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    AwakeableReturn ret =
        instance.callCborVmFunction(
            (exports) -> exports.vmSysAwakeable(vmPtr), AwakeableReturn.class);
    if (ret instanceof AwakeableReturn.Failure f) {
      throw vmError(f.code(), f.message());
    }
    return (AwakeableReturn.Ok) ret;
  }

  public void sysCompleteAwakeableWithSuccess(String id, Slice payload) {
    sysCompleteAwakeable(id, new NonEmptyValueParam.Success(payload));
  }

  public void sysCompleteAwakeableWithFailure(String id, TerminalException reason) {
    sysCompleteAwakeable(id, new NonEmptyValueParam.Failure(reason));
  }

  private void sysCompleteAwakeable(String id, NonEmptyValueParam result) {
    LOG.trace("[vm=0x{}] sysCompleteAwakeable()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysCompleteAwakeable,
        new VmSysCompleteAwakeableParameters(id, result));
  }

  public int sysCreateSignalHandle(String signalName) {
    LOG.trace("[vm=0x{}] sysCreateSignalHandle()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysCreateSignalHandle,
        new VmSysCreateSignalHandleParameters(signalName));
  }

  public void sysCompleteSignalWithSuccess(String target, String signalName, Slice slice) {
    sysCompleteSignal(target, signalName, new NonEmptyValueParam.Success(slice));
  }

  public void sysCompleteSignalWithFailure(
      String target, String signalName, TerminalException reason) {
    sysCompleteSignal(target, signalName, new NonEmptyValueParam.Failure(reason));
  }

  private void sysCompleteSignal(String target, String signalName, NonEmptyValueParam result) {
    LOG.trace("[vm=0x{}] sysCompleteSignal()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysCompleteSignal,
        new VmSysCompleteSignalParameters(target, signalName, result));
  }

  public int sysPromiseGet(String key) {
    LOG.trace("[vm=0x{}] sysPromiseGet()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysPromiseGet, new VmSysPromiseGetParameters(key));
  }

  public int sysPromisePeek(String key) {
    LOG.trace("[vm=0x{}] sysPromisePeek()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysPromisePeek, new VmSysPromisePeekParameters(key));
  }

  public int sysPromiseComplete(String key, NonEmptyValueParam result) {
    LOG.trace("[vm=0x{}] sysPromiseComplete()", Integer.toHexString(vmPtr));
    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysPromiseComplete,
        new VmSysPromiseCompleteParameters(key, result));
  }

  public int sysPromiseCompleteWithSuccess(String key, Slice value) {
    return sysPromiseComplete(key, new NonEmptyValueParam.Success(value));
  }

  public int sysPromiseCompleteWithFailure(String key, TerminalException reason) {
    return sysPromiseComplete(key, new NonEmptyValueParam.Failure(reason));
  }

  public int sysRun(String name) {
    LOG.trace("[vm=0x{}] sysRun()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysRun, new VmSysRunParameters(name));
  }

  public void proposeRunCompletionWithSuccess(int handle, Slice value) {
    LOG.trace("[vm=0x{}] proposeRunCompletionSuccess()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmProposeRunCompletion,
        new VmProposeRunCompletionParameters(
            handle, new RunResult.Success(value.toByteArray()), 0L, null));
  }

  public void proposeRunCompletionTerminalFailure(int handle, TerminalException terminalException) {
    LOG.trace("[vm=0x{}] proposeRunCompletionTerminalFailure()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmProposeRunCompletion,
        new VmProposeRunCompletionParameters(
            handle, new RunResult.TerminalFailure(terminalException), 0L, null));
  }

  public void proposeRunCompletionRetryableFailure(
      int handle,
      Throwable throwable,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    LOG.trace("[vm=0x{}] proposeRunCompletionRetryableFailure()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmProposeRunCompletion,
        new VmProposeRunCompletionParameters(
            handle,
            new RunResult.RetryableFailure(throwable),
            attemptDuration.toMillis(),
            retryPolicy != null ? new WasmRetryPolicy(retryPolicy) : null));
  }

  public void sysCancelInvocation(String invocationId) {
    LOG.trace("[vm=0x{}] sysCancelInvocation()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysCancelInvocation,
        new VmSysCancelInvocation(invocationId));
  }

  public int sysAttachInvocation(String invocationId) {
    LOG.trace("[vm=0x{}] sysAttachInvocation()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysAttachInvocation,
        new VmSysAttachInvocation(invocationId));
  }

  public int sysGetInvocationOutput(String invocationId) {
    LOG.trace("[vm=0x{}] sysGetInvocationOutput()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysGetInvocationOutput,
        new VmSysGetInvocationOutput(invocationId));
  }

  public void sysWriteOutput(NonEmptyValueParam result) {
    LOG.trace("[vm=0x{}] sysWriteOutput()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysWriteOutput, new VmSysWriteOutputParameters(result));
  }

  public void sysWriteOutputWithSuccess(Slice value) {
    sysWriteOutput(new NonEmptyValueParam.Success(value));
  }

  public void sysWriteOutputWithFailure(TerminalException exception) {
    sysWriteOutput(new NonEmptyValueParam.Failure(exception));
  }

  public void sysEnd() {
    LOG.trace("[vm=0x{}] sysEnd()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    callWithEmptyReturn(SharedCoreWasm_ModuleExports::vmSysEnd);
  }

  // =========================================================================
  // Result types (returned to callers of this class)
  // =========================================================================

  public sealed interface AwaitResult {
    AwaitResult ANY_COMPLETED = new AnyCompleted();
    AwaitResult WAIT_EXTERNAL_PROGRESS = new WaitExternalProgress();
    AwaitResult CANCEL_SIGNAL_RECEIVED = new CancelSignalReceived();
    AwaitResult SUSPENDED = new Suspended();

    record AnyCompleted() implements AwaitResult {}

    record WaitExternalProgress() implements AwaitResult {}

    record ExecuteRun(int handle) implements AwaitResult {}

    record CancelSignalReceived() implements AwaitResult {}

    record Suspended() implements AwaitResult {}
  }

  /**
   * Tree-shaped await point. Mirrors the Rust {@code restate_sdk_shared_core::UnresolvedFuture}
   * 1:1, so the runtime sees the actual combinator semantics in {@code AwaitingOnMessage} / {@code
   * SuspensionMessage}.
   */
  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = UnresolvedFuture.Single.class, name = "single"),
    @JsonSubTypes.Type(value = UnresolvedFuture.FirstCompleted.class, name = "firstCompleted"),
    @JsonSubTypes.Type(value = UnresolvedFuture.AllCompleted.class, name = "allCompleted"),
    @JsonSubTypes.Type(
        value = UnresolvedFuture.FirstSucceededOrAllFailed.class,
        name = "firstSucceededOrAllFailed"),
    @JsonSubTypes.Type(
        value = UnresolvedFuture.AllSucceededOrFirstFailed.class,
        name = "allSucceededOrFirstFailed"),
    @JsonSubTypes.Type(value = UnresolvedFuture.Unknown.class, name = "unknown"),
  })
  public sealed interface UnresolvedFuture {
    record Single(int handle) implements UnresolvedFuture {}

    record FirstCompleted(List<UnresolvedFuture> children) implements UnresolvedFuture {}

    record AllCompleted(List<UnresolvedFuture> children) implements UnresolvedFuture {}

    record FirstSucceededOrAllFailed(List<UnresolvedFuture> children) implements UnresolvedFuture {}

    record AllSucceededOrFirstFailed(List<UnresolvedFuture> children) implements UnresolvedFuture {}

    record Unknown(List<UnresolvedFuture> children) implements UnresolvedFuture {}
  }

  // =========================================================================
  // CBOR response types (decoded from WASM responses)
  // =========================================================================

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = VmNewReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = VmNewReturn.Failure.class, name = "failure"),
  })
  sealed interface VmNewReturn {
    record Ok(int pointer) implements VmNewReturn {}

    record Failure(int code, String message) implements VmNewReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = EmptyReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = EmptyReturn.Failure.class, name = "failure"),
  })
  sealed interface EmptyReturn {
    record Ok() implements EmptyReturn {}

    record Failure(int code, String message) implements EmptyReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = HandleReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = HandleReturn.Failure.class, name = "failure"),
  })
  sealed interface HandleReturn {
    record Ok(int handle) implements HandleReturn {}

    record Failure(int code, String message) implements HandleReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = IsReadyReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = IsReadyReturn.Failure.class, name = "failure"),
  })
  sealed interface IsReadyReturn {
    record Ok(boolean ready) implements IsReadyReturn {}

    record Failure(int code, String message) implements IsReadyReturn {}
  }

  record ResponseHeadReturn(int statusCode, List<String[]> headers) {}

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = DoProgressReturn.AnyCompleted.class, name = "anyCompleted"),
    @JsonSubTypes.Type(
        value = DoProgressReturn.WaitingExternalProgress.class,
        name = "waitingExternalProgress"),
    @JsonSubTypes.Type(value = DoProgressReturn.ExecuteRun.class, name = "executeRun"),
    @JsonSubTypes.Type(
        value = DoProgressReturn.CancelSignalReceived.class,
        name = "cancelSignalReceived"),
    @JsonSubTypes.Type(value = DoProgressReturn.Suspended.class, name = "suspended"),
    @JsonSubTypes.Type(value = DoProgressReturn.Failure.class, name = "failure"),
  })
  public sealed interface DoProgressReturn {
    record AnyCompleted() implements DoProgressReturn {}

    record WaitingExternalProgress() implements DoProgressReturn {}

    record ExecuteRun(int handle) implements DoProgressReturn {}

    record CancelSignalReceived() implements DoProgressReturn {}

    record Suspended() implements DoProgressReturn {}

    record Failure(int code, String message) implements DoProgressReturn {}
  }

  /** Notification value payload — matches Rust {@code NotificationValue}. */
  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = NotificationValue.Void.class, name = "void"),
    @JsonSubTypes.Type(value = NotificationValue.Success.class, name = "success"),
    @JsonSubTypes.Type(value = NotificationValue.Failure.class, name = "failure"),
    @JsonSubTypes.Type(value = NotificationValue.StateKeys.class, name = "stateKeys"),
    @JsonSubTypes.Type(value = NotificationValue.InvocationId.class, name = "invocationId"),
  })
  public sealed interface NotificationValue {
    NotificationValue VOID = new Void();

    record Void() implements NotificationValue {}

    record Success(byte[] value) implements NotificationValue {
      public Slice slice() {
        return Slice.wrap(value);
      }
    }

    record Failure(int code, String message, @Nullable List<String[]> metadata)
        implements NotificationValue {
      public TerminalException terminalException() {
        return new TerminalException(
            code,
            message,
            metadata != null
                ? metadata.stream().collect(Collectors.toMap(e -> e[0], e -> e[1]))
                : null);
      }
    }

    record StateKeys(List<String> keys) implements NotificationValue {}

    record InvocationId(String id) implements NotificationValue {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = TakeNotificationReturn.NotReady.class, name = "notReady"),
    @JsonSubTypes.Type(value = TakeNotificationReturn.Value.class, name = "value"),
    @JsonSubTypes.Type(value = TakeNotificationReturn.Failure.class, name = "failure"),
  })
  public sealed interface TakeNotificationReturn {
    record NotReady() implements TakeNotificationReturn {}

    record Value(NotificationValue value) implements TakeNotificationReturn {}

    record Failure(int code, String message) implements TakeNotificationReturn {}
  }

  public record Input(
      String invocationId, String key, List<String[]> headers, byte[] input, long randomSeed) {
    public Map<String, String> headersAsMap() {
      Map<String, String> orderedHeaders = new LinkedHashMap<>();
      if (this.headers() != null) {
        for (var e : this.headers()) orderedHeaders.put(e[0], e[1]);
      }
      return Collections.unmodifiableMap(orderedHeaders);
    }
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SysInputReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = SysInputReturn.Failure.class, name = "failure"),
  })
  public sealed interface SysInputReturn {
    record Ok(Input input) implements SysInputReturn {}

    record Failure(int code, String message) implements SysInputReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = AwakeableReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = AwakeableReturn.Failure.class, name = "failure"),
  })
  public sealed interface AwakeableReturn {
    record Ok(String id, int handle) implements AwakeableReturn {}

    record Failure(int code, String message) implements AwakeableReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SysCallReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = SysCallReturn.Failure.class, name = "failure"),
  })
  public sealed interface SysCallReturn {
    record Ok(int invocationIdHandle, int resultHandle) implements SysCallReturn {}

    record Failure(int code, String message) implements SysCallReturn {}
  }

  // =========================================================================
  // Input DTOs (Java → Rust, CBOR maps with camelCase keys)
  // Field names match Rust struct field names after camelCase renaming.
  // =========================================================================

  record VmNotifyError(String message, @Nullable String stacktrace) {}

  record VmNewParameters(List<String[]> headers) {}

  public record VmDoProgressParameters(UnresolvedFuture future) {}

  public record VmSysStateGetParameters(String key) {}

  public record VmSysStateSetParameters(String key, byte[] value) {}

  public record VmSysStateClearParameters(String key) {}

  public record VmSysSleepParameters(
      String name, long wakeUpTimeSinceUnixEpochMillis, long nowSinceUnixEpochMillis) {}

  /** Combined success/failure union — matches Rust {@code NonEmptyValueParam}. */
  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = NonEmptyValueParam.Success.class, name = "success"),
    @JsonSubTypes.Type(value = NonEmptyValueParam.Failure.class, name = "failure"),
  })
  public sealed interface NonEmptyValueParam {
    record Success(byte[] value) implements NonEmptyValueParam {
      public Success(Slice slice) {
        this(slice.toByteArray());
      }
    }

    record Failure(int code, String message, @Nullable List<String[]> metadata)
        implements NonEmptyValueParam {
      public Failure(TerminalException exception) {
        this(
            exception.getCode(),
            exception.getMessage(),
            exception.getMetadata().entrySet().stream()
                .map(e -> new String[] {e.getKey(), e.getValue()})
                .collect(Collectors.toList()));
      }
    }
  }

  public record VmSysCompleteAwakeableParameters(String id, NonEmptyValueParam result) {}

  public record VmSysCallParameters(
      String service,
      String handler,
      @Nullable String key,
      @Nullable String idempotencyKey,
      List<String[]> headers,
      byte[] input) {}

  public record VmSysSendParameters(
      String service,
      String handler,
      @Nullable String key,
      @Nullable String idempotencyKey,
      List<String[]> headers,
      byte[] input,
      @Nullable Long executionTimeSinceUnixEpochMillis) {}

  public record VmSysCancelInvocation(String invocationId) {}

  public record VmSysAttachInvocation(String invocationId) {}

  public record VmSysGetInvocationOutput(String invocationId) {}

  public record VmSysPromiseGetParameters(String key) {}

  public record VmSysPromisePeekParameters(String key) {}

  public record VmSysPromiseCompleteParameters(String id, NonEmptyValueParam result) {}

  public record VmSysRunParameters(String name) {}

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = RunResult.Success.class, name = "success"),
    @JsonSubTypes.Type(value = RunResult.TerminalFailure.class, name = "terminalFailure"),
    @JsonSubTypes.Type(value = RunResult.RetryableFailure.class, name = "retryableFailure"),
  })
  public sealed interface RunResult {
    record Success(byte[] value) implements RunResult {
      public Success(Slice slice) {
        this(slice.toByteArray());
      }
    }

    record TerminalFailure(int code, String message, @Nullable List<String[]> metadata)
        implements RunResult {
      public TerminalFailure(TerminalException exception) {
        this(
            exception.getCode(),
            exception.getMessage(),
            exception.getMetadata().entrySet().stream()
                .map(e -> new String[] {e.getKey(), e.getValue()})
                .collect(Collectors.toList()));
      }
    }

    record RetryableFailure(int code, String message, @Nullable String stacktrace)
        implements RunResult {
      public RetryableFailure(Throwable exception) {
        this(500, exception.getMessage(), stacktraceToString(exception));
      }
    }
  }

  public record VmProposeRunCompletionParameters(
      int handle,
      RunResult result,
      long attemptDurationMillis,
      @Nullable WasmRetryPolicy retryPolicy) {}

  public record WasmRetryPolicy(
      long initialIntervalMillis,
      float factor,
      @Nullable Long maxIntervalMillis,
      @Nullable Integer maxAttempts,
      @Nullable Long maxDurationMillis) {
    public WasmRetryPolicy(RetryPolicy retryPolicy) {
      this(
          retryPolicy.getInitialDelay().toMillis(),
          retryPolicy.getExponentiationFactor(),
          retryPolicy.getMaxDelay() != null ? retryPolicy.getMaxDelay().toMillis() : null,
          retryPolicy.getMaxAttempts(),
          retryPolicy.getMaxDuration() != null ? retryPolicy.getMaxDuration().toMillis() : null);
    }
  }

  public record VmSysCreateSignalHandleParameters(String name) {}

  public record VmSysCompleteSignalParameters(
      String target, String name, NonEmptyValueParam result) {}

  public record VmSysWriteOutputParameters(NonEmptyValueParam result) {}

  private int callWithHandleReturn(
      SharedCoreInstance.QuadFunction<SharedCoreWasm_ModuleExports, Integer, Integer, Integer, Long>
          func,
      Object input) {
    var ret =
        instance.callCborVmFunction(
            (exports, ptr, len) -> func.apply(exports, vmPtr, ptr, len), input, HandleReturn.class);
    if (ret instanceof HandleReturn.Failure f) throw vmError(f.code, f.message);
    return ((HandleReturn.Ok) ret).handle();
  }

  private int callWithHandleReturn(BiFunction<SharedCoreWasm_ModuleExports, Integer, Long> func) {
    var ret =
        instance.callCborVmFunction(exports -> func.apply(exports, vmPtr), HandleReturn.class);
    if (ret instanceof HandleReturn.Failure f) throw vmError(f.code, f.message);
    return ((HandleReturn.Ok) ret).handle();
  }

  private void callWithEmptyReturn(
      SharedCoreInstance.QuadFunction<SharedCoreWasm_ModuleExports, Integer, Integer, Integer, Long>
          func,
      Object input) {
    var ret =
        instance.callCborVmFunction(
            (exports, ptr, len) -> func.apply(exports, vmPtr, ptr, len), input, EmptyReturn.class);
    if (ret instanceof EmptyReturn.Failure f) throw vmError(f.code, f.message);
  }

  private void callWithEmptyReturn(BiFunction<SharedCoreWasm_ModuleExports, Integer, Long> func) {
    var ret = instance.callCborVmFunction(exports -> func.apply(exports, vmPtr), EmptyReturn.class);
    if (ret instanceof EmptyReturn.Failure f) throw vmError(f.code, f.message);
  }

  private static ProtocolException vmError(int code, String message) {
    return new ProtocolException(message, code);
  }

  private static List<String[]> toHeaderList(
      @Nullable Collection<Map.Entry<String, String>> headers) {
    if (headers == null || headers.isEmpty()) return Collections.emptyList();
    return headers.stream().map(e -> new String[] {e.getKey(), e.getValue()}).toList();
  }

  private static String stacktraceToString(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
