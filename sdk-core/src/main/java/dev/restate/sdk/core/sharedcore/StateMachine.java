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
  private final HostBufferRegistry registry;
  private boolean freed;

  /**
   * Volatile mirror of the VM's invocation state, updated on the state-machine thread after every
   * sys_* call via the piggybacked state ordinal in the response.
   */
  private volatile InvocationState cachedState = InvocationState.WAITING_START;

  private StateMachine(SharedCoreInstance instance, int vmPtr, HostBufferRegistry registry) {
    this.instance = instance;
    this.vmPtr = vmPtr;
    this.registry = registry;
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
    return new StateMachine(instance, vmPtr, instance.registry());
  }

  @Override
  public void close() {
    if (!freed) {
      LOG.trace("[vm=0x{}] close()", Integer.toHexString(vmPtr));
      // vmFree drops all of Rust's HostBufferHandles, each of which calls
      // host_buffer_release back into the registry — by the time vmFree
      // returns, every entry this VM owned is released. The registry
      // itself outlives this StateMachine (it's owned by the
      // SharedCoreInstance) and is reused by subsequent StateMachines on
      // the same thread.
      instance.getExports().vmFree(vmPtr);
      freed = true;
      cachedState = InvocationState.CLOSED;
    }
  }

  private void verifyNotFreed() {
    if (freed) {
      AbortedExecutionException.sneakyThrow();
    }
  }

  public void notifyInput(Slice slice) {
    if (freed) {
      return;
    }
    LOG.trace("[vm=0x{}] notifyInput()", Integer.toHexString(vmPtr));
    // Register the Slice directly; Rust takes over the refcount-share
    // via from_parts. The Slice must outlive the registration —
    // contract of the caller.
    int id = registry.register(slice);
    instance.getExports().vmNotifyInput(vmPtr, id, 0, slice.readableBytes());
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
        new VmNotifyError(formatThrowableMessage(throwable), formatThrowableStackTrace(throwable)));
    // notifyError transitions the VM to CLOSED; reflect that in the cached state.
    cachedState = InvocationState.CLOSED;
  }

  /**
   * Drain the next buffer from the VM's output queue. Returns {@code null} when no buffer is
   * currently available (call again later) or the stream has ended. Callers typically loop until
   * {@code null} to drain everything currently queued.
   */
  public @Nullable Slice takeOutput() {
    if (freed) {
      return null;
    }
    LOG.trace("[vm=0x{}] takeOutput()", Integer.toHexString(vmPtr));

    var ret =
        instance.callCborVmFunction(exports -> exports.vmTakeOutput(vmPtr), TakeOutputReturn.class);
    if (ret instanceof TakeOutputReturn.None) {
      return null;
    }
    BufferParam buf = ((TakeOutputReturn.Buffer) ret).buffer();
    return toSlice(buf);
  }

  /**
   * Convert a {@link BufferParam} returned by Rust into a {@link Slice}.
   *
   * <ul>
   *   <li>{@link BufferParam.InMemory} wraps its byte[].
   *   <li>{@link BufferParam.Host} pulls a sub-Slice from the registry and releases the
   *       refcount share (the sub-Slice pins the underlying storage so the bytes stay reachable
   *       after release).
   *   <li>{@link BufferParam.HostMulti} concatenates the segments into a fresh byte[] (one heap
   *       allocation), releases each segment's refcount-share, and wraps the byte[] in a Slice.
   *   <li>{@link BufferParam.Materialised} hands back its Slice.
   * </ul>
   */
  private Slice toSlice(BufferParam buf) {
    if (buf instanceof BufferParam.InMemory inMemory) {
      return Slice.wrap(inMemory.value());
    }
    if (buf instanceof BufferParam.Materialised m) {
      return m.slice();
    }
    if (buf instanceof BufferParam.HostMulti multi) {
      int total = 0;
      for (BufferParam.HostSegment seg : multi.segments()) {
        total += seg.len();
      }
      byte[] out = new byte[total];
      int dstOff = 0;
      for (BufferParam.HostSegment seg : multi.segments()) {
        registry.readInto(seg.id(), seg.offset(), seg.len(), out, dstOff);
        dstOff += seg.len();
        registry.release(seg.id());
      }
      return Slice.wrap(out);
    }
    BufferParam.Host host = (BufferParam.Host) buf;
    Slice sub = registry.slice(host.id(), host.offset(), host.len());
    registry.release(host.id());
    return sub;
  }

  /**
   * Build a {@link BufferParam.Host} by registering {@code slice} with the host buffer registry.
   * The returned handle carries one refcount-share (Java's), which the caller transfers to Rust by
   * passing it across the WASM ABI.
   */
  private BufferParam.Host registerSlice(Slice slice) {
    int id = registry.register(slice);
    return new BufferParam.Host(id, 0, slice.readableBytes());
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

  /**
   * Returns a snapshot of the VM's invocation state. Cheap volatile read — safe to call from any
   * thread; the cached value is kept in sync by every sys_* call (piggybacked on the response) and
   * by {@link #close()}.
   */
  public InvocationState state() {
    return cachedState;
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
    if (ret instanceof TakeNotificationReturn.Value v) {
      NotificationValue value = v.value();
      if (value instanceof NotificationValue.Success success) {
        return new NotificationValue.Success(materialise(success.value()));
      }
      return value;
    }
    TakeNotificationReturn.Failure f = (TakeNotificationReturn.Failure) ret;
    throw vmError(f.code, f.message);
  }

  public Input sysInput() {
    LOG.trace("[vm=0x{}] sysInput()", Integer.toHexString(vmPtr));
    verifyNotFreed();

    var ret =
        instance.callCborVmFunction(exports -> exports.vmSysInput(vmPtr), SysInputReturn.class);
    notifyStateUpdate(ret);
    if (ret instanceof SysInputReturn.Failure f) throw vmError(f.code(), f.message());
    Input wire = ((SysInputReturn.Ok) ret).input();
    BufferParam materialised = materialise(wire.input());
    return new Input(
        wire.invocationId(), wire.key(), wire.headers(), materialised, wire.randomSeed());
  }

  /**
   * Wrap a host-backed {@link BufferParam} ({@link BufferParam.Host} or {@link
   * BufferParam.HostMulti}) into a {@link BufferParam.Materialised} carrying the resulting Slice.
   * {@link BufferParam.InMemory} passes through unchanged. Thin wrapper over {@link #toSlice},
   * used by {@link #sysInput} and {@link #takeNotification} which keep the value as a {@code
   * BufferParam} inside their result records.
   */
  private BufferParam materialise(BufferParam buf) {
    if (buf instanceof BufferParam.Host || buf instanceof BufferParam.HostMulti) {
      return new BufferParam.Materialised(toSlice(buf));
    }
    return buf;
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
        new VmSysStateSetParameters(key, registerSlice(value)));
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
                registerSlice(payload)),
            SysCallReturn.class);
    notifyStateUpdate(ret);
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
            registerSlice(payload),
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
    notifyStateUpdate(ret);
    if (ret instanceof AwakeableReturn.Failure f) {
      throw vmError(f.code(), f.message());
    }
    return (AwakeableReturn.Ok) ret;
  }

  public void sysCompleteAwakeableWithSuccess(String id, Slice payload) {
    sysCompleteAwakeable(id, new NonEmptyValueParam.Success(registerSlice(payload)));
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
    sysCompleteSignal(target, signalName, new NonEmptyValueParam.Success(registerSlice(slice)));
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
    return sysPromiseComplete(key, new NonEmptyValueParam.Success(registerSlice(value)));
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
    if (freed) {
      LOG.trace("Going to ignore completion for handle {} because state machine is closed", handle);
      return;
    }

    callWithEmptyReturn(
        SharedCoreWasm_ModuleExports::vmProposeRunCompletion,
        new VmProposeRunCompletionParameters(
            handle, new RunResult.Success(registerSlice(value)), 0L, null));
  }

  public void proposeRunCompletionTerminalFailure(int handle, TerminalException terminalException) {
    LOG.trace("[vm=0x{}] proposeRunCompletionTerminalFailure()", Integer.toHexString(vmPtr));
    if (freed) {
      LOG.trace("Going to ignore completion for handle {} because state machine is closed", handle);
      return;
    }

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
    if (freed) {
      LOG.trace("Going to ignore completion for handle {} because state machine is closed", handle);
      return;
    }

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
    sysWriteOutput(new NonEmptyValueParam.Success(registerSlice(value)));
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
  // Buffer ABI — wire shape for payloads crossing the WASM boundary.
  // Mirrors Rust's `BufferAbi` enum (sdk-core/src/main/rust/src/lib.rs).
  //
  // Refcount semantics across the boundary:
  //   - Java→Rust: Java has already called `registry.register()` and is
  //     transferring the refcount-share via the (id, offset, len) tuple.
  //     Rust calls `HostBufferHandle::from_parts` (no retain).
  //   - Rust→Java: Rust `mem::forget`s its handle to transfer the share to
  //     Java. Java must `registry.release(id)` when done.
  // =========================================================================

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = BufferParam.InMemory.class, name = "inMemory"),
    @JsonSubTypes.Type(value = BufferParam.Host.class, name = "host"),
    @JsonSubTypes.Type(value = BufferParam.HostMulti.class, name = "hostMulti"),
  })
  public sealed interface BufferParam {
    record InMemory(byte[] value) implements BufferParam {}

    record Host(int id, int offset, int len) implements BufferParam {}

    /**
     * Multi-segment host buffer — used when the Rust decoder coalesces a body that spans multiple
     * input chunks. Materialised once on the Java heap by {@link #toSlice(BufferParam)}; each
     * segment's refcount-share is released after its bytes are copied into the result byte[].
     */
    record HostMulti(List<HostSegment> segments) implements BufferParam {}

    /** Per-segment record for {@link HostMulti}. Mirrors Rust's {@code HostSegmentAbi}. */
    record HostSegment(int id, int offset, int len) {}

    /**
     * Post-materialisation variant carrying a {@link Slice} view over the registered bytes.
     * Internal — never appears in CBOR (no {@code @JsonSubTypes} entry); {@link
     * #materialise(BufferParam)} produces it after pulling the Slice straight from the registry
     * and releasing the refcount share. Subsequent reads ({@code Input.slice()},
     * {@code NotificationValue.Success.slice()}) just hand back this Slice.
     */
    record Materialised(Slice slice) implements BufferParam {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = TakeOutputReturn.Buffer.class, name = "buffer"),
    @JsonSubTypes.Type(value = TakeOutputReturn.None.class, name = "none"),
  })
  public sealed interface TakeOutputReturn {
    record Buffer(BufferParam buffer) implements TakeOutputReturn {}

    record None() implements TakeOutputReturn {}
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
  sealed interface EmptyReturn extends WithState {
    record Ok(int state) implements EmptyReturn {}

    record Failure(int code, String message, int state) implements EmptyReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = HandleReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = HandleReturn.Failure.class, name = "failure"),
  })
  sealed interface HandleReturn extends WithState {
    record Ok(int handle, int state) implements HandleReturn {}

    record Failure(int code, String message, int state) implements HandleReturn {}
  }

  /**
   * Common interface for every sys_* response type — the Rust side piggybacks the current {@link
   * InvocationState} ordinal so the Java side can update its cached state without an extra wasm
   * call.
   */
  interface WithState {
    int state();
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

  /**
   * Notification value payload — matches Rust {@code NotificationValue}.
   *
   * <p>{@link Success#value} is a {@link BufferParam}: CBOR delivers it as either {@link
   * BufferParam.InMemory} or {@link BufferParam.Host}. {@link #takeNotification} materialises any
   * {@code Host} variant into {@link BufferParam.Materialised} (a zero-copy sub-Slice from the
   * registry, with the refcount share released) before returning to user code, so callers can
   * always unwrap via {@link Success#slice}.
   */
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

    record Success(BufferParam value) implements NotificationValue {
      public Slice slice() {
        if (value instanceof BufferParam.InMemory im) {
          return Slice.wrap(im.value());
        }
        if (value instanceof BufferParam.Materialised m) {
          return m.slice();
        }
        throw new IllegalStateException(
            "NotificationValue.Success must be materialised before slice() — "
                + "StateMachine.takeNotification() handles that automatically");
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

  /**
   * Invocation input — returned from {@link #sysInput()}. {@code input} is a {@link BufferParam};
   * {@link #sysInput()} materialises any {@code Host} variant into {@link BufferParam.Materialised}
   * (zero-copy sub-Slice from the registry) before returning, so callers can always unwrap via
   * {@link #slice()}.
   */
  public record Input(
      String invocationId, String key, List<String[]> headers, BufferParam input, long randomSeed) {
    public Map<String, String> headersAsMap() {
      Map<String, String> orderedHeaders = new LinkedHashMap<>();
      if (this.headers() != null) {
        for (var e : this.headers()) orderedHeaders.put(e[0], e[1]);
      }
      return Collections.unmodifiableMap(orderedHeaders);
    }

    /** Unwrap the input payload as a {@link Slice}. Only valid after materialisation. */
    public Slice slice() {
      if (input instanceof BufferParam.InMemory im) {
        return Slice.wrap(im.value());
      }
      if (input instanceof BufferParam.Materialised m) {
        return m.slice();
      }
      throw new IllegalStateException(
          "Input.input must be materialised before slice() — "
              + "StateMachine.sysInput() handles that automatically");
    }
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SysInputReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = SysInputReturn.Failure.class, name = "failure"),
  })
  public sealed interface SysInputReturn extends WithState {
    record Ok(Input input, int state) implements SysInputReturn {}

    record Failure(int code, String message, int state) implements SysInputReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = AwakeableReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = AwakeableReturn.Failure.class, name = "failure"),
  })
  public sealed interface AwakeableReturn extends WithState {
    record Ok(String id, int handle, int state) implements AwakeableReturn {}

    record Failure(int code, String message, int state) implements AwakeableReturn {}
  }

  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SysCallReturn.Ok.class, name = "ok"),
    @JsonSubTypes.Type(value = SysCallReturn.Failure.class, name = "failure"),
  })
  public sealed interface SysCallReturn extends WithState {
    record Ok(int invocationIdHandle, int resultHandle, int state) implements SysCallReturn {}

    record Failure(int code, String message, int state) implements SysCallReturn {}
  }

  // =========================================================================
  // Input DTOs (Java → Rust, CBOR maps with camelCase keys)
  // Field names match Rust struct field names after camelCase renaming.
  // =========================================================================

  record VmNotifyError(String message, @Nullable String stacktrace) {}

  record VmNewParameters(List<String[]> headers) {}

  public record VmDoProgressParameters(UnresolvedFuture future) {}

  public record VmSysStateGetParameters(String key) {}

  public record VmSysStateSetParameters(String key, BufferParam value) {}

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
    record Success(BufferParam value) implements NonEmptyValueParam {}

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
      BufferParam input) {}

  public record VmSysSendParameters(
      String service,
      String handler,
      @Nullable String key,
      @Nullable String idempotencyKey,
      List<String[]> headers,
      BufferParam input,
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
    record Success(BufferParam value) implements RunResult {}

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
        this(500, exception.getMessage(), formatThrowableStackTrace(exception));
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

  /** Updates the cached state from the piggybacked ordinal on a sys_* response. */
  private void notifyStateUpdate(WithState ret) {
    this.cachedState = InvocationState.values()[ret.state()];
  }

  private int callWithHandleReturn(
      SharedCoreInstance.QuadFunction<SharedCoreWasm_ModuleExports, Integer, Integer, Integer, Long>
          func,
      Object input) {
    var ret =
        instance.callCborVmFunction(
            (exports, ptr, len) -> func.apply(exports, vmPtr, ptr, len), input, HandleReturn.class);
    notifyStateUpdate(ret);
    if (ret instanceof HandleReturn.Failure f) throw vmError(f.code, f.message);
    return ((HandleReturn.Ok) ret).handle();
  }

  private int callWithHandleReturn(BiFunction<SharedCoreWasm_ModuleExports, Integer, Long> func) {
    var ret =
        instance.callCborVmFunction(exports -> func.apply(exports, vmPtr), HandleReturn.class);
    notifyStateUpdate(ret);
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
    notifyStateUpdate(ret);
    if (ret instanceof EmptyReturn.Failure f) throw vmError(f.code, f.message);
  }

  private void callWithEmptyReturn(BiFunction<SharedCoreWasm_ModuleExports, Integer, Long> func) {
    var ret = instance.callCborVmFunction(exports -> func.apply(exports, vmPtr), EmptyReturn.class);
    notifyStateUpdate(ret);
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

  private static String formatThrowableMessage(Throwable throwable) {
    String message = throwable.getMessage();
    if (message == null) return throwable.getClass().getName();
    return message;
  }

  private static String formatThrowableStackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
