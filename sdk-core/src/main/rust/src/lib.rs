//! Native (C ABI) wrapper around `restate-sdk-shared-core` for the Java SDK.
//!
//! This crate is compiled to a native `cdylib` and called from Java via the
//! Panama Foreign Function & Memory API (JDK 23+). The boundary is a plain C
//! ABI designed to be cbindgen/jextract-friendly:
//!
//!   - The VM instance is an opaque `*mut VmHandle` returned by `vm_new` and
//!     released by `vm_free`. The Java side guarantees exclusive, serialized
//!     access per handle (single thread at a time, no reentrancy), so we use a
//!     plain `Box` rather than `Rc<RefCell<..>>`.
//!   - Every fallible call writes its result into a caller-provided, **typed**
//!     `#[repr(C, u32)]` tagged-union out-parameter — `Ok { state, .. }` carries
//!     the payload, `Err { error }` carries a `VmError` (a failed op closes the
//!     VM, so the error arm has no state). `EmptyResult`, `HandleResult`,
//!     `CallResult`, `RunResult`, `AwakeableResult`, `IsReadyToExecuteResult`,
//!     `InputResult`. `AwaitResult` and `Notification` instead mirror their
//!     domain response one-variant-per-arm (no piggybacked state — `AwaitResult`'s
//!     only state-relevant outcome is `Suspended`, recorded locally by the SDK).
//!     Variant tags are prefixed with the enum name (cbindgen `prefix_with_name`)
//!     so jextract emits collision-free constants/bodies. Infallible calls skip the
//!     union: `take_output` and `get_response_content_type` write a bare `Slice`.
//!   - Scalars cross as direct args; byte/string payloads cross by value as one of
//!     two `#[repr(C)] { ptr, len }` structs that name their ownership: a
//!     **`ForeignSlice`** is borrowed Java-`Arena` memory (Rust reads it in-call and
//!     never frees it — keys, names, ids, and the encoded header/future/failure/
//!     retry-policy blobs), a **`Slice`** is Rust-allocator memory (`alloc_buffer`/
//!     `leak_buffer`) that Rust owns. Values cross via per-shape `_success`/`_failure`
//!     fn variants (no `NonEmptyValue` ABI struct).
//!   - **Memory ownership**: the type says who frees, and *the callee always
//!     frees*. A buffer can only be freed by the allocator that made it — a JVM
//!     `Arena`/`Unsafe` allocation is not Rust's `Global`, and freeing one across
//!     that line is UB — so whatever Rust must retain past the call, Rust must also
//!     allocate. A `ForeignSlice` stays Java's: Rust reads it in-call only and its
//!     confined `Arena` frees it; Rust must never free it. A `Slice` is Rust's:
//!     opaque payloads transfer ownership *in* — allocated by `alloc_buffer`, filled
//!     by Java, re-owned here via `Slice::take` (`Bytes::from_owner`) and freed when
//!     the `CoreVM` drops them. This is exactly why inputs are `alloc_buffer`-backed
//!     rather than a Java `Arena`: the core retains them past the call, so a
//!     Java-owned arena would either free too early (use-after-free) or never
//!     (leak). Outputs are `Slice`s the caller frees via `free_buffer`. Encoded
//!     blobs and out-param structs stay transient (decoded/copied in-call).
//!   - **FFI organization**: each exported function is a thin `unsafe` shim
//!     (`_vm_*` with `#[export_name = "vm_*"]`) whose only job is to isolate the
//!     raw-pointer reads (`slice_from`, `vm_mut`) and the out-param `write_out`,
//!     handing borrowed `&[u8]` to a **safe** `#[inline] fn vm_*(vm: &mut CoreVM,
//!     ..)`. All payload (de)coding (UTF-8, header/future/failure/retry-policy
//!     blobs, value building) is safe code living in those inner fns. `vm_free` /
//!     `free_buffer` / `init` are inherently unsafe (no SM call) and stay direct.

#![allow(clippy::missing_safety_doc)]
#![allow(clippy::not_unsafe_ptr_arg_deref)]
#![allow(clippy::too_many_arguments)]

use bytes::{Buf, BufMut, Bytes};
use restate_sdk_shared_core::{
    AttachInvocationTarget, AwaitResponse, AwakeableHandle, CoreVM, Error, Header, HeaderMap,
    NonEmptyValue, NotificationHandle, PayloadOptions, RetryPolicy, RunExitResult, RunHandle,
    Target, TerminalFailure, UnresolvedFuture, VMOptions, Value, VM,
};
use std::borrow::Cow;
use std::convert::Infallible;
use std::ffi::c_void;
use std::io::Write;
use std::mem::MaybeUninit;
use std::slice;
use std::time::Duration;
use restate_sdk_shared_core::tracing_pretty::{Pretty, PrettyFields};
use tracing::level_filters::LevelFilter;
use tracing::{Level, };
use tracing_subscriber::fmt::format::FmtSpan;
use tracing_subscriber::fmt::MakeWriter;
use tracing_subscriber::layer::{Layer, SubscriberExt};
use tracing_subscriber::Registry;
use tracing_subscriber::util::SubscriberInitExt;

// =========================================================================
// Init & logging
// =========================================================================

