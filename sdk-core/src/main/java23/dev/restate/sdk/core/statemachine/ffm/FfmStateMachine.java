// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine.ffm;

import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.StateMachine;
import dev.restate.sdk.core.UnresolvedFuture;
import dev.restate.sdk.core.statemachine.ffm.generated.*;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Panama/FFM (JDK 23+) implementation of the canonical {@link StateMachine}, driving the native
 * {@code restate-sdk-shared-core} library through the jextract-generated {@code SharedCoreNative}
 * bindings, over the C ABI documented in {@code sdk-core/src/main/rust/src/lib.rs}.
 *
 * <p>Each call is a direct FFM downcall. Results come back either register-packed into the scalar
 * return value (the invocation state, plus a handle for handle-returning calls) or, when too wide
 * to pack, written into a caller-provided out-param struct whose first field is the state. Owned
 * {@link Slice}s are copied out and freed by the caller. On the {@link
 * SharedCoreNative#ERROR_STATE()} sentinel the core has already written its terminal message and
 * closed, so we sneaky-throw {@link AbortedExecutionException} ({@link #abortAfterCoreClosed()});
 * construction and {@code isReadyToExecute} errors surface as a {@link ProtocolException}.
 *
 * <p>An instance is driven by a single thread at a time (no reentrancy, per the contract); only
 * {@link #state()} — a volatile read — is safe from any thread. Each downcall allocates its
 * out-param and inputs in a confined {@link Arena} for the duration of the call.
 */
public final class FfmStateMachine implements StateMachine {

  private final MemorySegment vmHandle;
  private final String responseContentType;
  private boolean freed = false;

  /**
   * Volatile mirror of the VM's invocation state, updated on the state-machine thread after every
   * call from the piggybacked {@code state} ordinal in the result struct.
   */
  private volatile InvocationState cachedState = InvocationState.WAITING_START;

  static {
    // Load the native library BEFORE SharedCoreNative is class-initialized so its
    // SymbolLookup.loaderLookup() resolves the vm_* symbols.
    NativeLibraryLoader.ensureLoaded();
    // Install the native tracing subscriber: filter at the host's configured level (so disabled
    // callsites short-circuit in the core) and forward surviving events to Log4j2 via an upcall.
    SharedCoreNative.init(NativeLogging.abiLevel(), NativeLogging.callbackStub());
  }

  public FfmStateMachine(HeadersAccessor headersAccessor) {
    List<String[]> headers = new ArrayList<>();
    for (String key : headersAccessor.keys()) {
      String value = headersAccessor.get(key);
      if (value != null) {
        headers.add(new String[] {key, value});
      }
    }

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = VmNewResult.allocate(arena);

      SharedCoreNative.vm_new(FfmEncoding.foreignHeaderPairs(arena, headers), out);
      if (VmNewResult.tag(out) == SharedCoreNative.VmNewResult_Err()) {
        throw closeVmAndMapError(VmNewResult_Err_Body.error(VmNewResult.err(out)));
      }
      this.vmHandle = VmNewResult_Ok_Body.handle(VmNewResult.ok(out));
      this.responseContentType =
          FfmEncoding.takeSliceString(
              VmNewResult_Ok_Body.response_content_type(VmNewResult.ok(out)));
    }
  }

  // -------------------------------------------------------------------------
  // Lifecycle & I/O
  // -------------------------------------------------------------------------

  @Override
  public void notifyInput(Slice bytes) {
    if (freed) {
      return;
    }
    // Ownership transfer (see the ownership docs in rust/src/lib.rs): the payload is an
    // alloc_buffer-backed buffer the native call re-owns (the core retains it zero-copy, frees it
    // when done). The by-value Slice wrapper {ptr,len} lives in the confined arena just for the
    // call.
    try (Arena arena = Arena.ofConfined()) {
      SharedCoreNative.vm_notify_input(vmHandle, FfmEncoding.transferToSlice(arena, bytes));
    }
  }

  @Override
  public void notifyInputClosed() {
    if (freed) {
      return;
    }
    SharedCoreNative.vm_notify_input_closed(vmHandle);
  }

  @Override
  public void notifyError(Throwable throwable) {
    if (freed || cachedState == InvocationState.CLOSED) {
      return;
    }
    // Ownership transfer (see the ownership docs in rust/src/lib.rs): message/stacktrace are
    // alloc_buffer-backed buffers the native call re-owns as Strings (zero-copy, UTF-8 unchecked).
    // The by-value Slice wrappers live in the confined arena just for the call.
    try (Arena arena = Arena.ofConfined()) {
      SharedCoreNative.vm_notify_error(
          vmHandle,
          FfmEncoding.transferUtf8ToSlice(arena, formatThrowableMessage(throwable)),
          FfmEncoding.transferUtf8ToSlice(arena, formatThrowableStackTrace(throwable)));
    }
    // notify_error transitions the VM to CLOSED; reflect that in the cached state.
    cachedState = InvocationState.CLOSED;
  }

  @Override
  public Slice takeOutput() {
    if (freed) {
      return Slice.EMPTY;
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = FfmEncoding.allocateSliceStruct(arena);
      SharedCoreNative.vm_take_output(vmHandle, out);
      return FfmEncoding.wrapOwnedSlice(out);
    }
  }

  @Override
  public String getResponseContentType() {
    return responseContentType;
  }

  @Override
  public boolean isReadyToExecute() {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = IsReadyToExecuteResult.allocate(arena);
      SharedCoreNative.vm_is_ready_to_execute(vmHandle, out);
      if (IsReadyToExecuteResult.tag(out) == SharedCoreNative.IsReadyToExecuteResult_Err()) {
        throw closeVmAndMapError(
            IsReadyToExecuteResult_Err_Body.error(IsReadyToExecuteResult.err(out)));
      }
      MemorySegment ok = IsReadyToExecuteResult.ok(out);
      return IsReadyToExecuteResult_Ok_Body.value(ok) != 0;
    }
  }

  @Override
  public InvocationState state() {
    return cachedState;
  }

  @Override
  public AwaitResult doAwait(UnresolvedFuture future) {
    verifyNotFreed();
    long packed;
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment futureSeg = FfmEncoding.foreignAwaitTree(arena, future);
      if (futureSeg == null) {
        // Everything already resolved: nothing to await.
        return AwaitResult.ANY_COMPLETED;
      }
      packed = SharedCoreNative.vm_do_await(vmHandle, futureSeg);
    }

    // Register-packed u64: low byte = variant tag, high 32 bits = ExecuteRun handle. The ERROR
    // variant also covers suspension (the core folds it in); either way the core already wrote its
    // terminal message and closed, so we abort cleanly rather than fetch the discarded detail.
    int variant = (int) packed;
    if (variant == SharedCoreNative.AWAIT_VARIANT_ANY_COMPLETED()) {
      return AwaitResult.ANY_COMPLETED;
    } else if (variant == SharedCoreNative.AWAIT_VARIANT_WAITING_EXTERNAL_PROGRESS()) {
      return AwaitResult.WAIT_EXTERNAL_PROGRESS;
    } else if (variant == SharedCoreNative.AWAIT_VARIANT_EXECUTE_RUN()) {
      return new AwaitResult.ExecuteRun((int) (packed >>> 32));
    } else if (variant == SharedCoreNative.AWAIT_VARIANT_CANCEL_SIGNAL_RECEIVED()) {
      return AwaitResult.CANCEL_SIGNAL_RECEIVED;
    } else if (variant == SharedCoreNative.AWAIT_VARIANT_ERROR()) {
      abortAfterCoreClosed();
    }
    throw new IllegalStateException("Unknown do_await variant: " + variant);
  }

  @Override
  public @Nullable NotificationValue takeNotification(int handle) {
    verifyNotFreed();
    int variant = SharedCoreNative.vm_take_notification(vmHandle, handle);
    if (variant == SharedCoreNative.NotificationVariant_NotReady()) {
      return null;
    } else if (variant == SharedCoreNative.NotificationVariant_Empty()) {
      return NotificationValue.Empty.INSTANCE;
    } else if (variant == SharedCoreNative.NotificationVariant_Success()) {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment out = FfmEncoding.allocateSliceStruct(arena);
        SharedCoreNative.vm_take_notification_success(vmHandle, out);
        // Handed over zero-copy (GC-tied).
        return new NotificationValue.Success(FfmEncoding.wrapOwnedSlice(out));
      }
    } else if (variant == SharedCoreNative.NotificationVariant_TerminalFailure()) {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment out = AbiTerminalFailure.allocate(arena);
        SharedCoreNative.vm_take_notification_terminal_failure(vmHandle, out);
        return new NotificationValue.Failure(
            new TerminalException(
                AbiTerminalFailure.code(out),
                FfmEncoding.takeSliceString(AbiTerminalFailure.message(out)),
                decodeMetadataMap(FfmEncoding.takeSliceBytes(AbiTerminalFailure.metadata(out)))));
      }
    } else if (variant == SharedCoreNative.NotificationVariant_StateKeys()) {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment out = FfmEncoding.allocateSliceStruct(arena);
        SharedCoreNative.vm_take_notification_state_keys(vmHandle, out);
        return new NotificationValue.StateKeys(decodeStringList(FfmEncoding.takeSliceBytes(out)));
      }
    } else if (variant == SharedCoreNative.NotificationVariant_InvocationId()) {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment out = FfmEncoding.allocateSliceStruct(arena);
        SharedCoreNative.vm_take_notification_invocation_id(vmHandle, out);
        return new NotificationValue.InvocationId(FfmEncoding.takeSliceString(out));
      }
    }
    throw new IllegalStateException("Unknown notification variant: " + variant);
  }

  private static Map<String, String> decodeMetadataMap(byte[] extra) {
    if (extra.length == 0) {
      return Map.of();
    }
    Map<String, String> metadata = new LinkedHashMap<>();
    ByteBuffer buf = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN);
    int count = buf.getInt();
    for (int i = 0; i < count; i++) {
      metadata.put(FfmEncoding.readString(buf), FfmEncoding.readString(buf));
    }
    return metadata;
  }

  private static List<String> decodeStringList(byte[] extra) {
    if (extra.length == 0) {
      return new ArrayList<>();
    }
    ByteBuffer buf = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN);
    int count = buf.getInt();
    List<String> keys = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      keys.add(FfmEncoding.readString(buf));
    }
    return keys;
  }

  @Override
  public Input input() {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = InputResult.allocate(arena);
      SharedCoreNative.vm_sys_input(vmHandle, out);
      int state = InputResult.state(out);
      if (state == SharedCoreNative.ERROR_STATE()) {
        abortAfterCoreClosed();
      }
      updateState(state);

      // The handler input payload is handed over zero-copy (GC-tied, freed via free_buffer when the
      // slice is collected) and propagated straight to the user deserialization layer.
      Slice input = FfmEncoding.wrapOwnedSlice(InputResult.input(out));

      // Everything else is copied out anyway, so the core packs it into one metadata blob (one
      // alloc
      // + one free instead of seven). Layout mirrors the InputResult doc: random_seed(u64),
      // invocation_id(str), key(str), headers(u32 count, count*(str,str)), then
      // scope/limit_key/idempotency_key as (u8 present, [str]).
      ByteBuffer meta =
          ByteBuffer.wrap(FfmEncoding.takeSliceBytes(InputResult.metadata(out)))
              .order(ByteOrder.LITTLE_ENDIAN);
      long randomSeed = meta.getLong();
      String invocationId = FfmEncoding.readString(meta);
      String key = FfmEncoding.readString(meta);
      int hcount = meta.getInt();
      List<String[]> headers = new ArrayList<>(hcount);
      for (int i = 0; i < hcount; i++) {
        headers.add(new String[] {FfmEncoding.readString(meta), FfmEncoding.readString(meta)});
      }
      String scope = readOptString(meta);
      String limitKey = readOptString(meta);
      String idempotencyKey = readOptString(meta);

      return new Input(
          invocationId, key, headers, input, randomSeed, scope, limitKey, idempotencyKey);
    }
  }

  /** Reads an optional string encoded as {@code u8 present, [u32 len, bytes]}. */
  private static @Nullable String readOptString(ByteBuffer buf) {
    return buf.get() == 0 ? null : FfmEncoding.readString(buf);
  }

  @Override
  public void close() {
    if (!freed) {
      try {
        SharedCoreNative.vm_free(vmHandle);
      } catch (Throwable ignored) {
        // best-effort cleanup
      }
      freed = true;
      cachedState = InvocationState.CLOSED;
    }
  }

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------

  @Override
  public int stateGet(String key) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      return parseHandleResult(
          SharedCoreNative.vm_sys_state_get(vmHandle, FfmEncoding.foreignUtf8(arena, key)));
    }
  }

  @Override
  public int stateGetKeys() {
    verifyNotFreed();
    return parseHandleResult(SharedCoreNative.vm_sys_state_get_keys(vmHandle));
  }

  @Override
  public void stateSet(String key, Slice value) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_sys_state_set(
              vmHandle,
              FfmEncoding.foreignUtf8(arena, key),
              FfmEncoding.transferToSlice(arena, value)));
    }
  }

  @Override
  public void stateClear(String key) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_sys_state_clear(vmHandle, FfmEncoding.foreignUtf8(arena, key)));
    }
  }

  @Override
  public void stateClearAll() {
    verifyNotFreed();
    consumeEmptyResult(SharedCoreNative.vm_sys_state_clear_all(vmHandle));
  }

  // -------------------------------------------------------------------------
  // Sleep
  // -------------------------------------------------------------------------

  @Override
  public int sleep(Duration duration, @Nullable String name) {
    verifyNotFreed();
    long now = System.currentTimeMillis();
    try (Arena arena = Arena.ofConfined()) {
      return parseHandleResult(
          SharedCoreNative.vm_sys_sleep(
              vmHandle,
              FfmEncoding.foreignUtf8(arena, name != null ? name : ""),
              now + duration.toMillis(),
              now));
    }
  }

  // -------------------------------------------------------------------------
  // Call / send
  // -------------------------------------------------------------------------

  @Override
  public CallHandle call(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment args =
          FfmEncoding.buildCallArguments(
              arena, target, payload, idempotencyKey, scope, limitKey, headers);
      MemorySegment out = CallResult.allocate(arena);
      SharedCoreNative.vm_sys_call(vmHandle, args, out);
      int state = CallResult.state(out);
      if (state == SharedCoreNative.ERROR_STATE()) {
        abortAfterCoreClosed();
      }
      updateState(state);
      return new CallHandle(CallResult.invocation_id_handle(out), CallResult.result_handle(out));
    }
  }

  @Override
  public int send(
      Target target,
      Slice payload,
      @Nullable String idempotencyKey,
      @Nullable String scope,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    verifyNotFreed();
    // 0 means "no delay"; otherwise the absolute wake-up time in epoch millis.
    long delayMillis =
        delay != null && !delay.isZero() ? System.currentTimeMillis() + delay.toMillis() : 0L;
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment args =
          FfmEncoding.buildCallArguments(
              arena, target, payload, idempotencyKey, scope, limitKey, headers);
      return parseHandleResult(SharedCoreNative.vm_sys_send(vmHandle, args, delayMillis));
    }
  }

  // -------------------------------------------------------------------------
  // Awakeables, signals & promises
  // -------------------------------------------------------------------------

  @Override
  public Awakeable awakeable() {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = AwakeableResult.allocate(arena);
      SharedCoreNative.vm_sys_awakeable(vmHandle, out);
      int state = AwakeableResult.state(out);
      if (state == SharedCoreNative.ERROR_STATE()) {
        abortAfterCoreClosed();
      }
      updateState(state);
      return new Awakeable(
          FfmEncoding.takeSliceString(AwakeableResult.id(out)), AwakeableResult.handle(out));
    }
  }

  @Override
  public void completeAwakeable(String id, Slice payload) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_sys_complete_awakeable_success(
              vmHandle,
              FfmEncoding.foreignUtf8(arena, id),
              FfmEncoding.transferToSlice(arena, payload)));
    }
  }

  @Override
  public void completeAwakeable(String id, TerminalException reason) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_sys_complete_awakeable_failure(
              vmHandle,
              FfmEncoding.foreignUtf8(arena, id),
              FfmEncoding.foreignFailure(arena, reason)));
    }
  }

  @Override
  public int createSignalHandle(String signalName) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      return parseHandleResult(
          SharedCoreNative.vm_sys_create_signal_handle(
              vmHandle, FfmEncoding.foreignUtf8(arena, signalName)));
    }
  }

  @Override
  public void completeSignal(String targetInvocationId, String signalName, Slice value) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_sys_complete_signal_success(
              vmHandle,
              FfmEncoding.foreignUtf8(arena, targetInvocationId),
              FfmEncoding.foreignUtf8(arena, signalName),
              FfmEncoding.transferToSlice(arena, value)));
    }
  }

  @Override
  public void completeSignal(
      String targetInvocationId, String signalName, TerminalException reason) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_sys_complete_signal_failure(
              vmHandle,
              FfmEncoding.foreignUtf8(arena, targetInvocationId),
              FfmEncoding.foreignUtf8(arena, signalName),
              FfmEncoding.foreignFailure(arena, reason)));
    }
  }

  @Override
  public int promiseGet(String key) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      return parseHandleResult(
          SharedCoreNative.vm_sys_promise_get(vmHandle, FfmEncoding.foreignUtf8(arena, key)));
    }
  }

  @Override
  public int promisePeek(String key) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      return parseHandleResult(
          SharedCoreNative.vm_sys_promise_peek(vmHandle, FfmEncoding.foreignUtf8(arena, key)));
    }
  }

  @Override
  public int promiseComplete(String key, Slice value) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      return parseHandleResult(
          SharedCoreNative.vm_sys_promise_complete_success(
              vmHandle,
              FfmEncoding.foreignUtf8(arena, key),
              FfmEncoding.transferToSlice(arena, value)));
    }
  }

  @Override
  public int promiseComplete(String key, TerminalException reason) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      return parseHandleResult(
          SharedCoreNative.vm_sys_promise_complete_failure(
              vmHandle,
              FfmEncoding.foreignUtf8(arena, key),
              FfmEncoding.foreignFailure(arena, reason)));
    }
  }

  // -------------------------------------------------------------------------
  // Run
  // -------------------------------------------------------------------------

  @Override
  public RunResultHandle run(String name) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = RunResult.allocate(arena);
      SharedCoreNative.vm_sys_run(vmHandle, FfmEncoding.foreignUtf8(arena, name), out);
      int state = RunResult.state(out);
      if (state == SharedCoreNative.ERROR_STATE()) {
        abortAfterCoreClosed();
      }
      updateState(state);
      return new RunResultHandle(RunResult.replayed(out) != 0, RunResult.handle(out));
    }
  }

  @Override
  public void proposeRunCompletion(int handle, Slice value) {
    if (freed) {
      return;
    }
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_propose_run_completion_success(
              vmHandle, handle, FfmEncoding.transferToSlice(arena, value)));
    }
  }

  @Override
  public void proposeRunCompletion(int handle, TerminalException terminalException) {
    if (freed) {
      return;
    }
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_propose_run_completion_terminal_failure(
              vmHandle, handle, FfmEncoding.foreignFailure(arena, terminalException)));
    }
  }

  @Override
  public void proposeRunCompletion(
      int handle,
      Throwable throwable,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy) {
    if (freed) {
      return;
    }
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_propose_run_completion_retryable_failure(
              vmHandle,
              handle,
              attemptDuration.toMillis(),
              FfmEncoding.transferUtf8ToSlice(arena, formatThrowableMessage(throwable)),
              FfmEncoding.transferUtf8ToSlice(arena, formatThrowableStackTrace(throwable)),
              FfmEncoding.foreignRetryPolicy(arena, retryPolicy)));
    }
  }

  // -------------------------------------------------------------------------
  // Invocation introspection
  // -------------------------------------------------------------------------

  @Override
  public void cancelInvocation(String invocationId) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_sys_cancel_invocation(
              vmHandle, FfmEncoding.foreignUtf8(arena, invocationId)));
    }
  }

  @Override
  public int attachInvocation(String invocationId) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      return parseHandleResult(
          SharedCoreNative.vm_sys_attach_invocation(
              vmHandle, FfmEncoding.foreignUtf8(arena, invocationId)));
    }
  }

  @Override
  public int getInvocationOutput(String invocationId) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      return parseHandleResult(
          SharedCoreNative.vm_sys_get_invocation_output(
              vmHandle, FfmEncoding.foreignUtf8(arena, invocationId)));
    }
  }

  // -------------------------------------------------------------------------
  // Output & termination
  // -------------------------------------------------------------------------

  @Override
  public void writeOutput(Slice value) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_sys_write_output_success(
              vmHandle, FfmEncoding.transferToSlice(arena, value)));
    }
  }

  @Override
  public void writeOutput(TerminalException exception) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      consumeEmptyResult(
          SharedCoreNative.vm_sys_write_output_failure(
              vmHandle, FfmEncoding.foreignFailure(arena, exception)));
    }
  }

  @Override
  public void end() {
    verifyNotFreed();
    consumeEmptyResult(SharedCoreNative.vm_sys_end(vmHandle));
  }

  // -------------------------------------------------------------------------
  // Result helpers
  // -------------------------------------------------------------------------

  /**
   * Decodes a register-packed handle result ({@code u64}): low 32 bits = state (or the {@link
   * SharedCoreNative#ERROR_STATE()} sentinel), high 32 bits = handle. On the error sentinel we
   * {@link #abortAfterCoreClosed()}; otherwise update the cached state and return the handle.
   */
  private int parseHandleResult(long packed) {
    int state = (int) packed;
    if (state == SharedCoreNative.ERROR_STATE()) {
      abortAfterCoreClosed();
    }
    updateState(state);
    return (int) (packed >>> 32);
  }

  /**
   * Applies a register-packed empty result (a bare {@code u32}): the new state, or the {@link
   * SharedCoreNative#ERROR_STATE()} sentinel. On the error sentinel we {@link
   * #abortAfterCoreClosed()}; otherwise update the cached state.
   */
  private void consumeEmptyResult(int packed) {
    if (packed == SharedCoreNative.ERROR_STATE()) {
      abortAfterCoreClosed();
    }
    updateState(packed);
  }

  private void updateState(int ordinal) {
    // `case` labels must be compile-time constants, and the SharedCoreNative.*_STATE() bindings are
    // method calls, so this can't be a switch — it's an if/else chain like the other decoders.
    if (ordinal == SharedCoreNative.WAITING_START_STATE()) {
      this.cachedState = InvocationState.WAITING_START;
    } else if (ordinal == SharedCoreNative.REPLAYING_STATE()) {
      this.cachedState = InvocationState.REPLAYING;
    } else if (ordinal == SharedCoreNative.PROCESSING_STATE()) {
      this.cachedState = InvocationState.PROCESSING;
    } else {
      this.cachedState = InvocationState.CLOSED;
    }
  }

  /** Close the VM and map the error */
  private ProtocolException closeVmAndMapError(MemorySegment errorStruct) {
    cachedState = InvocationState.CLOSED;
    int code = VmError.code(errorStruct);
    String message = FfmEncoding.takeSliceString(VmError.message(errorStruct));
    return new ProtocolException(message, code);
  }

  private void verifyNotFreed() {
    if (freed) {
      AbortedExecutionException.sneakyThrow();
    }
  }

  /**
   * Mark the state machine as closed and throw AbortedExecutionException.
   *
   * <p>A subsequent notifyError will no-op.
   */
  private void abortAfterCoreClosed() {
    cachedState = InvocationState.CLOSED;
    AbortedExecutionException.sneakyThrow();
  }

  private static String formatThrowableMessage(Throwable throwable) {
    return throwable.toString();
  }

  private static String formatThrowableStackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
