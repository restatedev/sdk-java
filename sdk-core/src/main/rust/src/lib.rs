//! Native (C ABI) wrapper around `restate-sdk-shared-core`, compiled to a `cdylib` and called from
//! Java via the Panama Foreign Function & Memory API (JDK 23+). The boundary is a plain C ABI kept
//! cbindgen/jextract-friendly:
//!
//!   - The VM is an opaque `*mut VmHandle` from `vm_new`, released by `vm_free`. Java serializes
//!     access per handle (one thread at a time, no reentrancy), so it is a plain `Box`.
//!   - Fallible calls write into a caller-provided, typed `#[repr(C, u32)]` tagged-union out-param:
//!     `Ok { state, .. }` on success, `Err { error }` on failure (a failed op closes the VM, so the
//!     error arm carries no state). Tags are prefixed with the enum name (cbindgen
//!     `prefix_with_name`) so jextract emits collision-free constants.
//!   - Scalars cross as direct args; byte/string payloads cross by value as a `ForeignSlice`
//!     (borrowed Java memory, read in-call, never freed here) or a `Slice` (Rust-owned). See the
//!     `mem` module for the ownership rules.
//!   - Each exported function is a thin `unsafe` shim (`_vm_*`, `#[export_name = "vm_*"]`) that
//!     isolates the raw-pointer reads and delegates to a safe inner `fn`.

#![allow(clippy::missing_safety_doc)]
#![allow(clippy::not_unsafe_ptr_arg_deref)]
#![allow(clippy::too_many_arguments)]

mod logging;
mod mem;

pub use logging::AbiLogLevel;
pub use mem::{ForeignSlice, Slice};

use crate::logging::init_logging;
use bytes::{Buf, BufMut, Bytes};
use restate_sdk_shared_core::{
    AttachInvocationTarget, AwaitResponse, AwakeableHandle, CoreVM, Error, Header, HeaderMap,
    NonEmptyValue, NotificationHandle, PayloadOptions, RetryPolicy, RunExitResult, RunHandle,
    State, Target, TerminalFailure, UnresolvedFuture, VMOptions, VMResult, Value, VM,
};
use std::borrow::Cow;
use std::convert::Infallible;
use std::ffi::c_void;
use std::mem::MaybeUninit;
use std::time::Duration;
// =========================================================================
// Init & logging
// =========================================================================

/// Install the process-global tracing subscriber and panic hook. `level` is the max [`AbiLogLevel`]
/// to emit — set from the host's logging configuration so disabled callsites (e.g. the core's
/// `#[instrument(level = "trace")]` spans) short-circuit before doing any work. Events that pass the
/// filter are formatted into a thread-local buffer and forwarded to the host via [`LogCallback`]
/// (zero-copy).
///
/// This must be called exactly once. `log_callback` must be non-null (the host always installs a
/// sink), and a second call — or any pre-existing global subscriber — **panics**: silently ignoring
/// it would hide a real wiring bug and leave logs going nowhere.
#[no_mangle]
pub extern "C" fn init(level: AbiLogLevel, log_callback: *const c_void) {
    std::panic::set_hook(Box::new(|panic| {
        eprintln!("[restate-shared-core] core panicked: {panic}");
    }));
    assert!(
        !log_callback.is_null(),
        "init called with a null log callback"
    );
    init_logging(level, log_callback);
}

// =========================================================================
// VM handle
// =========================================================================

pub struct VmHandle {
    vm: CoreVM,
    notification_scratch: Option<Option<Value>>,
    last_vm_error_scratch: Option<Error>,
}

impl VmHandle {
    pub(crate) fn pack_handle_result(&mut self, res: VMResult<NotificationHandle>) -> u64 {
        match res {
            Ok(h) => (self.vm.state() as u8 as u64) | ((u32::from(h) as u64) << 32),
            Err(e) => {
                self.last_vm_error_scratch = Some(e);
                ERROR_STATE as u64
            }
        }
    }

    pub(crate) fn pack_empty_result(&mut self, res: VMResult<()>) -> u32 {
        match res {
            Ok(()) => self.vm.state() as u8 as u32,
            Err(e) => {
                self.last_vm_error_scratch = Some(e);
                ERROR_STATE
            }
        }
    }

    pub(crate) fn pack_struct_result<T: Default>(&mut self, res: VMResult<T>) -> T {
        match res {
            Ok(v) => v,
            Err(e) => {
                self.last_vm_error_scratch = Some(e);
                T::default()
            }
        }
    }
}

struct Headers(Vec<(String, String)>);

impl HeaderMap for Headers {
    type Error = Infallible;

    fn extract(&self, name: &str) -> Result<Option<&str>, Self::Error> {
        for (key, value) in &self.0 {
            if key.eq_ignore_ascii_case(name) {
                return Ok(Some(value));
            }
        }
        Ok(None)
    }
}

#[inline]
unsafe fn vm_mut<'a>(handle: *mut VmHandle) -> &'a mut VmHandle {
    assert_not_null(handle);
    &mut *handle
}

#[inline]
fn state_of(vm: &CoreVM) -> u32 {
    VM::state(vm) as u8 as u32
}

// =========================================================================
// Shared ABI types
// =========================================================================

/// Error payload. Valid only when the enclosing result's `ok == 0`. `message` is
/// an owned UTF-8 `Slice` the caller frees via `free_buffer`.
#[repr(C)]
pub struct VmError {
    pub code: u32,
    pub message: Slice,
}

impl VmError {
    #[inline]
    fn of(e: &Error) -> Self {
        VmError {
            code: e.code() as u32,
            message: Slice::from_vec(e.to_string().into_bytes()),
        }
    }
}

/// Result of `vm_new`: the opaque VM handle on success, or the construction
/// error. Unlike the other results there is no `state` piggyback — there is no
/// prior call and the caller starts at `WAITING_START`.
#[repr(C, u32)]
pub enum VmNewResult {
    Ok {
        handle: *mut VmHandle,
        response_content_type: Slice,
    },
    Err {
        error: VmError,
    },
}

// The Java decoder maps a call's returned `state` ordinal to an `InvocationState` via these
// constants. cbindgen can't const-eval `State::X as u8`, so it would silently drop such consts;
// we hardcode the discriminants as plain literals (which it *does* export as `#define`s) and
// statically assert below that they still match the core's `State` enum.
pub const WAITING_START_STATE: u32 = 0;
pub const REPLAYING_STATE: u32 = 1;
pub const PROCESSING_STATE: u32 = 2;
pub const CLOSED_STATE: u32 = 3;