/// Zero-copy log sink installed by the host: `(level, msg_ptr, msg_len)`. `level` is the
/// [`AbiLogLevel`] ordinal; the `(ptr, len)` pair is a UTF-8 view into a Rust-owned, thread-local
/// buffer that is valid **only for the synchronous duration of this call**. The callee must copy
/// the bytes out before returning and must never retain the pointer — no allocation crosses the
/// boundary and nothing is freed here (contrast the owned `Slice` results). It must also never
/// unwind (the Java upcall swallows all exceptions), as unwinding across `extern "C"` is UB.
type LogCallback = extern "C" fn(level: u32, msg_ptr: *const u8, msg_len: usize);

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
pub unsafe extern "C" fn init(level: u32, log_callback: *const c_void) {
    std::panic::set_hook(Box::new(|panic| {
        eprintln!("[restate-shared-core] core panicked: {panic}");
    }));
    assert!(
        !log_callback.is_null(),
        "init called with a null log callback"
    );

    // Prepare log writer
    // SAFETY: the host passed a non-null upcall stub with the `LogCallback` signature. On all
    // supported targets a data pointer and a C function pointer share a representation.
    let callback: LogCallback = std::mem::transmute(log_callback);

    let level: Level = AbiLogLevel::from(level).into();
    let level_filter = LevelFilter::from_level(level);

    let fmt_layer = if level == Level::TRACE {
        tracing_subscriber::fmt::layer()
            .with_ansi(false)
            .without_time()
            .with_span_events(FmtSpan::ENTER)
            .with_writer(MakeJavaCallbackWriter(callback))
            .event_format(
                Pretty::default()
                    .without_time()
                    .with_thread_names(false)
                    .with_thread_ids(false)
                    .with_target(true)
                    .with_level(true),
            )
            .fmt_fields(PrettyFields::default())
            .boxed()
    } else {
        tracing_subscriber::fmt::layer()
            .with_ansi(false)
            .without_time()
            .with_thread_names(false)
            .with_thread_ids(false)
            .with_file(false)
            .with_line_number(false)
            .with_target(false)
            .with_level(false)
            .with_span_events(FmtSpan::NONE)
            .with_writer(MakeJavaCallbackWriter(callback))
            .boxed()
    };

    Registry::default().with(
        fmt_layer
            .with_filter(level_filter),
    ).init();
}

pub struct MakeJavaCallbackWriter(LogCallback);

impl<'a> MakeWriter<'a> for MakeJavaCallbackWriter {
    type Writer = JavaCallbackWriter;

    fn make_writer(&'a self) -> Self::Writer {
        JavaCallbackWriter {
            buffer: vec![],
            level: AbiLogLevel::Trace as u32, // if no level is known, assume the most detailed
            log_callback: self.0,
        }
    }

    fn make_writer_for(&'a self, meta: &tracing::Metadata<'_>) -> Self::Writer {
        let level = *meta.level();
        JavaCallbackWriter {
            buffer: vec![],
            level: AbiLogLevel::from(level) as u32,
            log_callback: self.0,
        }
    }
}

pub struct JavaCallbackWriter {
    buffer: Vec<u8>,
    level: u32,
    log_callback: LogCallback,
}

impl Write for JavaCallbackWriter {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        self.buffer.write(buf)
    }

    fn flush(&mut self) -> std::io::Result<()> {
        if !self.buffer.is_empty() {
            (self.log_callback)(self.level, self.buffer.as_ptr(), self.buffer.len());
        }
        Ok(())
    }

    fn write_all(&mut self, buf: &[u8]) -> std::io::Result<()> {
        (self.log_callback)(self.level, buf.as_ptr(), buf.len());
        Ok(())
    }
}

impl Drop for JavaCallbackWriter {
    fn drop(&mut self) {
        let _ = JavaCallbackWriter::flush(self);
    }
}

#[repr(u32)]
enum AbiLogLevel {
    Trace = 0,
    Debug = 1,
    Info = 2,
    Warn = 3,
    Error = 4,
}

impl From<u32> for AbiLogLevel {
    fn from(value: u32) -> Self {
        match value {
            0 => AbiLogLevel::Trace,
            1 => AbiLogLevel::Debug,
            2 => AbiLogLevel::Info,
            3 => AbiLogLevel::Warn,
            _ => AbiLogLevel::Error,
        }
    }
}

impl From<Level> for AbiLogLevel {
    fn from(value: Level) -> Self {
        match value {
            Level::TRACE => AbiLogLevel::Trace,
            Level::DEBUG => AbiLogLevel::Debug,
            Level::INFO => AbiLogLevel::Info,
            Level::WARN => AbiLogLevel::Warn,
            Level::ERROR => AbiLogLevel::Error,
        }
    }
}

impl From<AbiLogLevel> for Level {
    fn from(value: AbiLogLevel) -> Self {
        match value {
            AbiLogLevel::Trace => Level::TRACE,
            AbiLogLevel::Debug => Level::DEBUG,
            AbiLogLevel::Info => Level::INFO,
            AbiLogLevel::Warn => Level::WARN,
            AbiLogLevel::Error => Level::ERROR,
        }
    }
}

// =========================================================================
// VM handle
// =========================================================================

pub struct VmHandle {
    vm: CoreVM,
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

/// Rust-allocator memory (`alloc_buffer` / `leak_buffer`) crossing the boundary, owned by Rust.
/// Either transferred **in** — Java filled a buffer it obtained from `alloc_buffer` (Rust's
/// allocator) and Rust re-owns it here (`take`/`take_string`) and frees it — or returned **out**:
/// Rust leaks it and the caller frees it via `free_buffer`. `ptr` is null / `len` is 0 for the
/// empty slice. Contrast `ForeignSlice`, which is borrowed Java-`Arena` memory Rust must never free.
#[repr(C)]
pub struct Slice {
    pub ptr: *const u8,
    pub len: usize,
}

impl Slice {
    const EMPTY: Slice = Slice {
        ptr: std::ptr::null(),
        len: 0,
    };

