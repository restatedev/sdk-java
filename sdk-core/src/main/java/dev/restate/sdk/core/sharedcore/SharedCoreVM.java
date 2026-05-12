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
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public final class SharedCoreVM implements AutoCloseable {

  private static final Logger LOG = LogManager.getLogger(SharedCoreVM.class);

  private final SharedCoreInstance instance;
  private final int vmPtr;
  private boolean closed;

  private SharedCoreVM(SharedCoreInstance instance, int vmPtr) {
    this.instance = instance;
    this.vmPtr = vmPtr;
    this.closed = false;
  }

  // -------------------------------------------------------------------------
  // Factory
  // -------------------------------------------------------------------------

  public static SharedCoreVM create(HeadersAccessor headersAccessor) {
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
    return new SharedCoreVM(instance, vmPtr);
  }

  @Override
  public void close() {
    if (!closed) {
      LOG.trace("[vm=0x{}] close()", Integer.toHexString(vmPtr));
      instance.getExports().vmFree(vmPtr);
      closed = true;
    }
  }

  private void verifyNotClosed() {
    if (closed) {
      throw new IllegalStateException("Attempting to use the VM when is closed");
    }
  }

  public void notifyInput(byte[] bytes) {
    if (closed) {
      return;
    }
    LOG.trace("[vm=0x{}] notifyInput()", Integer.toHexString(vmPtr));
    var bufferPtr = instance.write(bytes);
    instance.getExports().vmNotifyInput(vmPtr, bufferPtr, bytes.length);
  }

  public void notifyInputClosed() {
    if (closed) {
      return;
    }
    LOG.trace("[vm=0x{}] notifyInputClosed()", Integer.toHexString(vmPtr));
    instance.getExports().vmNotifyInputClosed(vmPtr);
  }

  public void notifyError(
      String message, @Nullable String stacktrace, @Nullable Long delayOverrideMillis) {
    if (closed) {
      return;
    }
    LOG.trace("[vm=0x{}] notifyError()", Integer.toHexString(vmPtr));
    instance.callCborVmFunction(
        (exports, ptr, len) -> exports.vmNotifyError(vmPtr, ptr, len),
        new VmNotifyError(message, stacktrace, delayOverrideMillis));
  }

  public byte[] takeOutput() {
    if (closed) {
      return new byte[0];
    }
    LOG.trace("[vm=0x{}] takeOutput()", Integer.toHexString(vmPtr));

    var ptr = instance.getExports().vmTakeOutput(vmPtr);
    return instance.readAndFree(ptr);
  }

  public String getResponseContentType() {
    LOG.trace("[vm=0x{}] getResponseContentType()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    // TODO change this to just return headers back bro
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
    verifyNotClosed();

    var ret =
        instance.callCborVmFunction(
            exports -> exports.vmIsReadyToExecute(vmPtr), IsReadyReturn.class);
    if (ret instanceof IsReadyReturn.Failure f) throw vmError(f.code, f.message);
    return ((IsReadyReturn.Ok) ret).ready();
  }

  public boolean isCompleted(int handle) {
    LOG.trace("[vm=0x{}] isCompleted()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    return instance.getExports().vmIsCompleted(vmPtr, handle) != 0L;
  }

  public DoProgressResult doProgress(int[] handles) {
    LOG.trace("[vm=0x{}] doProgress()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    var ret =
        instance.callCborVmFunction(
            (exports, ptr, len) -> exports.vmDoProgress(vmPtr, ptr, len),
            new VmDoProgressParameters(handles),
            DoProgressReturn.class);
    if (ret instanceof DoProgressReturn.AnyCompleted) return DoProgressResult.ANY_COMPLETED;
    if (ret instanceof DoProgressReturn.WaitingExternalProgress)
      return DoProgressResult.WAIT_EXTERNAL_PROGRESS;
    if (ret instanceof DoProgressReturn.CancelSignalReceived)
      return DoProgressResult.CANCEL_SIGNAL_RECEIVED;
    if (ret instanceof DoProgressReturn.ExecuteRun r)
      return new DoProgressResult.ExecuteRun(r.handle());
    if (ret instanceof DoProgressReturn.Suspended) return DoProgressResult.SUSPENDED;
    DoProgressReturn.Failure f = (DoProgressReturn.Failure) ret;
    throw vmError(f.code, f.message);
  }

  public @Nullable NotificationValue takeNotification(int handle) {
    LOG.trace("[vm=0x{}] takeNotification()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    var ret =
        instance.callCborVmFunction(
            exports -> exports.vmTakeNotification(vmPtr, handle), TakeNotificationReturn.class);
    if (ret instanceof TakeNotificationReturn.NotReady) return null;
    if (ret instanceof TakeNotificationReturn.Suspended) return null;
    if (ret instanceof TakeNotificationReturn.Value v) return v.value();
    TakeNotificationReturn.Failure f = (TakeNotificationReturn.Failure) ret;
    throw vmError(f.code, f.message);
  }

  public Input sysInput() {
    LOG.trace("[vm=0x{}] sysInput()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    var ret =
        instance.callCborVmFunction(exports -> exports.vmSysInput(vmPtr), SysInputReturn.class);
    if (ret instanceof SysInputReturn.Failure f) throw vmError(f.code(), f.message());
    WasmInput w = ((SysInputReturn.Ok) ret).input();
    List<Map.Entry<String, String>> hdrs = new ArrayList<>();
    if (w.headers() != null) {
      for (String[] pair : w.headers()) hdrs.add(Map.entry(pair[0], pair[1]));
    }
    return new Input(
        w.invocationId(), w.randomSeed(), w.key(), Collections.unmodifiableList(hdrs), w.input());
  }

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------

  public int sysStateGet(String key) {
    LOG.trace("[vm=0x{}] sysStateGet()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysStateGet, new VmSysStateGetParameters(key));
  }

  public int sysStateGetKeys() {
    LOG.trace("[vm=0x{}] sysStateGetKeys()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    return callWithHandleReturn(SharedCoreWasm_ModuleExports::vmSysStateGetKeys);
  }

  public void sysStateSet(String key, byte[] value) {
    LOG.trace("[vm=0x{}] sysStateSet()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysStateSet, new VmSysStateSetParameters(key, value));
  }

  public void sysStateClear(String key) {
    LOG.trace("[vm=0x{}] sysStateClear()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysStateClear, new VmSysStateClearParameters(key));
  }

  public void sysStateClearAll() {
    LOG.trace("[vm=0x{}] sysStateClearAll()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(SharedCoreWasm_ModuleExports::vmSysStateClearAll);
  }

  // -------------------------------------------------------------------------
  // Sleep
  // -------------------------------------------------------------------------

  public int sysSleep(long durationMillis, @Nullable String name) {
    LOG.trace("[vm=0x{}] sysSleep()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    long now = System.currentTimeMillis();
    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysSleep,
        new VmSysSleepParameters(name != null ? name : "", now + durationMillis, now));
  }

  // -------------------------------------------------------------------------
  // Call / Send
  // -------------------------------------------------------------------------

  public CallHandleResult sysCall(
      String service,
      String handler,
      @Nullable String key,
      byte[] payload,
      @Nullable String idempotencyKey,
      @Nullable List<Map.Entry<String, String>> extraHeaders) {
    LOG.trace("[vm=0x{}] sysCall()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    var ret =
        instance.callCborVmFunction(
            (exports, ptr, len) -> exports.vmSysCall(vmPtr, ptr, len),
            new VmSysCallParameters(
                service, handler, key, idempotencyKey, toHeaderList(extraHeaders), payload),
            SysCallReturn.class);
    if (ret instanceof SysCallReturn.Failure f) throw vmError(f.code(), f.message());
    SysCallReturn.Ok ok = (SysCallReturn.Ok) ret;
    return new CallHandleResult(ok.invocationIdHandle(), ok.resultHandle());
  }

  public int sysSend(
      String service,
      String handler,
      @Nullable String key,
      byte[] payload,
      @Nullable String idempotencyKey,
      @Nullable List<Map.Entry<String, String>> extraHeaders,
      @Nullable Long delayMillis) {
    LOG.trace("[vm=0x{}] sysSend()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    Long executionTime = delayMillis != null ? System.currentTimeMillis() + delayMillis : null;
    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysSend,
        new VmSysSendParameters(
            service,
            handler,
            key,
            idempotencyKey,
            toHeaderList(extraHeaders),
            payload,
            executionTime));
  }

  // -------------------------------------------------------------------------
  // Awakeables
  // -------------------------------------------------------------------------

  public AwakeableResult sysAwakeable() {
    LOG.trace("[vm=0x{}] sysAwakeable()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    AwakeableReturn ret =
        instance.callCborVmFunction(
            (exports) -> exports.vmSysAwakeable(vmPtr), AwakeableReturn.class);
    if (ret instanceof AwakeableReturn.Failure f) throw vmError(f.code(), f.message());
    AwakeableReturn.Ok ok = (AwakeableReturn.Ok) ret;
    return new AwakeableResult(ok.handle(), ok.id());
  }

  public void sysCompleteAwakeable(String id, NonEmptyValueParam result) {
    LOG.trace("[vm=0x{}] sysCompleteAwakeable()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysCompleteAwakeable,
        new VmSysCompleteAwakeableParameters(id, result));
  }

  public void sysCompleteAwakeableSuccess(String id, byte[] value) {
    sysCompleteAwakeable(id, new NonEmptyValueParam.Success(value));
  }

  public void sysCompleteAwakeableFailure(String id, int code, String message) {
    sysCompleteAwakeable(id, new NonEmptyValueParam.Failure(code, message, null));
  }

  public void sysCompleteAwakeableFailure(
      String id, int code, String message, @Nullable List<String[]> metadata) {
    sysCompleteAwakeable(id, new NonEmptyValueParam.Failure(code, message, metadata));
  }

  public int sysCreateSignalHandle(String signalName) {
    LOG.trace("[vm=0x{}] sysCreateSignalHandle()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysCreateSignalHandle,
        new VmSysCreateSignalHandleParameters(signalName));
  }

  public void sysCompleteSignal(String target, String signalName, NonEmptyValueParam result) {
    LOG.trace("[vm=0x{}] sysCompleteSignal()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysCompleteSignal,
        new VmSysCompleteSignalParameters(target, signalName, result));
  }

  public void sysCompleteSignalSuccess(String target, String signalName, byte[] value) {
    sysCompleteSignal(target, signalName, new NonEmptyValueParam.Success(value));
  }

  public void sysCompleteSignalFailure(String target, String signalName, int code, String message) {
    sysCompleteSignal(target, signalName, new NonEmptyValueParam.Failure(code, message, null));
  }

  public void sysCompleteSignalFailure(
      String target,
      String signalName,
      int code,
      String message,
      @Nullable List<String[]> metadata) {
    sysCompleteSignal(target, signalName, new NonEmptyValueParam.Failure(code, message, metadata));
  }

  public int sysPromiseGet(String key) {
    LOG.trace("[vm=0x{}] sysPromiseGet()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysPromiseGet, new VmSysPromiseGetParameters(key));
  }

  public int sysPromisePeek(String key) {
    LOG.trace("[vm=0x{}] sysPromisePeek()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysPromisePeek, new VmSysPromisePeekParameters(key));
  }

  public int sysPromiseComplete(String key, NonEmptyValueParam result) {
    LOG.trace("[vm=0x{}] sysPromiseComplete()", Integer.toHexString(vmPtr));
    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysPromiseComplete,
        new VmSysPromiseCompleteParameters(key, result));
  }

  public int sysPromiseCompleteSuccess(String key, byte[] value) {
    return sysPromiseComplete(key, new NonEmptyValueParam.Success(value));
  }

  public int sysPromiseCompleteFailure(String key, int code, String message) {
    return sysPromiseComplete(key, new NonEmptyValueParam.Failure(code, message, null));
  }

  public int sysPromiseCompleteFailure(
      String key, int code, String message, @Nullable List<String[]> metadata) {
    return sysPromiseComplete(key, new NonEmptyValueParam.Failure(code, message, metadata));
  }

  public int sysRun(String name) {
    LOG.trace("[vm=0x{}] sysRun()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysRun, new VmSysRunParameters(name));
  }

  public void proposeRunCompletionSuccess(int handle, byte[] value) {
    LOG.trace("[vm=0x{}] proposeRunCompletionSuccess()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmProposeRunCompletion,
        new VmProposeRunCompletionParameters(handle, new RunResult.Success(value), 0L, null));
  }

  public void proposeRunCompletionTerminalFailure(
      int handle, int code, String message, @Nullable List<String[]> metadata) {
    LOG.trace("[vm=0x{}] proposeRunCompletionTerminalFailure()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmProposeRunCompletion,
        new VmProposeRunCompletionParameters(
            handle, new RunResult.TerminalFailure(code, message, metadata), 0L, null));
  }

  public void proposeRunCompletionRetryableFailure(
      int handle,
      int code,
      String message,
      @Nullable String stacktrace,
      long attemptDurationMillis,
      @Nullable WasmRetryPolicy retryPolicy) {
    LOG.trace("[vm=0x{}] proposeRunCompletionRetryableFailure()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmProposeRunCompletion,
        new VmProposeRunCompletionParameters(
            handle,
            new RunResult.RetryableFailure(code, message, stacktrace),
            attemptDurationMillis,
            retryPolicy));
  }

  public void sysCancelInvocation(String invocationId) {
    LOG.trace("[vm=0x{}] sysCancelInvocation()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysCancelInvocation,
        new VmSysCancelInvocation(invocationId));
  }

  public int sysAttachInvocation(String invocationId) {
    LOG.trace("[vm=0x{}] sysAttachInvocation()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysAttachInvocation,
        new VmSysAttachInvocation(invocationId));
  }

  public int sysGetInvocationOutput(String invocationId) {
    LOG.trace("[vm=0x{}] sysGetInvocationOutput()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    return callWithHandleReturn(
        SharedCoreWasm_ModuleExports::vmSysGetInvocationOutput,
        new VmSysGetInvocationOutput(invocationId));
  }

  public void sysWriteOutput(NonEmptyValueParam result) {
    LOG.trace("[vm=0x{}] sysWriteOutput()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmSysWriteOutput, new VmSysWriteOutputParameters(result));
  }

  public void sysWriteOutputSuccess(byte[] value) {
    sysWriteOutput(new NonEmptyValueParam.Success(value));
  }

  public void sysWriteOutputFailure(int code, String message) {
    sysWriteOutput(new NonEmptyValueParam.Failure(code, message, null));
  }

  public void sysWriteOutputFailure(int code, String message, @Nullable List<String[]> metadata) {
    sysWriteOutput(new NonEmptyValueParam.Failure(code, message, metadata));
  }

  public void sysEnd() {
    LOG.trace("[vm=0x{}] sysEnd()", Integer.toHexString(vmPtr));
    verifyNotClosed();

    callWithEmptyReturn(SharedCoreWasm_ModuleExports::vmSysEnd);
  }

  // =========================================================================
  // Result types (returned to callers of this class)
  // =========================================================================

  public sealed interface DoProgressResult {
    DoProgressResult ANY_COMPLETED = new AnyCompleted();
    DoProgressResult WAIT_EXTERNAL_PROGRESS = new WaitExternalProgress();
    DoProgressResult CANCEL_SIGNAL_RECEIVED = new CancelSignalReceived();
    DoProgressResult SUSPENDED = new Suspended();

    record AnyCompleted() implements DoProgressResult {}

    record WaitExternalProgress() implements DoProgressResult {}

    record ExecuteRun(int handle) implements DoProgressResult {}

    record CancelSignalReceived() implements DoProgressResult {}

    record Suspended() implements DoProgressResult {}
  }

  // =========================================================================
  // CBOR response types (decoded from WASM responses)
  // =========================================================================

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = VmNewReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = VmNewReturn.Failure.class, name = "failure"),
  })
  public sealed interface VmNewReturn {
    record Ok(int pointer) implements VmNewReturn {}

    record Failure(int code, String message) implements VmNewReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = EmptyReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = EmptyReturn.Failure.class, name = "failure"),
  })
  public sealed interface EmptyReturn {
    record Ok() implements EmptyReturn {}

    record Failure(int code, String message) implements EmptyReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = HandleReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = HandleReturn.Failure.class, name = "failure"),
  })
  public sealed interface HandleReturn {
    record Ok(int handle) implements HandleReturn {}

    record Failure(int code, String message) implements HandleReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = IsReadyReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = IsReadyReturn.Failure.class, name = "failure"),
  })
  public sealed interface IsReadyReturn {
    record Ok(boolean ready) implements IsReadyReturn {}

    record Failure(int code, String message) implements IsReadyReturn {}
  }

  /** Plain struct — mirrors Go's VmGetResponseHeadReturn (no Ok/Failure wrapper). */
  public record ResponseHeadReturn(int statusCode, List<String[]> headers) {}

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

    record Success(byte[] value) implements NotificationValue {}

    record Failure(int code, String message, @Nullable List<String[]> metadata)
        implements NotificationValue {}

    record StateKeys(List<String> keys) implements NotificationValue {}

    record InvocationId(String id) implements NotificationValue {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = TakeNotificationReturn.NotReady.class, name = "notReady"),
    @JsonSubTypes.Type(value = TakeNotificationReturn.Value.class, name = "value"),
    @JsonSubTypes.Type(value = TakeNotificationReturn.Suspended.class, name = "suspended"),
    @JsonSubTypes.Type(value = TakeNotificationReturn.Failure.class, name = "failure"),
  })
  public sealed interface TakeNotificationReturn {
    record NotReady() implements TakeNotificationReturn {}

    record Value(NotificationValue value) implements TakeNotificationReturn {}

    record Suspended() implements TakeNotificationReturn {}

    record Failure(int code, String message) implements TakeNotificationReturn {}
  }

  public record WasmInput(
      String invocationId,
      String key,
      List<String[]> headers,
      byte[] input,
      long randomSeed,
      boolean shouldUseRandomSeed) {}

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SysInputReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = SysInputReturn.Failure.class, name = "failure"),
  })
  public sealed interface SysInputReturn {
    record Ok(WasmInput input) implements SysInputReturn {}

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

  public record VmNotifyError(
      String message, @Nullable String stacktrace, @Nullable Long delayOverrideMillis) {}

  public record VmNewParameters(List<String[]> headers) {}

  public record VmDoProgressParameters(int[] handles) {}

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
    record Success(byte[] value) implements NonEmptyValueParam {}

    record Failure(int code, String message, @Nullable List<String[]> metadata)
        implements NonEmptyValueParam {}
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
    record Success(byte[] value) implements RunResult {}

    record TerminalFailure(int code, String message, @Nullable List<String[]> metadata)
        implements RunResult {}

    record RetryableFailure(int code, String message, @Nullable String stacktrace)
        implements RunResult {}
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
      @Nullable Long maxDurationMillis) {}

  public record VmSysCreateSignalHandleParameters(String name) {}

  public record VmSysCompleteSignalParameters(
      String target, String name, NonEmptyValueParam result) {}

  public record VmSysWriteOutputParameters(NonEmptyValueParam result) {}

  // =========================================================================
  // Result value types returned to WasmStateMachineImpl
  // =========================================================================

  public record Input(
      String invocationId,
      long randomSeed,
      String key,
      List<Map.Entry<String, String>> headers,
      byte[] body) {}

  public record CallHandleResult(int invocationIdHandle, int resultHandle) {}

  public record AwakeableResult(int signalHandle, String awakeableId) {}

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

  private static List<String[]> toHeaderList(@Nullable List<Map.Entry<String, String>> headers) {
    if (headers == null || headers.isEmpty()) return Collections.emptyList();
    List<String[]> r = new ArrayList<>(headers.size());
    for (Map.Entry<String, String> e : headers) r.add(new String[] {e.getKey(), e.getValue()});
    return r;
  }
}