const _: () = {
    assert!(WAITING_START_STATE == State::WaitingPreFlight as u32);
    assert!(REPLAYING_STATE == State::Replaying as u32);
    assert!(PROCESSING_STATE == State::Processing as u32);
    assert!(CLOSED_STATE == State::Closed as u32);
};
/// Sentinel `state` value meaning "error": the real detail is stashed in `last_vm_error_scratch` and
/// fetched via `vm_take_last_vm_error`. `u32::MAX` is never a real `InvocationState` ordinal. Shared
/// by every state-piggybacking result — the packed `HandleResult`/`EmptyResult` scalars and the
/// `state` field of the `InputResult`/`CallResult`/`RunResult`/`AwakeableResult` structs. cbindgen
/// exports it for the Java decoder.
pub const ERROR_STATE: u32 = u32::MAX;

#[inline]
unsafe fn write_out<T>(out: *mut T, value: T) {
    debug_assert!(!out.is_null());
    std::ptr::write(out, value);
}

// =========================================================================
// Lifecycle
// =========================================================================

#[export_name = "vm_new"]
pub unsafe extern "C" fn _vm_new(headers: ForeignSlice, out: *mut VmNewResult) {
    let r = match vm_new(headers.as_slice()) {
        Ok((response_content_type, vm)) => VmNewResult::Ok {
            handle: Box::into_raw(vm),
            response_content_type: response_content_type
                .map(Slice::from_string)
                .unwrap_or(Slice::EMPTY),
        },
        Err(e) => VmNewResult::Err {
            error: VmError::of(&e),
        },
    };
    write_out(out, r);
}

#[inline]
fn vm_new(headers_buf: &[u8]) -> Result<(Option<String>, Box<VmHandle>), Error> {
    let headers = decode_header_list(headers_buf);
    CoreVM::new(Headers(headers), VMOptions::default()).map(|vm| {
        let response_content_type = VM::get_response_head(&vm)
            .headers
            .into_iter()
            .find(|h| h.key.eq_ignore_ascii_case("content-type"))
            .map(|h| h.value.into_owned());
        (
            response_content_type,
            Box::new(VmHandle {
                vm,
                notification_scratch: None,
                last_vm_error_scratch: None,
            }),
        )
    })
}

#[export_name = "vm_free"]
pub unsafe extern "C" fn _vm_free(handle: *mut VmHandle) {
    assert_not_null(handle);
    drop(Box::from_raw(handle));
}

/// Take the error stashed by the last failing call that packs its result (e.g. a `do_await`
/// returning `Error`) and write it as a `VmError` into `out`. Panics if none is pending — a
/// caller-protocol violation, since this must only be called on the error path.
#[export_name = "vm_take_last_vm_error"]
pub unsafe extern "C" fn _vm_take_last_vm_error(handle: *mut VmHandle, out: *mut VmError) {
    let err = vm_mut(handle)
        .last_vm_error_scratch
        .take()
        .expect("vm_take_last_vm_error called without a pending error");
    write_out(out, VmError::of(&err));
}

// =========================================================================
// Input / output
// =========================================================================

/// `input` must be an `alloc_buffer` buffer fully written by the caller; ownership transfers
/// here (the core retains it zero-copy via `Slice::take`, frees it when dropped).
#[export_name = "vm_notify_input"]
pub unsafe extern "C" fn _vm_notify_input(handle: *mut VmHandle, input: Slice) {
    let h = vm_mut(handle);
    vm_notify_input(&mut h.vm, input.take());
}

#[inline]
fn vm_notify_input(vm: &mut CoreVM, input: Bytes) {
    VM::notify_input(vm, input);
}

#[export_name = "vm_notify_input_closed"]
pub unsafe extern "C" fn _vm_notify_input_closed(handle: *mut VmHandle) {
    let h = vm_mut(handle);
    vm_notify_input_closed(&mut h.vm);
}

#[inline]
fn vm_notify_input_closed(vm: &mut CoreVM) {
    VM::notify_input_closed(vm);
}

/// `message`/`stacktrace` are `alloc_buffer` buffers fully written by the caller; ownership
/// transfers here (re-owned as `String`s via `Slice::take_string`, zero-copy, UTF-8 unchecked since
/// Java already encoded them). `stacktrace`'s `ptr` may be null (no stacktrace).
#[export_name = "vm_notify_error"]
pub unsafe extern "C" fn _vm_notify_error(
    handle: *mut VmHandle,
    message: Slice,
    stacktrace: Slice,
) {
    let h = vm_mut(handle);
    let message = message.take_string();
    let stacktrace = (!stacktrace.is_null()).then(|| stacktrace.take_string());
    vm_notify_error(&mut h.vm, message, stacktrace);
}

#[inline]
fn vm_notify_error(vm: &mut CoreVM, message: String, stacktrace: Option<String>) {
    let mut error = Error::new(500u16, Cow::Owned(message));
    if let Some(stacktrace) = stacktrace {
        error = error.with_stacktrace(stacktrace);
    }
    VM::notify_error(vm, error, None);
}

#[export_name = "vm_take_output"]
pub unsafe extern "C" fn _vm_take_output(handle: *mut VmHandle, out: *mut Slice) {
    let h = vm_mut(handle);
    write_out(out, vm_take_output(&mut h.vm));
}

#[inline]
fn vm_take_output(vm: &mut CoreVM) -> Slice {
    Slice::from_bytes(VM::take_output(vm))
}

/// Result of `is_ready_to_execute`: a boolean in `value` (0/1).
#[repr(C, u32)]
pub enum IsReadyToExecuteResult {
    Ok { value: u32 },
    Err { error: VmError },
}

#[export_name = "vm_is_ready_to_execute"]
pub unsafe extern "C" fn _vm_is_ready_to_execute(
    handle: *mut VmHandle,
    out: *mut IsReadyToExecuteResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_is_ready_to_execute(&mut h.vm));
}

#[inline]
fn vm_is_ready_to_execute(vm: &mut CoreVM) -> IsReadyToExecuteResult {
    match VM::is_ready_to_execute(vm) {
        Ok(ready) => IsReadyToExecuteResult::Ok {
            value: ready as u32,
        },
        Err(e) => IsReadyToExecuteResult::Err {
            error: VmError::of(&e),
        },
    }
}