    /// Leak an owned buffer into a `Slice` the caller frees via `free_buffer`.
    #[inline]
    fn from_vec(v: Vec<u8>) -> Self {
        if v.is_empty() {
            return Self::EMPTY;
        }
        let len = v.len();
        let ptr = Box::into_raw(v.into_boxed_slice()) as *mut u8;
        Slice { ptr, len }
    }

    /// Leak an owned buffer into a `Slice` the caller frees via `free_buffer`.
    #[inline]
    fn from_bytes(b: Bytes) -> Self {
        Self::from_vec(Vec::from(b))
    }

    /// Leak an owned buffer into a `Slice` the caller frees via `free_buffer`.
    #[inline]
    fn from_string(s: String) -> Self {
        Self::from_vec(s.into_bytes())
    }

    /// Re-take ownership of a transferred-in payload (Java filled an `alloc_buffer` buffer) as
    /// `Bytes`, zero-copy. The caller must not touch the buffer after the call.
    #[inline]
    unsafe fn take(self) -> Bytes {
        let ptr = self.ptr as *mut u8;
        let len = self.len;
        if ptr.is_null() || len == 0 {
            Bytes::new()
        } else {
            Bytes::from_owner(NativeInput { ptr, len })
        }
    }

    /// Like `take`, but re-owns the payload as a `String` (UTF-8 unchecked — Java already encoded it).
    #[inline]
    unsafe fn take_string(self) -> String {
        let ptr = self.ptr as *mut u8;
        let len = self.len;
        if ptr.is_null() || len == 0 {
            String::new()
        } else {
            // SAFETY: reconstruct the exact `Vec` leaked by `alloc_buffer` (cap == len) and wrap it as a
            // `String`; the bytes are valid UTF-8 by construction on the Java side.
            String::from_utf8_unchecked(Vec::from_raw_parts(ptr, len, len))
        }
    }
}

/// A borrowed view into caller (Java) memory: the Java side owns the allocation (its confined
/// `Arena`) and frees it; Rust only reads it for the duration of the call and must **never** free
/// it. Used for transient inputs — keys, names, ids, and the encoded blobs (headers, the await
/// future tree, failures, retry policy). Contrast `Slice`, which is Rust-allocator memory Rust owns.
#[derive(Clone, Copy)]
#[repr(C)]
pub struct ForeignSlice {
    pub ptr: *const u8,
    pub len: usize,
}

impl ForeignSlice {
    /// Borrow the foreign bytes for the duration of the call (Java keeps ownership and frees them).
    #[inline]
    unsafe fn as_slice<'a>(self) -> &'a [u8] {
        if self.ptr.is_null() || self.len == 0 {
            &[]
        } else {
            slice::from_raw_parts(self.ptr, self.len)
        }
    }

    /// Borrow the foreign bytes as str for the duration of the call (Java keeps ownership and frees them).
    #[inline]
    unsafe fn as_str<'a>(self) -> &'a str {
        if self.ptr.is_null() || self.len == 0 {
            ""
        } else {
            str::from_utf8_unchecked(slice::from_raw_parts(self.ptr, self.len))
        }
    }
}

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
    Ok { handle: *mut VmHandle },
    Err { error: VmError },
}

/// Result of a call returning nothing (state mutations, completions, end). On
/// `Err` the VM is closed, so only the error travels (no state piggyback).
#[repr(C, u32)]
pub enum EmptyResult {
    Ok { state: u32 },
    Err { error: VmError },
}

impl EmptyResult {
    #[inline]
    fn build(r: Result<(), Error>, state: u32) -> Self {
        match r {
            Ok(()) => EmptyResult::Ok { state },
            Err(e) => EmptyResult::Err {
                error: VmError::of(&e),
            },
        }
    }
}

/// Result of a call returning a single notification handle.
#[repr(C, u32)]
pub enum HandleResult {
    Ok { state: u32, handle: u32 },
    Err { error: VmError },
}

impl HandleResult {
    #[inline]
    fn build(r: Result<NotificationHandle, Error>, state: u32) -> Self {
        match r {
            Ok(h) => HandleResult::Ok {
                state,
                handle: h.into(),
            },
            Err(e) => HandleResult::Err {
                error: VmError::of(&e),
            },
        }
    }
}

/// Result of `sys_call`: the invocation-id and result notification handles.
#[repr(C, u32)]
pub enum CallResult {
    Ok {
        state: u32,
        invocation_id_handle: u32,
        result_handle: u32,
    },
    Err {
        error: VmError,
    },
}

/// Result of `sys_run`: the run handle plus whether it was replayed.
#[repr(C, u32)]
pub enum RunResult {
    Ok {
        state: u32,
        handle: u32,
        replayed: u32,
    },
    Err {
        error: VmError,
    },
}

/// Result of `sys_awakeable`: the handle plus the owned awakeable id.
#[repr(C, u32)]
pub enum AwakeableResult {
    Ok { state: u32, handle: u32, id: Slice },
    Err { error: VmError },
}

#[inline]
unsafe fn write_out<T>(out: *mut T, value: T) {
    debug_assert!(!out.is_null());
    std::ptr::write(out, value);
}

// =========================================================================
// Lifecycle
// =========================================================================

/// Create a new VM from the encoded header list, writing the typed result into
/// `out`: `Ok { handle }` on success, `Err { error }` on failure.
#[export_name = "vm_new"]
pub unsafe extern "C" fn _vm_new(headers: ForeignSlice, out: *mut VmNewResult) {
    let r = match vm_new(headers.as_slice()) {
        Ok(vm) => VmNewResult::Ok {
            handle: Box::into_raw(vm),
        },
        Err(e) => VmNewResult::Err {
            error: VmError::of(&e),
        },
    };
    write_out(out, r);
}

