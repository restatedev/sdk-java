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
import dev.restate.sdk.core.statemachine.ffm.generated.*;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
 * <p>Each call is a direct FFM downcall that writes a typed result struct into a caller-provided
 * out-parameter (piggybacking the current invocation state) and returns owned {@link Slice}s the
 * caller copies out and frees; on error it throws a {@link ProtocolException} from the returned
 * code and message.
 *
 * <p>An instance is driven by a single thread at a time (no reentrancy, per the contract); only
 * {@link #state()} — a volatile read — is safe from any thread. Each downcall allocates its
 * out-param and inputs in a confined {@link Arena} for the duration of the call.
 */
public final class FfmStateMachine implements StateMachine {

  private final MemorySegment vmHandle;
  private boolean freed = false;

  /**
   * Volatile mirror of the VM's invocation state, updated on the state-machine thread after every
   * call from the piggybacked {@code state} ordinal in the result struct.
   */
  private volatile InvocationState cachedState = InvocationState.WAITING_START;

  private static final InvocationState[] STATES = InvocationState.values();

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
      byte[] headersBlob = FfmEncoding.encodeHeaderPairs(headers);
      MemorySegment out = VmNewResult.allocate(arena);

      SharedCoreNative.vm_new(FfmEncoding.foreignBytes(arena, headersBlob), out);
      if (VmNewResult.tag(out) == SharedCoreNative.VmNewResult_Err()) {
        throw closeVmAndMapError(VmNewResult_Err_Body.error(VmNewResult.err(out)));
      }
      this.vmHandle = VmNewResult_Ok_Body.handle(VmNewResult.ok(out));
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
      // take_output never fails: it writes a bare Slice (not a BufferResult). Copy the buffer out
      // into a heap byte[] and free the native buffer immediately (takeSliceBytes). This is the
      // safe
      // default while we chase a GC-tied-lifetime flake in the bidi path: the zero-copy
      // wrapOwnedSlice variant handed the buffer to the async output stream and could be collected
      // before the stream consumed it, intermittently dropping a flushed chunk.
      MemorySegment out = FfmEncoding.allocateSliceStruct(arena);
      SharedCoreNative.vm_take_output(vmHandle, out);
      return Slice.wrap(FfmEncoding.takeSliceBytes(out));
    }
  }

  @Override
  public String getResponseContentType() {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      // Infallible: the core scans its own header list and returns just the content-type value as a
      // bare owned Slice (empty when absent), which we copy out + free.
      MemorySegment out = FfmEncoding.allocateSliceStruct(arena);
      SharedCoreNative.vm_get_response_content_type(vmHandle, out);
      return FfmEncoding.takeSliceString(out);
    }
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
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment futureSeg = FfmEncoding.foreignBytes(arena, FfmEncoding.encodeFuture(future));
      // The native binding type is also named AwaitResult; fully qualify it so it doesn't collide
      // with the StateMachine.AwaitResult we return (inherited into scope as a bare simple name).
      MemorySegment out =
          dev.restate.sdk.core.statemachine.ffm.generated.AwaitResult.allocate(arena);
      SharedCoreNative.vm_do_await(vmHandle, futureSeg, out);

      // Tagged union mirroring AwaitResponse one variant per arm. do_await does not piggyback the
      // VM state: the live outcomes all leave the VM running; suspension closes it, which we record
      // locally and surface by sneaky-throwing AbortedExecutionException (per the StateMachine
      // contract) so the driver aborts the user code. Err closes the VM too and is thrown.
      int tag = dev.restate.sdk.core.statemachine.ffm.generated.AwaitResult.tag(out);
      if (tag == SharedCoreNative.AwaitResult_AnyCompleted()) {
        return AwaitResult.ANY_COMPLETED;
      } else if (tag == SharedCoreNative.AwaitResult_WaitingExternalProgress()) {
        return AwaitResult.WAIT_EXTERNAL_PROGRESS;
      } else if (tag == SharedCoreNative.AwaitResult_ExecuteRun()) {
        return new AwaitResult.ExecuteRun(
            AwaitResult_ExecuteRun_Body.run_handle(
                dev.restate.sdk.core.statemachine.ffm.generated.AwaitResult.execute_run(out)));
      } else if (tag == SharedCoreNative.AwaitResult_CancelSignalReceived()) {
        return AwaitResult.CANCEL_SIGNAL_RECEIVED;
      } else if (tag == SharedCoreNative.AwaitResult_Err()) {
        throw closeVmAndMapError(
            AwaitResult_Err_Body.error(
                dev.restate.sdk.core.statemachine.ffm.generated.AwaitResult.err(out)));
      }
      throw new IllegalStateException("Unknown do_await tag: " + tag);
    }
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
      metadata.put(readString(buf), readString(buf));
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
      keys.add(readString(buf));
    }
    return keys;
  }

  @Override
  public Input input() {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = InputResult.allocate(arena);
      SharedCoreNative.vm_sys_input(vmHandle, out);
      if (InputResult.tag(out) == SharedCoreNative.InputResult_Err()) {
        throw closeVmAndMapError(InputResult_Err_Body.error(InputResult.err(out)));
      }
      MemorySegment ok = InputResult.ok(out);
      updateState(InputResult_Ok_Body.state(ok));

      // The handler input payload is handed over zero-copy (GC-tied, freed via free_buffer when the
      // slice is collected) and propagated straight to the user deserialization layer.
      Slice input = FfmEncoding.wrapOwnedSlice(InputResult_Ok_Body.input(ok));

      // Everything else is copied out anyway, so the core packs it into one metadata blob (one
      // alloc
      // + one free instead of seven). Layout mirrors the InputResult doc: random_seed(u64),
      // invocation_id(str), key(str), headers(u32 count, count*(str,str)), then
      // scope/limit_key/idempotency_key as (u8 present, [str]).
      ByteBuffer meta =
          ByteBuffer.wrap(FfmEncoding.takeSliceBytes(InputResult_Ok_Body.metadata(ok)))
              .order(ByteOrder.LITTLE_ENDIAN);
      long randomSeed = meta.getLong();
      String invocationId = readString(meta);
      String key = readString(meta);
      int hcount = meta.getInt();
      List<String[]> headers = new ArrayList<>(hcount);
      for (int i = 0; i < hcount; i++) {
        headers.add(new String[] {readString(meta), readString(meta)});
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
    return buf.get() == 0 ? null : readString(buf);
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
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_state_get(vmHandle, FfmEncoding.foreignUtf8(arena, key), out);
      return handleResult(out);
    }
  }

  @Override
  public int stateGetKeys() {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_state_get_keys(vmHandle, out);
      return handleResult(out);
    }
  }

  @Override
  public void stateSet(String key, Slice value) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_state_set(
          vmHandle,
          FfmEncoding.foreignUtf8(arena, key),
          FfmEncoding.transferToSlice(arena, value),
          out);
      emptyResult(out);
    }
  }

  @Override
  public void stateClear(String key) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_state_clear(vmHandle, FfmEncoding.foreignUtf8(arena, key), out);
      emptyResult(out);
    }
  }

  @Override
  public void stateClearAll() {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_state_clear_all(vmHandle, out);
      emptyResult(out);
    }
  }

  // -------------------------------------------------------------------------
  // Sleep
  // -------------------------------------------------------------------------

  @Override
  public int sleep(Duration duration, @Nullable String name) {
    verifyNotFreed();
    long now = System.currentTimeMillis();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_sleep(
          vmHandle,
          FfmEncoding.foreignUtf8(arena, name != null ? name : ""),
          now + duration.toMillis(),
          now,
          out);
      return handleResult(out);
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
      if (CallResult.tag(out) == SharedCoreNative.CallResult_Err()) {
        throw closeVmAndMapError(CallResult_Err_Body.error(CallResult.err(out)));
      }
      MemorySegment ok = CallResult.ok(out);
      updateState(CallResult_Ok_Body.state(ok));
      return new CallHandle(
          CallResult_Ok_Body.invocation_id_handle(ok), CallResult_Ok_Body.result_handle(ok));
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
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_send(vmHandle, args, delayMillis, out);
      return handleResult(out);
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
      if (AwakeableResult.tag(out) == SharedCoreNative.AwakeableResult_Err()) {
        throw closeVmAndMapError(AwakeableResult_Err_Body.error(AwakeableResult.err(out)));
      }
      MemorySegment ok = AwakeableResult.ok(out);
      updateState(AwakeableResult_Ok_Body.state(ok));
      return new Awakeable(
          FfmEncoding.takeSliceString(AwakeableResult_Ok_Body.id(ok)),
          AwakeableResult_Ok_Body.handle(ok));
    }
  }

  @Override
  public void completeAwakeable(String id, Slice payload) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_complete_awakeable_success(
          vmHandle,
          FfmEncoding.foreignUtf8(arena, id),
          FfmEncoding.transferToSlice(arena, payload),
          out);
      emptyResult(out);
    }
  }

  @Override
  public void completeAwakeable(String id, TerminalException reason) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_complete_awakeable_failure(
          vmHandle,
          FfmEncoding.foreignUtf8(arena, id),
          FfmEncoding.foreignBytes(arena, FfmEncoding.encodeFailure(reason)),
          out);
      emptyResult(out);
    }
  }

  @Override
  public int createSignalHandle(String signalName) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_create_signal_handle(
          vmHandle, FfmEncoding.foreignUtf8(arena, signalName), out);
      return handleResult(out);
    }
  }

  @Override
  public void completeSignal(String targetInvocationId, String signalName, Slice value) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_complete_signal_success(
          vmHandle,
          FfmEncoding.foreignUtf8(arena, targetInvocationId),
          FfmEncoding.foreignUtf8(arena, signalName),
          FfmEncoding.transferToSlice(arena, value),
          out);
      emptyResult(out);
    }
  }

  @Override
  public void completeSignal(
      String targetInvocationId, String signalName, TerminalException reason) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_complete_signal_failure(
          vmHandle,
          FfmEncoding.foreignUtf8(arena, targetInvocationId),
          FfmEncoding.foreignUtf8(arena, signalName),
          FfmEncoding.foreignBytes(arena, FfmEncoding.encodeFailure(reason)),
          out);
      emptyResult(out);
    }
  }

  @Override
  public int promiseGet(String key) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_promise_get(vmHandle, FfmEncoding.foreignUtf8(arena, key), out);
      return handleResult(out);
    }
  }

  @Override
  public int promisePeek(String key) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_promise_peek(vmHandle, FfmEncoding.foreignUtf8(arena, key), out);
      return handleResult(out);
    }
  }

  @Override
  public int promiseComplete(String key, Slice value) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_promise_complete_success(
          vmHandle,
          FfmEncoding.foreignUtf8(arena, key),
          FfmEncoding.transferToSlice(arena, value),
          out);
      return handleResult(out);
    }
  }

  @Override
  public int promiseComplete(String key, TerminalException reason) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_promise_complete_failure(
          vmHandle,
          FfmEncoding.foreignUtf8(arena, key),
          FfmEncoding.foreignBytes(arena, FfmEncoding.encodeFailure(reason)),
          out);
      return handleResult(out);
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
      if (RunResult.tag(out) == SharedCoreNative.RunResult_Err()) {
        throw closeVmAndMapError(RunResult_Err_Body.error(RunResult.err(out)));
      }
      MemorySegment ok = RunResult.ok(out);
      updateState(RunResult_Ok_Body.state(ok));
      return new RunResultHandle(RunResult_Ok_Body.replayed(ok) != 0, RunResult_Ok_Body.handle(ok));
    }
  }

  @Override
  public void proposeRunCompletion(int handle, Slice value) {
    if (freed) {
      return;
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_propose_run_completion_success(
          vmHandle, handle, FfmEncoding.transferToSlice(arena, value), out);
      emptyResult(out);
    }
  }

  @Override
  public void proposeRunCompletion(int handle, TerminalException terminalException) {
    if (freed) {
      return;
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_propose_run_completion_terminal_failure(
          vmHandle,
          handle,
          FfmEncoding.foreignBytes(arena, FfmEncoding.encodeFailure(terminalException)),
          out);
      emptyResult(out);
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
      MemorySegment paramsSeg =
          FfmEncoding.foreignBytes(
              arena,
              FfmEncoding.encodeRetryableRunParams(
                  formatThrowableMessage(throwable),
                  formatThrowableStackTrace(throwable),
                  retryPolicy));
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_propose_run_completion_retryable_failure(
          vmHandle, handle, attemptDuration.toMillis(), paramsSeg, out);
      emptyResult(out);
    }
  }

  // -------------------------------------------------------------------------
  // Invocation introspection
  // -------------------------------------------------------------------------

  @Override
  public void cancelInvocation(String invocationId) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_cancel_invocation(
          vmHandle, FfmEncoding.foreignUtf8(arena, invocationId), out);
      emptyResult(out);
    }
  }

  @Override
  public int attachInvocation(String invocationId) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_attach_invocation(
          vmHandle, FfmEncoding.foreignUtf8(arena, invocationId), out);
      return handleResult(out);
    }
  }

  @Override
  public int getInvocationOutput(String invocationId) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = HandleResult.allocate(arena);
      SharedCoreNative.vm_sys_get_invocation_output(
          vmHandle, FfmEncoding.foreignUtf8(arena, invocationId), out);
      return handleResult(out);
    }
  }

  // -------------------------------------------------------------------------
  // Output & termination
  // -------------------------------------------------------------------------

  @Override
  public void writeOutput(Slice value) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_write_output_success(
          vmHandle, FfmEncoding.transferToSlice(arena, value), out);
      emptyResult(out);
    }
  }

  @Override
  public void writeOutput(TerminalException exception) {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_write_output_failure(
          vmHandle, FfmEncoding.foreignBytes(arena, FfmEncoding.encodeFailure(exception)), out);
      emptyResult(out);
    }
  }

  @Override
  public void end() {
    verifyNotFreed();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = EmptyResult.allocate(arena);
      SharedCoreNative.vm_sys_end(vmHandle, out);
      emptyResult(out);
    }
  }

  // -------------------------------------------------------------------------
  // Result helpers
  // -------------------------------------------------------------------------

  /** Reads a {@link HandleResult} tagged union: on {@code Ok} update state + return the handle. */
  private int handleResult(MemorySegment out) {
    if (HandleResult.tag(out) == SharedCoreNative.HandleResult_Err()) {
      throw closeVmAndMapError(HandleResult_Err_Body.error(HandleResult.err(out)));
    }
    MemorySegment ok = HandleResult.ok(out);
    updateState(HandleResult_Ok_Body.state(ok));
    return HandleResult_Ok_Body.handle(ok);
  }

  /** Reads an {@link EmptyResult} tagged union: on {@code Ok} update state. */
  private void emptyResult(MemorySegment out) {
    if (EmptyResult.tag(out) == SharedCoreNative.EmptyResult_Err()) {
      throw closeVmAndMapError(EmptyResult_Err_Body.error(EmptyResult.err(out)));
    }
    updateState(EmptyResult_Ok_Body.state(EmptyResult.ok(out)));
  }

  private void updateState(int ordinal) {
    if (ordinal >= 0 && ordinal < STATES.length) {
      this.cachedState = STATES[ordinal];
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

  private static String readString(ByteBuffer buf) {
    int len = buf.getInt();
    if (len == 0) {
      return "";
    }
    byte[] data = new byte[len];
    buf.get(data);
    return new String(data, StandardCharsets.UTF_8);
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