/// `do_await` outcome tags.
pub const AWAIT_VARIANT_ANY_COMPLETED: u32 = 0;
pub const AWAIT_VARIANT_WAITING_EXTERNAL_PROGRESS: u32 = 1;
pub const AWAIT_VARIANT_EXECUTE_RUN: u32 = 2;
pub const AWAIT_VARIANT_CANCEL_SIGNAL_RECEIVED: u32 = 3;
pub const AWAIT_VARIANT_ERROR: u32 = 4;

/// Make progress on the encoded await future tree (see `decode_future`), returning the outcome
/// register-packed into a bare `u64`: low 32 bits = variant (`AWAIT_VARIANT_*`), high 32 bits = the
/// `ExecuteRun` run handle (0 otherwise). No out-param/arena on this hot path. On error the detail is
/// stashed in `last_vm_error_scratch` and fetched via `vm_take_last_vm_error` (only on the `Error`
/// variant). `AwaitResponse::Suspended` isn't represented: the SDK records suspension locally and
/// sneaky-throws.
#[export_name = "vm_do_await"]
pub unsafe extern "C" fn _vm_do_await(handle: *mut VmHandle, future: ForeignSlice) -> u64 {
    let h = vm_mut(handle);
    vm_do_await(h, future.as_slice())
}

#[inline]
fn vm_do_await(h: &mut VmHandle, future_buf: &[u8]) -> u64 {
    let future = decode_future(&mut { future_buf });
    let (variant, run_handle): (u32, u32) = match VM::do_await(&mut h.vm, future) {
        Ok(AwaitResponse::AnyCompleted) => (AWAIT_VARIANT_ANY_COMPLETED, 0),
        Ok(AwaitResponse::WaitingExternalProgress { .. }) => {
            (AWAIT_VARIANT_WAITING_EXTERNAL_PROGRESS, 0)
        }
        Ok(AwaitResponse::ExecuteRun(run)) => (AWAIT_VARIANT_EXECUTE_RUN, run.into()),
        Ok(AwaitResponse::CancelSignalReceived) => (AWAIT_VARIANT_CANCEL_SIGNAL_RECEIVED, 0),
        Err(e) => {
            h.last_vm_error_scratch = Some(e);
            (AWAIT_VARIANT_ERROR, 0)
        }
    };
    (variant as u64) | ((run_handle as u64) << 32)
}

#[repr(C)]
pub struct AbiTerminalFailure {
    code: u32,
    message: Slice,
    metadata: Slice,
}

#[repr(u8)]
pub enum NotificationVariant {
    NotReady,
    Empty,
    Success,
    TerminalFailure,
    StateKeys,
    InvocationId,
}

#[export_name = "vm_take_notification"]
pub unsafe extern "C" fn _vm_take_notification(
    handle: *mut VmHandle,
    notification_handle: u32,
) -> NotificationVariant {
    let h = vm_mut(handle);

    // Parse result
    let result = vm_take_notification_and_store_in_scratch(&mut h.vm, notification_handle);
    let notification_variant = match &result {
        None => NotificationVariant::NotReady,
        Some(Value::Void) => NotificationVariant::Empty,
        Some(Value::Success(_)) => NotificationVariant::Success,
        Some(Value::Failure(_)) => NotificationVariant::TerminalFailure,
        Some(Value::StateKeys(_)) => NotificationVariant::StateKeys,
        Some(Value::InvocationId(_)) => NotificationVariant::InvocationId,
    };

    // Save notification in scratch
    h.notification_scratch = Some(result);

    notification_variant
}

#[inline]
fn vm_take_notification_and_store_in_scratch(
    vm: &mut CoreVM,
    notification_handle: u32,
) -> Option<Value> {
    // Ignoring failures in take_notification is fine: eventually code will get to do_progress, where we propagate errors up!
    VM::take_notification(vm, NotificationHandle::from(notification_handle)).unwrap_or(None)
}

#[export_name = "vm_take_notification_success"]
pub unsafe extern "C" fn _vm_take_notification_success(handle: *mut VmHandle, out: *mut Slice) {
    match vm_mut(handle).notification_scratch.take() {
        Some(Some(Value::Success(bytes))) => write_out(out, Slice::from_bytes(bytes)),
        _ => panic!("vm_take_notification_success called without a pending Success notification"),
    }
}

#[export_name = "vm_take_notification_terminal_failure"]
pub unsafe extern "C" fn _vm_take_notification_terminal_failure(
    handle: *mut VmHandle,
    out: *mut AbiTerminalFailure,
) {
    match vm_mut(handle).notification_scratch.take() {
        Some(Some(Value::Failure(TerminalFailure {
            code,
            message,
            metadata,
        }))) => {
            let mut buf = Vec::new();
            buf.put_u32_le(metadata.len() as u32);
            for (k, v) in metadata {
                put_str(&mut buf, &k);
                put_str(&mut buf, &v);
            }
            write_out(
                out,
                AbiTerminalFailure {
                    code: code as u32,
                    message: Slice::from_string(message),
                    metadata: Slice::from_vec(buf),
                },
            );
        }
        _ => panic!(
            "vm_take_notification_terminal_failure called without a pending TerminalFailure notification"
        ),
    }
}

#[export_name = "vm_take_notification_state_keys"]
pub unsafe extern "C" fn _vm_take_notification_state_keys(handle: *mut VmHandle, out: *mut Slice) {
    match vm_mut(handle).notification_scratch.take() {
        Some(Some(Value::StateKeys(keys))) => {
            let mut buf = Vec::new();
            buf.put_u32_le(keys.len() as u32);
            for k in keys {
                put_str(&mut buf, &k);
            }
            write_out(out, Slice::from_vec(buf));
        }
        _ => {
            panic!(
                "vm_take_notification_state_keys called without a pending StateKeys notification"
            )
        }
    }
}

#[export_name = "vm_take_notification_invocation_id"]
pub unsafe extern "C" fn _vm_take_notification_invocation_id(
    handle: *mut VmHandle,
    out: *mut Slice,
) {
    match vm_mut(handle).notification_scratch.take() {
        Some(Some(Value::InvocationId(id))) => write_out(out, Slice::from_string(id)),
        _ => panic!(
            "vm_take_notification_invocation_id called without a pending InvocationId notification"
        ),
    }
}