#[inline]
fn vm_new(headers_buf: &[u8]) -> Result<Box<VmHandle>, Error> {
    let headers = decode_header_list(headers_buf);
    CoreVM::new(Headers(headers), VMOptions::default()).map(|vm| Box::new(VmHandle { vm }))
}

#[no_mangle]
pub unsafe extern "C" fn vm_free(handle: *mut VmHandle) {
    assert_not_null(handle);
    drop(Box::from_raw(handle));
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
    let stacktrace = (!stacktrace.ptr.is_null()).then(|| stacktrace.take_string());
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

/// Infallible: returns the response's `content-type` header value as an owned `Slice` (empty when
/// absent). The header scan lives here so the boundary needn't ship the whole header list just to
/// look up one value; the caller copies it out + frees it via `free_buffer`.
#[export_name = "vm_get_response_content_type"]
pub unsafe extern "C" fn _vm_get_response_content_type(handle: *mut VmHandle, out: *mut Slice) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_get_response_content_type(&mut h.vm)
            .map(Slice::from_string)
            .unwrap_or(Slice::EMPTY),
    );
}

#[inline]
fn vm_get_response_content_type(vm: &mut CoreVM) -> Option<String> {
    VM::get_response_head(vm)
        .headers
        .into_iter()
        .find(|h| h.key.eq_ignore_ascii_case("content-type"))
        .map(|h| h.value.into_owned())
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

/// Result of `do_await`, mirroring `AwaitResponse` one variant per arm (plus
/// `Suspended` for the suspended error and `Err` for any other error). The VM state
/// is not piggybacked here: the only non-error outcomes all leave the VM live, and the
/// one state-relevant outcome — `Suspended`, after which the VM is Closed — is recorded
/// locally by the SDK (which then aborts the user code). `Err` is thrown by the caller.
#[repr(C, u32)]
pub enum AwaitResult {
    AnyCompleted,
    WaitingExternalProgress,
    ExecuteRun { run_handle: u32 },
    CancelSignalReceived,
    Err { error: VmError },
}

/// Input is the encoded await future tree; see `decode_future`.
#[export_name = "vm_do_await"]
pub unsafe extern "C" fn _vm_do_await(
    handle: *mut VmHandle,
    future: ForeignSlice,
    out: *mut AwaitResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_do_await(&mut h.vm, future.as_slice()));
}

#[inline]
fn vm_do_await(vm: &mut CoreVM, future_buf: &[u8]) -> AwaitResult {
    let future = decode_future(&mut { future_buf });
    match VM::do_await(vm, future) {
        Ok(AwaitResponse::AnyCompleted) => AwaitResult::AnyCompleted,
        Ok(AwaitResponse::WaitingExternalProgress { .. }) => AwaitResult::WaitingExternalProgress,
        Ok(AwaitResponse::ExecuteRun(run)) => AwaitResult::ExecuteRun {
            run_handle: run.into(),
        },
        Ok(AwaitResponse::CancelSignalReceived) => AwaitResult::CancelSignalReceived,
        Err(e) => AwaitResult::Err {
            error: VmError::of(&e),
        },
    }
}

/// Result of `take_notification` (the hot path), as a tagged union: each variant
/// carries exactly its own fields (no overloaded `value`/`extra`). `metadata` and
/// `keys` are still owned `Slice`s holding a little-endian `(u32 count,
/// count*(u32 len, bytes))` encoding (variable collections can't live inline). The
/// Java decoder throws on `Error`.
#[repr(C, u32)]
pub enum Notification {
    NotReady,
    Empty,
    Success {
        value: Slice,
    },
    TerminalFailure {
        code: u32,
        message: Slice,
        metadata: Slice,
    },
    StateKeys {
        keys: Slice,
    },
    InvocationId {
        id: Slice,
    },
    /// A VM/protocol error surfaced while resolving the notification. Carries the same
    /// `VmError` the fallible results' `Err` arms use (nested `#[repr(C)]`, so the layout
    /// is identical to inlining `code`/`message`); the Java decoder turns it into a
    /// `ProtocolException` via the shared `vmError` path.
    Error {
        error: VmError,
    },
}

#[export_name = "vm_take_notification"]
pub unsafe extern "C" fn _vm_take_notification(
    handle: *mut VmHandle,
    notification_handle: u32,
    out: *mut Notification,
) {
    let h = vm_mut(handle);
    write_out(out, vm_take_notification(&mut h.vm, notification_handle));
}

#[inline]
fn vm_take_notification(vm: &mut CoreVM, notification_handle: u32) -> Notification {
    encode_notification(VM::take_notification(
        vm,
        NotificationHandle::from(notification_handle),
    ))
}