/// Result of `sys_input`. Everything the Java layer copies out anyway is packed into one
/// `metadata` blob (one allocation + one `free_buffer` instead of seven): `random_seed` (u64),
/// `invocation_id` (str), `key` (str), `headers` (`u32 count, count*(str,str)`), then the V7
/// optionals `scope`/`limit_key`/`idempotency_key` each as `(u8 present, [str])`. Strings are
/// encoded `u32 len, bytes`; integers little-endian. The handler `input` payload stays a separate
/// owned `Slice` — Java reinterprets it zero-copy and propagates it to the user deserialization
/// layer rather than copying it out. `state` rides inline (the usual piggyback).
#[repr(C)]
pub struct InputResult {
    pub state: u32,
    pub metadata: Slice,
    pub input: Slice,
}

impl Default for InputResult {
    fn default() -> Self {
        Self {
            state: ERROR_STATE,
            metadata: Slice::EMPTY,
            input: Slice::EMPTY,
        }
    }
}

/// Writes the typed `Input` into an `InputResult`. Only `headers` is encoded as a
/// blob: `u32 count, count*(str key, str value)`.
#[export_name = "vm_sys_input"]
pub unsafe extern "C" fn _vm_sys_input(handle: *mut VmHandle, out: *mut InputResult) {
    let h = vm_mut(handle);
    let res = vm_sys_input(&mut h.vm);
    write_out(out, h.pack_struct_result(res));
}

#[inline]
fn vm_sys_input(vm: &mut CoreVM) -> VMResult<InputResult> {
    let input = VM::sys_input(vm)?;
    // Pack everything the Java side copies out anyway into one blob (see `InputResult` doc).
    let mut meta = Vec::new();
    meta.put_u64_le(input.random_seed);
    put_str(&mut meta, &input.invocation_id);
    put_str(&mut meta, &input.key);
    meta.put_u32_le(input.headers.len() as u32);
    for header in &input.headers {
        put_str(&mut meta, &header.key);
        put_str(&mut meta, &header.value);
    }
    put_opt_str(&mut meta, input.scope.as_deref());
    put_opt_str(&mut meta, input.limit_key.as_deref());
    put_opt_str(&mut meta, input.idempotency_key.as_deref());
    Ok(InputResult {
        state: state_of(vm),
        metadata: Slice::from_vec(meta),
        input: Slice::from_bytes(input.input),
    })
}

// =========================================================================
// State
// =========================================================================

#[export_name = "vm_sys_state_get"]
pub unsafe extern "C" fn _vm_sys_state_get(handle: *mut VmHandle, key: ForeignSlice) -> u64 {
    let h = vm_mut(handle);
    let res = vm_sys_state_get(&mut h.vm, key.as_str());
    h.pack_handle_result(res)
}

#[inline]
fn vm_sys_state_get(vm: &mut CoreVM, key: &str) -> VMResult<NotificationHandle> {
    VM::sys_state_get(vm, key.to_owned(), PayloadOptions::default())
}

#[export_name = "vm_sys_state_get_keys"]
pub unsafe extern "C" fn _vm_sys_state_get_keys(handle: *mut VmHandle) -> u64 {
    let h = vm_mut(handle);
    let res = vm_sys_state_get_keys(&mut h.vm);
    h.pack_handle_result(res)
}

#[inline]
fn vm_sys_state_get_keys(vm: &mut CoreVM) -> VMResult<NotificationHandle> {
    VM::sys_state_get_keys(vm)
}

#[export_name = "vm_sys_state_set"]
pub unsafe extern "C" fn _vm_sys_state_set(
    handle: *mut VmHandle,
    key: ForeignSlice,
    value: Slice,
) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_state_set(&mut h.vm, key.as_str(), value.take());
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_state_set(vm: &mut CoreVM, key: &str, value: Bytes) -> VMResult<()> {
    VM::sys_state_set(vm, key.to_owned(), value, PayloadOptions::default())
}

#[export_name = "vm_sys_state_clear"]
pub unsafe extern "C" fn _vm_sys_state_clear(handle: *mut VmHandle, key: ForeignSlice) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_state_clear(&mut h.vm, key.as_str());
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_state_clear(vm: &mut CoreVM, key: &str) -> VMResult<()> {
    VM::sys_state_clear(vm, key.to_owned())
}

#[export_name = "vm_sys_state_clear_all"]
pub unsafe extern "C" fn _vm_sys_state_clear_all(handle: *mut VmHandle) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_state_clear_all(&mut h.vm);
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_state_clear_all(vm: &mut CoreVM) -> VMResult<()> {
    VM::sys_state_clear_all(vm)
}

#[export_name = "vm_sys_sleep"]
pub unsafe extern "C" fn _vm_sys_sleep(
    handle: *mut VmHandle,
    name: ForeignSlice,
    wake_up_time_since_unix_epoch_millis: u64,
    now_since_unix_epoch_millis: u64,
) -> u64 {
    let h = vm_mut(handle);
    let r = vm_sys_sleep(
        &mut h.vm,
        name.as_str(),
        wake_up_time_since_unix_epoch_millis,
        now_since_unix_epoch_millis,
    );
    h.pack_handle_result(r)
}

#[inline]
fn vm_sys_sleep(
    vm: &mut CoreVM,
    name: &str,
    wake_up_time_since_unix_epoch_millis: u64,
    now_since_unix_epoch_millis: u64,
) -> VMResult<NotificationHandle> {
    VM::sys_sleep(
        vm,
        name.to_owned(),
        Duration::from_millis(wake_up_time_since_unix_epoch_millis),
        Some(Duration::from_millis(now_since_unix_epoch_millis)),
    )
}

#[repr(C)]
pub struct AwakeableResult {
    pub state: u32,
    pub handle: u32,
    pub id: Slice,
}

impl Default for AwakeableResult {
    fn default() -> Self {
        Self {
            state: ERROR_STATE,
            handle: 0,
            id: Slice::EMPTY,
        }
    }
}

#[export_name = "vm_sys_awakeable"]
pub unsafe extern "C" fn _vm_sys_awakeable(handle: *mut VmHandle, out: *mut AwakeableResult) {
    let h = vm_mut(handle);
    let res = vm_sys_awakeable(&mut h.vm);
    write_out(out, h.pack_struct_result(res));
}

#[inline]
fn vm_sys_awakeable(vm: &mut CoreVM) -> VMResult<AwakeableResult> {
    let AwakeableHandle { id, handle } = VM::sys_awakeable(vm)?;
    Ok(AwakeableResult {
        state: state_of(vm),
        handle: handle.into(),
        id: Slice::from_string(id),
    })
}