fn encode_notification(result: Result<Option<Value>, Error>) -> Notification {
    match result {
        Ok(None) => Notification::NotReady,
        Ok(Some(Value::Void)) => Notification::Empty,
        // `Vec::from(Bytes)` avoids the copy when the buffer is uniquely owned (the eventual
        // `shrink_to_fit` in `leak_buffer` is then a no-op); otherwise it is a single copy,
        // never worse than `to_vec`.
        Ok(Some(Value::Success(bytes))) => Notification::Success {
            value: Slice::from_vec(Vec::from(bytes)),
        },
        Ok(Some(Value::Failure(TerminalFailure {
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
            Notification::TerminalFailure {
                code: code as u32,
                message: Slice::from_vec(message.into_bytes()),
                metadata: Slice::from_vec(buf),
            }
        }
        Ok(Some(Value::StateKeys(keys))) => {
            let mut buf = Vec::new();
            buf.put_u32_le(keys.len() as u32);
            for k in keys {
                put_str(&mut buf, &k);
            }
            Notification::StateKeys {
                keys: Slice::from_vec(buf),
            }
        }
        Ok(Some(Value::InvocationId(id))) => Notification::InvocationId {
            id: Slice::from_vec(id.into_bytes()),
        },
        Err(e) => Notification::Error {
            error: VmError::of(&e),
        },
    }
}

/// Result of `sys_input`. Everything the Java layer copies out anyway is packed into one
/// `metadata` blob (one allocation + one `free_buffer` instead of seven): `random_seed` (u64),
/// `invocation_id` (str), `key` (str), `headers` (`u32 count, count*(str,str)`), then the V7
/// optionals `scope`/`limit_key`/`idempotency_key` each as `(u8 present, [str])`. Strings are
/// encoded `u32 len, bytes`; integers little-endian. The handler `input` payload stays a separate
/// owned `Slice` — Java reinterprets it zero-copy and propagates it to the user deserialization
/// layer rather than copying it out. `state` rides inline (the usual piggyback).
#[repr(C, u32)]
pub enum InputResult {
    Ok {
        state: u32,
        metadata: Slice,
        input: Slice,
    },
    Err {
        error: VmError,
    },
}

/// Writes the typed `Input` into an `InputResult`. Only `headers` is encoded as a
/// blob: `u32 count, count*(str key, str value)`.
#[export_name = "vm_sys_input"]
pub unsafe extern "C" fn _vm_sys_input(handle: *mut VmHandle, out: *mut InputResult) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_input(&mut h.vm));
}

#[inline]
fn vm_sys_input(vm: &mut CoreVM) -> InputResult {
    match VM::sys_input(vm) {
        Ok(input) => {
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
            InputResult::Ok {
                state: state_of(vm),
                metadata: Slice::from_vec(meta),
                input: Slice::from_bytes(input.input),
            }
        }
        Err(e) => InputResult::Err {
            error: VmError::of(&e),
        },
    }
}

// =========================================================================
// State
// =========================================================================

#[export_name = "vm_sys_state_get"]
pub unsafe extern "C" fn _vm_sys_state_get(
    handle: *mut VmHandle,
    key: ForeignSlice,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_state_get(&mut h.vm, key.as_str()));
}

#[inline]
fn vm_sys_state_get(vm: &mut CoreVM, key: &str) -> HandleResult {
    let r = VM::sys_state_get(vm, key.to_owned(), PayloadOptions::default());
    HandleResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_state_get_keys"]
pub unsafe extern "C" fn _vm_sys_state_get_keys(handle: *mut VmHandle, out: *mut HandleResult) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_state_get_keys(&mut h.vm));
}

#[inline]
fn vm_sys_state_get_keys(vm: &mut CoreVM) -> HandleResult {
    let r = VM::sys_state_get_keys(vm);
    HandleResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_state_set"]
pub unsafe extern "C" fn _vm_sys_state_set(
    handle: *mut VmHandle,
    key: ForeignSlice,
    value: Slice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_state_set(&mut h.vm, key.as_str(), value.take()));
}

#[inline]
fn vm_sys_state_set(vm: &mut CoreVM, key: &str, value: Bytes) -> EmptyResult {
    let r = VM::sys_state_set(vm, key.to_owned(), value, PayloadOptions::default());
    EmptyResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_state_clear"]
pub unsafe extern "C" fn _vm_sys_state_clear(
    handle: *mut VmHandle,
    key: ForeignSlice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_state_clear(&mut h.vm, key.as_str()));
}

#[inline]
fn vm_sys_state_clear(vm: &mut CoreVM, key: &str) -> EmptyResult {
    let r = VM::sys_state_clear(vm, key.to_owned());
    EmptyResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_state_clear_all"]
pub unsafe extern "C" fn _vm_sys_state_clear_all(handle: *mut VmHandle, out: *mut EmptyResult) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_state_clear_all(&mut h.vm));
}

#[inline]
fn vm_sys_state_clear_all(vm: &mut CoreVM) -> EmptyResult {
    let r = VM::sys_state_clear_all(vm);
    EmptyResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_sleep"]
pub unsafe extern "C" fn _vm_sys_sleep(
    handle: *mut VmHandle,
    name: ForeignSlice,
    wake_up_time_since_unix_epoch_millis: u64,
    now_since_unix_epoch_millis: u64,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_sys_sleep(
            &mut h.vm,
            name.as_str(),
            wake_up_time_since_unix_epoch_millis,
            now_since_unix_epoch_millis,
        ),
    );
}

#[inline]
fn vm_sys_sleep(
    vm: &mut CoreVM,
    name: &str,
    wake_up_time_since_unix_epoch_millis: u64,
    now_since_unix_epoch_millis: u64,
) -> HandleResult {
    let r = VM::sys_sleep(
        vm,
        name.to_owned(),
        Duration::from_millis(wake_up_time_since_unix_epoch_millis),
        Some(Duration::from_millis(now_since_unix_epoch_millis)),
    );
    HandleResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_awakeable"]
pub unsafe extern "C" fn _vm_sys_awakeable(handle: *mut VmHandle, out: *mut AwakeableResult) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_awakeable(&mut h.vm));
}

#[inline]
fn vm_sys_awakeable(vm: &mut CoreVM) -> AwakeableResult {
    match VM::sys_awakeable(vm) {
        Ok(AwakeableHandle { id, handle }) => AwakeableResult::Ok {
            state: state_of(vm),
            handle: handle.into(),
            id: Slice::from_string(id),
        },
        Err(e) => AwakeableResult::Err {
            error: VmError::of(&e),
        },
    }
}