#[export_name = "vm_sys_complete_awakeable_success"]
pub unsafe extern "C" fn _vm_sys_complete_awakeable_success(
    handle: *mut VmHandle,
    id: ForeignSlice,
    value: Slice,
) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_complete_awakeable_success(&mut h.vm, id.as_str(), value.take());
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_complete_awakeable_success(vm: &mut CoreVM, id: &str, value: Bytes) -> VMResult<()> {
    complete_awakeable(vm, id, success_value(value))
}

#[export_name = "vm_sys_complete_awakeable_failure"]
pub unsafe extern "C" fn _vm_sys_complete_awakeable_failure(
    handle: *mut VmHandle,
    id: ForeignSlice,
    failure: ForeignSlice,
) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_complete_awakeable_failure(&mut h.vm, id.as_str(), failure.as_slice());
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_complete_awakeable_failure(vm: &mut CoreVM, id: &str, failure: &[u8]) -> VMResult<()> {
    complete_awakeable(vm, id, failure_value(failure))
}

#[inline]
fn complete_awakeable(vm: &mut CoreVM, id: &str, value: NonEmptyValue) -> VMResult<()> {
    VM::sys_complete_awakeable(vm, id.to_owned(), value, PayloadOptions::default())
}

// =========================================================================
// Call / send / invocation
// =========================================================================

#[repr(C)]
pub struct CallResult {
    pub state: u32,
    pub invocation_id_handle: u32,
    pub result_handle: u32,
}

impl Default for CallResult {
    fn default() -> Self {
        Self {
            state: ERROR_STATE,
            invocation_id_handle: 0,
            result_handle: 0,
        }
    }
}

#[export_name = "vm_sys_call"]
pub unsafe extern "C" fn _vm_sys_call(
    handle: *mut VmHandle,
    args: CallArguments,
    out: *mut CallResult,
) {
    let h = vm_mut(handle);
    let target = args.borrow();
    let input = args.input.take();
    let res = vm_sys_call(&mut h.vm, target, input);
    write_out(out, h.pack_struct_result(res));
}

#[inline]
fn vm_sys_call(vm: &mut CoreVM, target: BorrowedTarget, input: Bytes) -> VMResult<CallResult> {
    let call_handle = VM::sys_call(
        vm,
        target.into_core(),
        input,
        None,
        PayloadOptions::default(),
    )?;
    Ok(CallResult {
        state: state_of(vm),
        invocation_id_handle: call_handle.invocation_id_notification_handle.into(),
        result_handle: call_handle.call_notification_handle.into(),
    })
}

#[export_name = "vm_sys_send"]
pub unsafe extern "C" fn _vm_sys_send(
    handle: *mut VmHandle,
    args: CallArguments,
    delay_millis: u64,
) -> u64 {
    let h = vm_mut(handle);
    let target = args.borrow();
    let input = args.input.take();
    let r = vm_sys_send(&mut h.vm, target, input, delay_millis);
    h.pack_handle_result(r)
}

#[inline]
fn vm_sys_send(
    vm: &mut CoreVM,
    target: BorrowedTarget,
    input: Bytes,
    delay_millis: u64,
) -> VMResult<NotificationHandle> {
    // 0 means "no delay"; any non-zero value is the absolute wake-up time in epoch millis.
    let delay = (delay_millis != 0).then(|| Duration::from_millis(delay_millis));
    VM::sys_send(
        vm,
        target.into_core(),
        input,
        delay,
        None,
        PayloadOptions::default(),
    )
    .map(|s| s.invocation_id_notification_handle)
}

#[export_name = "vm_sys_cancel_invocation"]
pub unsafe extern "C" fn _vm_sys_cancel_invocation(handle: *mut VmHandle, id: ForeignSlice) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_cancel_invocation(&mut h.vm, id.as_slice());
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_cancel_invocation(vm: &mut CoreVM, invocation_id: &[u8]) -> VMResult<()> {
    VM::sys_cancel_invocation(vm, utf8(invocation_id).to_owned())
}

#[export_name = "vm_sys_attach_invocation"]
pub unsafe extern "C" fn _vm_sys_attach_invocation(handle: *mut VmHandle, id: ForeignSlice) -> u64 {
    let h = vm_mut(handle);
    let r = vm_sys_attach_invocation(&mut h.vm, id.as_slice());
    h.pack_handle_result(r)
}

#[inline]
fn vm_sys_attach_invocation(vm: &mut CoreVM, invocation_id: &[u8]) -> VMResult<NotificationHandle> {
    VM::sys_attach_invocation(
        vm,
        AttachInvocationTarget::InvocationId(utf8(invocation_id).to_owned()),
    )
}

#[export_name = "vm_sys_get_invocation_output"]
pub unsafe extern "C" fn _vm_sys_get_invocation_output(
    handle: *mut VmHandle,
    id: ForeignSlice,
) -> u64 {
    let h = vm_mut(handle);
    let r = vm_sys_get_invocation_output(&mut h.vm, id.as_slice());
    h.pack_handle_result(r)
}

#[inline]
fn vm_sys_get_invocation_output(
    vm: &mut CoreVM,
    invocation_id: &[u8],
) -> VMResult<NotificationHandle> {
    VM::sys_get_invocation_output(
        vm,
        AttachInvocationTarget::InvocationId(utf8(invocation_id).to_owned()),
    )
}

// =========================================================================
// Promises & signals
// =========================================================================

#[export_name = "vm_sys_promise_get"]
pub unsafe extern "C" fn _vm_sys_promise_get(handle: *mut VmHandle, key: ForeignSlice) -> u64 {
    let h = vm_mut(handle);
    let r = vm_sys_promise_get(&mut h.vm, key.as_slice());
    h.pack_handle_result(r)
}

#[inline]
fn vm_sys_promise_get(vm: &mut CoreVM, key: &[u8]) -> VMResult<NotificationHandle> {
    VM::sys_get_promise(vm, utf8(key).to_owned())
}

#[export_name = "vm_sys_promise_peek"]
pub unsafe extern "C" fn _vm_sys_promise_peek(handle: *mut VmHandle, key: ForeignSlice) -> u64 {
    let h = vm_mut(handle);
    let r = vm_sys_promise_peek(&mut h.vm, key.as_slice());
    h.pack_handle_result(r)
}

#[inline]
fn vm_sys_promise_peek(vm: &mut CoreVM, key: &[u8]) -> VMResult<NotificationHandle> {
    VM::sys_peek_promise(vm, utf8(key).to_owned())
}

#[export_name = "vm_sys_promise_complete_success"]
pub unsafe extern "C" fn _vm_sys_promise_complete_success(
    handle: *mut VmHandle,
    key: ForeignSlice,
    value: Slice,
) -> u64 {
    let h = vm_mut(handle);
    let r = vm_sys_promise_complete_success(&mut h.vm, key.as_slice(), value.take());
    h.pack_handle_result(r)
}

#[inline]
fn vm_sys_promise_complete_success(
    vm: &mut CoreVM,
    key: &[u8],
    value: Bytes,
) -> VMResult<NotificationHandle> {
    promise_complete(vm, key, success_value(value))
}

#[export_name = "vm_sys_promise_complete_failure"]
pub unsafe extern "C" fn _vm_sys_promise_complete_failure(
    handle: *mut VmHandle,
    key: ForeignSlice,
    failure: ForeignSlice,
) -> u64 {
    let h = vm_mut(handle);
    let r = vm_sys_promise_complete_failure(&mut h.vm, key.as_slice(), failure.as_slice());
    h.pack_handle_result(r)
}

#[inline]
fn vm_sys_promise_complete_failure(
    vm: &mut CoreVM,
    key: &[u8],
    failure: &[u8],
) -> VMResult<NotificationHandle> {
    promise_complete(vm, key, failure_value(failure))
}

#[inline]
fn promise_complete(
    vm: &mut CoreVM,
    key: &[u8],
    value: NonEmptyValue,
) -> VMResult<NotificationHandle> {
    VM::sys_complete_promise(vm, utf8(key).to_owned(), value, PayloadOptions::default())
}

#[export_name = "vm_sys_create_signal_handle"]
pub unsafe extern "C" fn _vm_sys_create_signal_handle(
    handle: *mut VmHandle,
    name: ForeignSlice,
) -> u64 {
    let h = vm_mut(handle);
    let r = vm_sys_create_signal_handle(&mut h.vm, name.as_slice());
    h.pack_handle_result(r)
}

#[inline]
fn vm_sys_create_signal_handle(vm: &mut CoreVM, name: &[u8]) -> VMResult<NotificationHandle> {
    VM::create_signal_handle(vm, utf8(name).to_owned())
}