#[export_name = "vm_sys_complete_awakeable_success"]
pub unsafe extern "C" fn _vm_sys_complete_awakeable_success(
    handle: *mut VmHandle,
    id: ForeignSlice,
    value: Slice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_sys_complete_awakeable_success(&mut h.vm, id.as_str(), value.take()),
    );
}

#[inline]
fn vm_sys_complete_awakeable_success(vm: &mut CoreVM, id: &str, value: Bytes) -> EmptyResult {
    complete_awakeable(vm, id, success_value(value))
}

#[export_name = "vm_sys_complete_awakeable_failure"]
pub unsafe extern "C" fn _vm_sys_complete_awakeable_failure(
    handle: *mut VmHandle,
    id: ForeignSlice,
    failure: ForeignSlice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_sys_complete_awakeable_failure(&mut h.vm, id.as_str(), failure.as_slice()),
    );
}

#[inline]
fn vm_sys_complete_awakeable_failure(vm: &mut CoreVM, id: &str, failure: &[u8]) -> EmptyResult {
    complete_awakeable(vm, id, failure_value(failure))
}

#[inline]
fn complete_awakeable(vm: &mut CoreVM, id: &str, value: NonEmptyValue) -> EmptyResult {
    let r = VM::sys_complete_awakeable(vm, id.to_owned(), value, PayloadOptions::default());
    EmptyResult::build(r, state_of(vm))
}

// =========================================================================
// Call / send / invocation
// =========================================================================

#[export_name = "vm_sys_call"]
pub unsafe extern "C" fn _vm_sys_call(
    handle: *mut VmHandle,
    args: CallArguments,
    out: *mut CallResult,
) {
    let h = vm_mut(handle);
    let target = args.borrow();
    let input = args.input.take();
    write_out(out, vm_sys_call(&mut h.vm, target, input));
}

#[inline]
fn vm_sys_call(vm: &mut CoreVM, target: BorrowedTarget, input: Bytes) -> CallResult {
    match VM::sys_call(
        vm,
        target.into_core(),
        input,
        None,
        PayloadOptions::default(),
    ) {
        Ok(call_handle) => CallResult::Ok {
            state: state_of(vm),
            invocation_id_handle: call_handle.invocation_id_notification_handle.into(),
            result_handle: call_handle.call_notification_handle.into(),
        },
        Err(e) => CallResult::Err {
            error: VmError::of(&e),
        },
    }
}

#[export_name = "vm_sys_send"]
pub unsafe extern "C" fn _vm_sys_send(
    handle: *mut VmHandle,
    args: CallArguments,
    delay_millis: u64,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    let target = args.borrow();
    let input = args.input.take();
    write_out(out, vm_sys_send(&mut h.vm, target, input, delay_millis));
}

#[inline]
fn vm_sys_send(
    vm: &mut CoreVM,
    target: BorrowedTarget,
    input: Bytes,
    delay_millis: u64,
) -> HandleResult {
    // 0 means "no delay"; any non-zero value is the absolute wake-up time in epoch millis.
    let delay = (delay_millis != 0).then(|| Duration::from_millis(delay_millis));
    let r = VM::sys_send(
        vm,
        target.into_core(),
        input,
        delay,
        None,
        PayloadOptions::default(),
    )
    .map(|s| s.invocation_id_notification_handle);
    HandleResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_cancel_invocation"]
pub unsafe extern "C" fn _vm_sys_cancel_invocation(
    handle: *mut VmHandle,
    id: ForeignSlice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_cancel_invocation(&mut h.vm, id.as_slice()));
}

#[inline]
fn vm_sys_cancel_invocation(vm: &mut CoreVM, invocation_id: &[u8]) -> EmptyResult {
    let r = VM::sys_cancel_invocation(vm, utf8(invocation_id).to_owned());
    EmptyResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_attach_invocation"]
pub unsafe extern "C" fn _vm_sys_attach_invocation(
    handle: *mut VmHandle,
    id: ForeignSlice,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_attach_invocation(&mut h.vm, id.as_slice()));
}

#[inline]
fn vm_sys_attach_invocation(vm: &mut CoreVM, invocation_id: &[u8]) -> HandleResult {
    let r = VM::sys_attach_invocation(
        vm,
        AttachInvocationTarget::InvocationId(utf8(invocation_id).to_owned()),
    );
    HandleResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_get_invocation_output"]
pub unsafe extern "C" fn _vm_sys_get_invocation_output(
    handle: *mut VmHandle,
    id: ForeignSlice,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_get_invocation_output(&mut h.vm, id.as_slice()));
}

#[inline]
fn vm_sys_get_invocation_output(vm: &mut CoreVM, invocation_id: &[u8]) -> HandleResult {
    let r = VM::sys_get_invocation_output(
        vm,
        AttachInvocationTarget::InvocationId(utf8(invocation_id).to_owned()),
    );
    HandleResult::build(r, state_of(vm))
}

// =========================================================================
// Promises & signals
// =========================================================================

#[export_name = "vm_sys_promise_get"]
pub unsafe extern "C" fn _vm_sys_promise_get(
    handle: *mut VmHandle,
    key: ForeignSlice,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_promise_get(&mut h.vm, key.as_slice()));
}

#[inline]
fn vm_sys_promise_get(vm: &mut CoreVM, key: &[u8]) -> HandleResult {
    let r = VM::sys_get_promise(vm, utf8(key).to_owned());
    HandleResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_promise_peek"]
pub unsafe extern "C" fn _vm_sys_promise_peek(
    handle: *mut VmHandle,
    key: ForeignSlice,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_promise_peek(&mut h.vm, key.as_slice()));
}