#[export_name = "vm_sys_complete_signal_success"]
pub unsafe extern "C" fn _vm_sys_complete_signal_success(
    handle: *mut VmHandle,
    target: ForeignSlice,
    name: ForeignSlice,
    value: Slice,
) -> u32 {
    let h = vm_mut(handle);
    let res =
        vm_sys_complete_signal_success(&mut h.vm, target.as_slice(), name.as_slice(), value.take());
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_complete_signal_success(
    vm: &mut CoreVM,
    target: &[u8],
    name: &[u8],
    value: Bytes,
) -> VMResult<()> {
    complete_signal(vm, target, name, success_value(value))
}

#[export_name = "vm_sys_complete_signal_failure"]
pub unsafe extern "C" fn _vm_sys_complete_signal_failure(
    handle: *mut VmHandle,
    target: ForeignSlice,
    name: ForeignSlice,
    failure: ForeignSlice,
) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_complete_signal_failure(
        &mut h.vm,
        target.as_slice(),
        name.as_slice(),
        failure.as_slice(),
    );
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_complete_signal_failure(
    vm: &mut CoreVM,
    target: &[u8],
    name: &[u8],
    failure: &[u8],
) -> VMResult<()> {
    complete_signal(vm, target, name, failure_value(failure))
}

#[inline]
fn complete_signal(
    vm: &mut CoreVM,
    target_invocation_id: &[u8],
    name: &[u8],
    value: NonEmptyValue,
) -> VMResult<()> {
    VM::sys_complete_signal(
        vm,
        utf8(target_invocation_id).to_owned(),
        utf8(name).to_owned(),
        value,
    )
}

// =========================================================================
// Run
// =========================================================================

#[repr(C)]
pub struct RunResult {
    pub state: u32,
    pub handle: u32,
    pub replayed: u32,
}

impl Default for RunResult {
    fn default() -> Self {
        Self {
            state: ERROR_STATE,
            handle: 0,
            replayed: 0,
        }
    }
}

#[export_name = "vm_sys_run"]
pub unsafe extern "C" fn _vm_sys_run(
    handle: *mut VmHandle,
    name: ForeignSlice,
    out: *mut RunResult,
) {
    let h = vm_mut(handle);
    let res = vm_sys_run(&mut h.vm, name.as_slice());
    write_out(out, h.pack_struct_result(res));
}

#[inline]
fn vm_sys_run(vm: &mut CoreVM, name: &[u8]) -> VMResult<RunResult> {
    let RunHandle { replayed, handle } = VM::sys_run(vm, utf8(name).to_owned())?;
    Ok(RunResult {
        state: state_of(vm),
        handle: handle.into(),
        replayed: replayed as u32,
    })
}

/// Propose a successful run completion (`value` = run result bytes).
#[export_name = "vm_propose_run_completion_success"]
pub unsafe extern "C" fn _vm_propose_run_completion_success(
    handle: *mut VmHandle,
    run_handle: u32,
    value: Slice,
) -> u32 {
    let h = vm_mut(handle);
    let res = vm_propose_run_completion_success(&mut h.vm, run_handle, value.take());
    h.pack_empty_result(res)
}

#[inline]
fn vm_propose_run_completion_success(
    vm: &mut CoreVM,
    run_handle: u32,
    value: Bytes,
) -> VMResult<()> {
    let result = RunExitResult::Success(value);
    propose_run_completion(vm, run_handle, result, RetryPolicy::default())
}

/// Propose a terminal-failure run completion (`failure` = encoded failure blob).
#[export_name = "vm_propose_run_completion_terminal_failure"]
pub unsafe extern "C" fn _vm_propose_run_completion_terminal_failure(
    handle: *mut VmHandle,
    run_handle: u32,
    failure: ForeignSlice,
) -> u32 {
    let h = vm_mut(handle);
    let res = vm_propose_run_completion_terminal_failure(&mut h.vm, run_handle, failure.as_slice());
    h.pack_empty_result(res)
}

#[inline]
fn vm_propose_run_completion_terminal_failure(
    vm: &mut CoreVM,
    run_handle: u32,
    failure: &[u8],
) -> VMResult<()> {
    let result = RunExitResult::TerminalFailure(decode_failure(&mut { failure }));
    propose_run_completion(vm, run_handle, result, RetryPolicy::default())
}

/// Propose a retryable-failure run completion. `message`/`stacktrace` are owned `alloc_buffer`
/// buffers re-owned as `String`s via `Slice::take_string` (`stacktrace` ptr may be null); the retry
/// policy is a borrowed `ForeignSlice` decoded by `decode_retry_policy` (a null/empty slice means no
/// policy → the core's default). The attempt duration is a direct arg.
#[export_name = "vm_propose_run_completion_retryable_failure"]
pub unsafe extern "C" fn _vm_propose_run_completion_retryable_failure(
    handle: *mut VmHandle,
    run_handle: u32,
    attempt_duration_millis: u64,
    message: Slice,
    stacktrace: Slice,
    retry_policy: ForeignSlice,
) -> u32 {
    let h = vm_mut(handle);
    let message = message.take_string();
    let stacktrace = (!stacktrace.is_null()).then(|| stacktrace.take_string());
    let res = vm_propose_run_completion_retryable_failure(
        &mut h.vm,
        run_handle,
        attempt_duration_millis,
        message,
        stacktrace,
        retry_policy.as_slice(),
    );
    h.pack_empty_result(res)
}

#[inline]
fn vm_propose_run_completion_retryable_failure(
    vm: &mut CoreVM,
    run_handle: u32,
    attempt_duration_millis: u64,
    message: String,
    stacktrace: Option<String>,
    retry_policy: &[u8],
) -> VMResult<()> {
    let mut error = Error::new(500u16, Cow::Owned(message));
    if let Some(stacktrace) = stacktrace {
        error = error.with_stacktrace(stacktrace);
    }
    let result = RunExitResult::RetryableFailure {
        attempt_duration: Duration::from_millis(attempt_duration_millis),
        error,
    };
    propose_run_completion(vm, run_handle, result, decode_retry_policy(retry_policy))
}

#[inline]
fn propose_run_completion(
    vm: &mut CoreVM,
    run_handle: u32,
    result: RunExitResult,
    retry_policy: RetryPolicy,
) -> VMResult<()> {
    VM::propose_run_completion(vm, run_handle.into(), result, retry_policy)
}

// =========================================================================
// Output & termination
// =========================================================================

#[export_name = "vm_sys_write_output_success"]
pub unsafe extern "C" fn _vm_sys_write_output_success(handle: *mut VmHandle, value: Slice) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_write_output_success(&mut h.vm, value.take());
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_write_output_success(vm: &mut CoreVM, value: Bytes) -> VMResult<()> {
    write_output(vm, success_value(value))
}

#[export_name = "vm_sys_write_output_failure"]
pub unsafe extern "C" fn _vm_sys_write_output_failure(
    handle: *mut VmHandle,
    failure: ForeignSlice,
) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_write_output_failure(&mut h.vm, failure.as_slice());
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_write_output_failure(vm: &mut CoreVM, failure: &[u8]) -> VMResult<()> {
    write_output(vm, failure_value(failure))
}

#[inline]
fn write_output(vm: &mut CoreVM, value: NonEmptyValue) -> VMResult<()> {
    VM::sys_write_output(vm, value, PayloadOptions::default())
}

#[export_name = "vm_sys_end"]
pub unsafe extern "C" fn _vm_sys_end(handle: *mut VmHandle) -> u32 {
    let h = vm_mut(handle);
    let res = vm_sys_end(&mut h.vm);
    h.pack_empty_result(res)
}

#[inline]
fn vm_sys_end(vm: &mut CoreVM) -> VMResult<()> {
    VM::sys_end(vm)
}

// =========================================================================
// ABI parameter structs
// =========================================================================

/// The arguments of a call/send. The target fields are `ForeignSlice`s borrowed from Java memory
/// (a null `ptr` means an absent optional; `headers` is an encoded header list); `input` is a
/// `Slice` whose ownership transfers into the core. `borrow()` resolves the target fields only —
/// the `input` payload is taken separately via `Slice::take`.
#[repr(C)]
pub struct CallArguments {
    pub service: ForeignSlice,
    pub handler: ForeignSlice,
    pub key: ForeignSlice,
    pub idempotency_key: ForeignSlice,
    pub scope: ForeignSlice,
    pub limit_key: ForeignSlice,
    pub headers: ForeignSlice,
    pub input: Slice,
}

impl CallArguments {
    /// Resolve the target `ForeignSlice` fields into borrowed slices (the only unsafe step); the
    /// actual `Target` is built by the safe `BorrowedTarget::into_core`.
    #[inline]
    unsafe fn borrow<'a>(&self) -> BorrowedTarget<'a> {
        // Strings cross as already-valid UTF-8 (Java encoded them), so borrow them unchecked as
        // `&str`; `headers` is the encoded header-list blob and stays raw bytes.
        let opt = |fs: ForeignSlice| (!fs.ptr.is_null()).then(|| fs.as_str());
        BorrowedTarget {
            service: self.service.as_str(),
            handler: self.handler.as_str(),
            key: opt(self.key),
            idempotency_key: opt(self.idempotency_key),
            scope: opt(self.scope),
            limit_key: opt(self.limit_key),
            headers: self.headers.as_slice(),
        }
    }
}

/// A `Target` whose fields still point at caller memory; turned into an owned core
/// `Target` (UTF-8 + header decode) by safe code in `into_core`. `scope`/`limit_key`
/// are V7 optionals (a null `*_ptr` means absent).
struct BorrowedTarget<'a> {
    service: &'a str,
    handler: &'a str,
    key: Option<&'a str>,
    idempotency_key: Option<&'a str>,
    scope: Option<&'a str>,
    limit_key: Option<&'a str>,
    headers: &'a [u8],
}

impl BorrowedTarget<'_> {
    #[inline]
    fn into_core(self) -> Target {
        Target {
            service: self.service.to_owned(),
            handler: self.handler.to_owned(),
            key: self.key.map(str::to_owned),
            idempotency_key: self.idempotency_key.map(str::to_owned),
            scope: self.scope.map(str::to_owned),
            limit_key: self.limit_key.map(str::to_owned),
            headers: decode_header_list(self.headers)
                .into_iter()
                .map(|(k, v)| Header {
                    key: k.into(),
                    value: v.into(),
                })
                .collect(),
        }
    }
}

// =========================================================================
// Buffer allocation / release
// =========================================================================

/// Allocate an (uninitialized) `len`-byte buffer from Rust's global allocator and return its
/// pointer. The caller fills every byte and hands it back to an input call (e.g. `vm_notify_input`,
/// `vm_sys_state_set`), which takes ownership; it is then freed by dropping the wrapping `Bytes`
/// (see `take_buffer`/`NativeInput`) or, if never handed over, via `free_buffer`. Returns null for
/// `len == 0`. Capacity equals `len`, so the matching `Vec::from_raw_parts(ptr, len, len)` is sound.
#[no_mangle]
pub unsafe extern "C" fn alloc_buffer(len: usize) -> *mut u8 {
    if len == 0 {
        return std::ptr::null_mut();
    }
    let vec: Vec<MaybeUninit<u8>> = vec![MaybeUninit::uninit(); len];
    // into_raw leaks the memory to the caller.
    Box::into_raw(vec.into_boxed_slice()) as *mut u8
}

/// Free a buffer previously handed to the caller in a result `Slice`, or one returned by
/// `alloc_buffer` that was never handed to an input call.
#[no_mangle]
pub unsafe extern "C" fn free_buffer(ptr: *mut u8, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }
    drop(Vec::from_raw_parts(ptr, len, len));
}

// =========================================================================
// Unsafe primitives — the *only* raw-pointer touchpoints
// =========================================================================

#[inline]
fn assert_not_null<T>(s: *const T) {
    if s.is_null() {
        panic!("null pointer passed across the shared-core boundary");
    }
}

// =========================================================================
// Safe (de)coding helpers — operate purely on borrowed `&[u8]`
// =========================================================================
//
// Reading is done over `&[u8]` (which implements `bytes::Buf`): the methods
// advance the slice in place, so decoders thread a `&mut &[u8]` cursor.
// Writing is done over `Vec<u8>` (which implements `bytes::BufMut`).

/// Validate a borrowed byte slice as UTF-8. Inbound strings are copied into an
/// owned `String` for the call (copy-for-now).
#[inline]
fn utf8(buf: &[u8]) -> &str {
    std::str::from_utf8(buf).expect("input is valid UTF-8")
}

/// Append an optional string to a blob as `(u8 present, [u32 len, bytes])`: a leading
/// present byte (1/0) then, when present, the `put_str` encoding.
#[inline]
fn put_opt_str(buf: &mut Vec<u8>, value: Option<&str>) {
    match value {
        Some(s) => {
            buf.put_u8(1);
            put_str(buf, s);
        }
        None => buf.put_u8(0),
    }
}

/// Build a success `NonEmptyValue` from an already-owned payload (no copy; the buffer's
/// ownership was transferred across the boundary via `take_buffer`).
#[inline]
fn success_value(value: Bytes) -> NonEmptyValue {
    NonEmptyValue::Success(value)
}

/// Build a failure `NonEmptyValue` from the encoded failure blob (see `decode_failure`).
#[inline]
fn failure_value(failure: &[u8]) -> NonEmptyValue {
    NonEmptyValue::Failure(decode_failure(&mut { failure }))
}

fn decode_failure(buf: &mut &[u8]) -> TerminalFailure {
    let code = buf.get_u16_le();
    let message = get_string(buf);
    let meta_count = buf.get_u32_le();
    let mut metadata = Vec::with_capacity(meta_count as usize);
    for _ in 0..meta_count {
        let k = get_string(buf);
        let v = get_string(buf);
        metadata.push((k, v));
    }
    TerminalFailure {
        code,
        message,
        metadata,
    }
}

/// Retry policy encoding: an empty slice means no policy (the core's default); otherwise
/// u64 initial, f32 factor, opt(u64 max_interval), opt(u32 max_attempts), opt(u64 max_duration).
fn decode_retry_policy(buf: &[u8]) -> RetryPolicy {
    if buf.is_empty() {
        return RetryPolicy::default();
    }
    let mut r = buf;
    RetryPolicy::Exponential {
        initial_interval: Duration::from_millis(r.get_u64_le()),
        factor: r.get_f32_le(),
        max_interval: get_opt_u64(&mut r).map(Duration::from_millis),
        max_attempts: get_opt_u32(&mut r),
        max_duration: get_opt_u64(&mut r).map(Duration::from_millis),
        on_max_attempts: Default::default(),
    }
}

fn decode_header_list(buf: &[u8]) -> Vec<(String, String)> {
    if buf.is_empty() {
        return Vec::new();
    }
    let mut r = buf;
    let count = r.get_u32_le();
    let mut out = Vec::with_capacity(count as usize);
    for _ in 0..count {
        let k = get_string(&mut r);
        let v = get_string(&mut r);
        out.push((k, v));
    }
    out
}

/// Await future tree encoding: u8 tag; tag 0 (Single) → u32 handle; tags 1..=5
/// → u32 count, count*node. Tags mirror `UnresolvedFuture` variants in order:
/// 0 Single, 1 FirstCompleted, 2 AllCompleted, 3 FirstSucceededOrAllFailed,
/// 4 AllSucceededOrFirstFailed, 5 Unknown.
fn decode_future(buf: &mut &[u8]) -> UnresolvedFuture {
    let tag = buf.get_u8();
    if tag == 0 {
        return UnresolvedFuture::Single(NotificationHandle::from(buf.get_u32_le()));
    }
    let count = buf.get_u32_le();
    let mut children = Vec::with_capacity(count as usize);
    for _ in 0..count {
        children.push(decode_future(buf));
    }
    match tag {
        1 => UnresolvedFuture::FirstCompleted(children),
        2 => UnresolvedFuture::AllCompleted(children),
        3 => UnresolvedFuture::FirstSucceededOrAllFailed(children),
        4 => UnresolvedFuture::AllSucceededOrFirstFailed(children),
        _ => UnresolvedFuture::Unknown(children),
    }
}

/// Reads a u32-length-prefixed UTF-8 string, advancing the cursor.
#[inline]
fn get_string(buf: &mut &[u8]) -> String {
    let len = buf.get_u32_le() as usize;
    let (head, tail) = buf.split_at(len);
    let s = utf8(head).to_owned();
    *buf = tail;
    s
}

/// Reads an `Option<u32>` encoded as a `u8` present-flag then the value.
#[inline]
fn get_opt_u32(buf: &mut &[u8]) -> Option<u32> {
    (buf.get_u8() != 0).then(|| buf.get_u32_le())
}

/// Reads an `Option<u64>` encoded as a `u8` present-flag then the value.
#[inline]
fn get_opt_u64(buf: &mut &[u8]) -> Option<u64> {
    (buf.get_u8() != 0).then(|| buf.get_u64_le())
}

/// Writes a u32-length-prefixed UTF-8 string.
#[inline]
fn put_str(buf: &mut Vec<u8>, s: &str) {
    buf.put_u32_le(s.len() as u32);
    buf.put_slice(s.as_bytes());
}