#[inline]
fn vm_sys_promise_peek(vm: &mut CoreVM, key: &[u8]) -> HandleResult {
    let r = VM::sys_peek_promise(vm, utf8(key).to_owned());
    HandleResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_promise_complete_success"]
pub unsafe extern "C" fn _vm_sys_promise_complete_success(
    handle: *mut VmHandle,
    key: ForeignSlice,
    value: Slice,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_sys_promise_complete_success(&mut h.vm, key.as_slice(), value.take()),
    );
}

#[inline]
fn vm_sys_promise_complete_success(vm: &mut CoreVM, key: &[u8], value: Bytes) -> HandleResult {
    promise_complete(vm, key, success_value(value))
}

#[export_name = "vm_sys_promise_complete_failure"]
pub unsafe extern "C" fn _vm_sys_promise_complete_failure(
    handle: *mut VmHandle,
    key: ForeignSlice,
    failure: ForeignSlice,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_sys_promise_complete_failure(&mut h.vm, key.as_slice(), failure.as_slice()),
    );
}

#[inline]
fn vm_sys_promise_complete_failure(vm: &mut CoreVM, key: &[u8], failure: &[u8]) -> HandleResult {
    promise_complete(vm, key, failure_value(failure))
}

#[inline]
fn promise_complete(vm: &mut CoreVM, key: &[u8], value: NonEmptyValue) -> HandleResult {
    let r = VM::sys_complete_promise(vm, utf8(key).to_owned(), value, PayloadOptions::default());
    HandleResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_create_signal_handle"]
pub unsafe extern "C" fn _vm_sys_create_signal_handle(
    handle: *mut VmHandle,
    name: ForeignSlice,
    out: *mut HandleResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_create_signal_handle(&mut h.vm, name.as_slice()));
}

#[inline]
fn vm_sys_create_signal_handle(vm: &mut CoreVM, name: &[u8]) -> HandleResult {
    let r = VM::create_signal_handle(vm, utf8(name).to_owned());
    HandleResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_complete_signal_success"]
pub unsafe extern "C" fn _vm_sys_complete_signal_success(
    handle: *mut VmHandle,
    target: ForeignSlice,
    name: ForeignSlice,
    value: Slice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_sys_complete_signal_success(&mut h.vm, target.as_slice(), name.as_slice(), value.take()),
    );
}

#[inline]
fn vm_sys_complete_signal_success(
    vm: &mut CoreVM,
    target: &[u8],
    name: &[u8],
    value: Bytes,
) -> EmptyResult {
    complete_signal(vm, target, name, success_value(value))
}

#[export_name = "vm_sys_complete_signal_failure"]
pub unsafe extern "C" fn _vm_sys_complete_signal_failure(
    handle: *mut VmHandle,
    target: ForeignSlice,
    name: ForeignSlice,
    failure: ForeignSlice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_sys_complete_signal_failure(
            &mut h.vm,
            target.as_slice(),
            name.as_slice(),
            failure.as_slice(),
        ),
    );
}

#[inline]
fn vm_sys_complete_signal_failure(
    vm: &mut CoreVM,
    target: &[u8],
    name: &[u8],
    failure: &[u8],
) -> EmptyResult {
    complete_signal(vm, target, name, failure_value(failure))
}

#[inline]
fn complete_signal(
    vm: &mut CoreVM,
    target_invocation_id: &[u8],
    name: &[u8],
    value: NonEmptyValue,
) -> EmptyResult {
    let r = VM::sys_complete_signal(
        vm,
        utf8(target_invocation_id).to_owned(),
        utf8(name).to_owned(),
        value,
    );
    EmptyResult::build(r, state_of(vm))
}

// =========================================================================
// Run
// =========================================================================

#[export_name = "vm_sys_run"]
pub unsafe extern "C" fn _vm_sys_run(
    handle: *mut VmHandle,
    name: ForeignSlice,
    out: *mut RunResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_run(&mut h.vm, name.as_slice()));
}

#[inline]
fn vm_sys_run(vm: &mut CoreVM, name: &[u8]) -> RunResult {
    match VM::sys_run(vm, utf8(name).to_owned()) {
        Ok(RunHandle { replayed, handle }) => RunResult::Ok {
            state: state_of(vm),
            handle: handle.into(),
            replayed: replayed as u32,
        },
        Err(e) => RunResult::Err {
            error: VmError::of(&e),
        },
    }
}

/// Propose a successful run completion (`value` = run result bytes).
#[export_name = "vm_propose_run_completion_success"]
pub unsafe extern "C" fn _vm_propose_run_completion_success(
    handle: *mut VmHandle,
    run_handle: u32,
    value: Slice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_propose_run_completion_success(&mut h.vm, run_handle, value.take()),
    );
}

#[inline]
fn vm_propose_run_completion_success(
    vm: &mut CoreVM,
    run_handle: u32,
    value: Bytes,
) -> EmptyResult {
    let result = RunExitResult::Success(value);
    propose_run_completion(vm, run_handle, result, RetryPolicy::default())
}

/// Propose a terminal-failure run completion (`failure` = encoded failure blob).
#[export_name = "vm_propose_run_completion_terminal_failure"]
pub unsafe extern "C" fn _vm_propose_run_completion_terminal_failure(
    handle: *mut VmHandle,
    run_handle: u32,
    failure: ForeignSlice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_propose_run_completion_terminal_failure(&mut h.vm, run_handle, failure.as_slice()),
    );
}

#[inline]
fn vm_propose_run_completion_terminal_failure(
    vm: &mut CoreVM,
    run_handle: u32,
    failure: &[u8],
) -> EmptyResult {
    let result = RunExitResult::TerminalFailure(decode_failure(&mut { failure }));
    propose_run_completion(vm, run_handle, result, RetryPolicy::default())
}

/// Propose a retryable-failure run completion. `params`: `u16 code, str message,
/// u8 has_stacktrace, [str stacktrace]`, then the retry-policy blob (see
/// `decode_retry_policy`). The attempt duration is a direct arg.
#[export_name = "vm_propose_run_completion_retryable_failure"]
pub unsafe extern "C" fn _vm_propose_run_completion_retryable_failure(
    handle: *mut VmHandle,
    run_handle: u32,
    attempt_duration_millis: u64,
    params: ForeignSlice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_propose_run_completion_retryable_failure(
            &mut h.vm,
            run_handle,
            attempt_duration_millis,
            params.as_slice(),
        ),
    );
}

#[inline]
fn vm_propose_run_completion_retryable_failure(
    vm: &mut CoreVM,
    run_handle: u32,
    attempt_duration_millis: u64,
    params: &[u8],
) -> EmptyResult {
    let mut r = params;
    let code = r.get_u16_le();
    let message = get_string(&mut r);
    let mut error = Error::new(code, message);
    if r.get_u8() != 0 {
        error = error.with_stacktrace(get_string(&mut r));
    }
    let retry_policy = decode_retry_policy(&mut r);
    let result = RunExitResult::RetryableFailure {
        attempt_duration: Duration::from_millis(attempt_duration_millis),
        error,
    };
    propose_run_completion(vm, run_handle, result, retry_policy)
}

#[inline]
fn propose_run_completion(
    vm: &mut CoreVM,
    run_handle: u32,
    result: RunExitResult,
    retry_policy: RetryPolicy,
) -> EmptyResult {
    let r = VM::propose_run_completion(vm, run_handle.into(), result, retry_policy);
    EmptyResult::build(r, state_of(vm))
}

// =========================================================================
// Output & termination
// =========================================================================

#[export_name = "vm_sys_write_output_success"]
pub unsafe extern "C" fn _vm_sys_write_output_success(
    handle: *mut VmHandle,
    value: Slice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_write_output_success(&mut h.vm, value.take()));
}

#[inline]
fn vm_sys_write_output_success(vm: &mut CoreVM, value: Bytes) -> EmptyResult {
    write_output(vm, success_value(value))
}

#[export_name = "vm_sys_write_output_failure"]
pub unsafe extern "C" fn _vm_sys_write_output_failure(
    handle: *mut VmHandle,
    failure: ForeignSlice,
    out: *mut EmptyResult,
) {
    let h = vm_mut(handle);
    write_out(
        out,
        vm_sys_write_output_failure(&mut h.vm, failure.as_slice()),
    );
}

#[inline]
fn vm_sys_write_output_failure(vm: &mut CoreVM, failure: &[u8]) -> EmptyResult {
    write_output(vm, failure_value(failure))
}

#[inline]
fn write_output(vm: &mut CoreVM, value: NonEmptyValue) -> EmptyResult {
    let r = VM::sys_write_output(vm, value, PayloadOptions::default());
    EmptyResult::build(r, state_of(vm))
}

#[export_name = "vm_sys_end"]
pub unsafe extern "C" fn _vm_sys_end(handle: *mut VmHandle, out: *mut EmptyResult) {
    let h = vm_mut(handle);
    write_out(out, vm_sys_end(&mut h.vm));
}

#[inline]
fn vm_sys_end(vm: &mut CoreVM) -> EmptyResult {
    let r = VM::sys_end(vm);
    EmptyResult::build(r, state_of(vm))
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

/// Owner for a Java-handed, `alloc_buffer`-allocated input buffer wrapped zero-copy as `Bytes`
/// (`Bytes::from_owner`). The core retains the `Bytes` (its protocol decoder slices payloads out of
/// it, the journal holds command/state values), so the buffer must outlive the call; it is freed
/// here when the last `Bytes` referencing it drops — at the latest when the `CoreVM` is dropped by
/// `vm_free`.
struct NativeInput {
    ptr: *mut u8,
    len: usize,
}

// SAFETY: the buffer is exclusively owned by this `NativeInput` (the caller relinquishes it on the
// call) and only ever read through `as_ref`, so moving it across threads is sound.
unsafe impl Send for NativeInput {}

impl AsRef<[u8]> for NativeInput {
    #[inline]
    fn as_ref(&self) -> &[u8] {
        // SAFETY: `ptr`/`len` describe the `alloc_buffer` allocation, fully initialized by the
        // caller before hand-off.
        unsafe { slice::from_raw_parts(self.ptr, self.len) }
    }
}

impl Drop for NativeInput {
    fn drop(&mut self) {
        // SAFETY: reconstruct the exact `Vec` leaked by `alloc_buffer` (cap == len) and drop it.
        unsafe { drop(Vec::from_raw_parts(self.ptr, self.len, self.len)) };
    }
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

/// Retry policy encoding: u8 has_policy; if 1: u64 initial, f32 factor,
/// opt(u64 max_interval), opt(u32 max_attempts), opt(u64 max_duration).
fn decode_retry_policy(buf: &mut &[u8]) -> RetryPolicy {
    if buf.get_u8() == 0 {
        return RetryPolicy::default();
    }
    RetryPolicy::Exponential {
        initial_interval: Duration::from_millis(buf.get_u64_le()),
        factor: buf.get_f32_le(),
        max_interval: get_opt_u64(buf).map(Duration::from_millis),
        max_attempts: get_opt_u32(buf),
        max_duration: get_opt_u64(buf).map(Duration::from_millis),
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
