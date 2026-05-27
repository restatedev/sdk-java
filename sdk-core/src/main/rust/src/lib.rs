//! Rust WASM wrapper around `restate-sdk-shared-core` for the Java SDK.
//!
//! Mirrors sdk-go/shared-core/src/lib.rs in structure exactly.
//! The only difference is CBOR (ciborium + serde) instead of protobuf (prost).
//!
//! Structure:
//!   - Each exported function is a thin `pub unsafe extern "C"` wrapper (prefixed `_`)
//!     that calls `ptr_to_input` / `output_to_ptr` and delegates to a safe inner fn.
//!   - Inner functions take `&Rc<RefCell<WasmVM>>` and return a typed CBOR response.
//!   - `From` impls at the bottom convert core results to CBOR response types,
//!     enabling `.into()` in inner functions (same pattern as Go).

#![allow(clippy::missing_safety_doc)]

use bytes::Bytes;
use restate_sdk_shared_core::{
    AttachInvocationTarget, AwaitResponse, Buffer, CoreVM, Error, Header, HeaderMap,
    HostBufferHandle, HostBufferRegistry, NonEmptyValue, NotificationHandle, PayloadOptions,
    ResponseHead, RetryPolicy, RunExitResult, Segment, Target, TerminalFailure, VMOptions, Value,
    VM,
};
use serde::{Deserialize, Serialize};
use std::borrow::Cow;
use std::cell::RefCell;
use std::convert::Infallible;
use std::io::Write;
use std::mem::MaybeUninit;
use std::rc::Rc;
use std::sync::Arc;
use std::time::Duration;
use tracing::level_filters::LevelFilter;
use tracing::{Level, Subscriber};
use tracing_subscriber::fmt::format::FmtSpan;
use tracing_subscriber::fmt::MakeWriter;
use tracing_subscriber::layer::SubscriberExt;
use tracing_subscriber::{Layer, Registry};

// --------- Init and logging

#[export_name = "init"]
pub unsafe extern "C" fn init(level: u32) {
    std::panic::set_hook(Box::new(|panic| {
        let panic_str = format!("Core panicked: {panic}");
        log(AbiLogLevel::Error, &panic_str)
    }));
    let _ = tracing::subscriber::set_global_default(log_subscriber(level.into()));
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
            4 => AbiLogLevel::Error,
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

pub struct MakeAbiLogWriter;

impl<'a> MakeWriter<'a> for MakeAbiLogWriter {
    type Writer = ConsoleWriter;

    fn make_writer(&'a self) -> Self::Writer {
        ConsoleWriter {
            buffer: vec![],
            level: Level::TRACE,
        }
    }

    fn make_writer_for(&'a self, meta: &tracing::Metadata<'_>) -> Self::Writer {
        let level = *meta.level();
        ConsoleWriter {
            buffer: vec![],
            level,
        }
    }
}

pub struct ConsoleWriter {
    buffer: Vec<u8>,
    level: Level,
}

impl Write for ConsoleWriter {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        self.buffer.write(buf)
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

impl Drop for ConsoleWriter {
    fn drop(&mut self) {
        let mut len = self.buffer.len();
        if len > 0 && self.buffer[len - 1] == b'\n' {
            len -= 1;
        }
        unsafe {
            _log(
                AbiLogLevel::from(self.level) as u32,
                self.buffer.as_ptr() as u32,
                len as u32,
            )
        }
    }
}

fn log_subscriber(level: AbiLogLevel) -> impl Subscriber + Send + Sync + 'static {
    let level = level.into();
    let fmt_layer = tracing_subscriber::fmt::layer()
        .with_ansi(false)
        .without_time()
        .with_thread_names(false)
        .with_thread_ids(false)
        .with_file(false)
        .with_line_number(false)
        .with_target(level == Level::TRACE)
        .with_level(false)
        .with_span_events(if level == Level::TRACE {
            FmtSpan::ENTER
        } else {
            FmtSpan::NONE
        })
        .with_writer(MakeAbiLogWriter)
        .with_filter(LevelFilter::from_level(level));
    Registry::default().with(fmt_layer)
}

// --------- Host buffer ABI
//
// `BufferAbi` is the on-wire (CBOR) shape used to carry user payloads
// across the WASM boundary in both directions. `InMemory` carries inline
// bytes; `Host` carries a (id, offset, len) handle into the Java-side
// `HostBufferRegistry`. `HostMulti` carries an ordered list of
// host-buffer segments — used when the decoder coalesced a body that
// spanned multiple input chunks; the Java side materialises it once on
// the heap and releases each segment.
//
// Refcount semantics across the boundary:
//   - When Java SENDS a `Host` variant to Rust (input DTO), Java has
//     already called `register()` and is transferring the refcount-share.
//     Rust calls `HostBufferHandle::from_parts` (no retain) to materialise
//     the handle.
//   - When Rust RETURNS a `Host` variant to Java (output DTO), the Rust
//     handle is `mem::forget`'d so its `Drop` doesn't fire; Java now owns
//     the refcount-share and must call `release(id)` when done.
//   - `HostMulti` follows the same protocol per segment: every
//     `(id, offset, len)` in the segment list is one transferred
//     refcount-share that the receiving side must eventually release.

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct HostSegmentAbi {
    id: u32,
    offset: u32,
    len: u32,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum BufferAbi {
    InMemory {
        #[serde(with = "serde_bytes")]
        value: Vec<u8>,
    },
    Host {
        id: u32,
        offset: u32,
        len: u32,
    },
    HostMulti {
        segments: Vec<HostSegmentAbi>,
    },
}

impl BufferAbi {
    /// Convert an incoming `BufferAbi` (Java→Rust) into a shared-core
    /// `Buffer`. For `Host`, reconstitutes the handle via `from_parts`
    /// against the per-VM registry — the refcount-share transfers in.
    /// For `HostMulti`, reconstitutes a multi-segment handle via
    /// `from_segments_no_retain` — every segment's refcount-share
    /// transfers in.
    fn into_buffer(self, registry: &Arc<dyn HostBufferRegistry>) -> Buffer {
        match self {
            BufferAbi::InMemory { value } => Buffer::InMemory(Bytes::from(value)),
            BufferAbi::Host { id, offset, len } => Buffer::Host(HostBufferHandle::from_parts(
                registry.clone(),
                id,
                offset,
                len,
            )),
            BufferAbi::HostMulti { segments } => {
                let segs: Vec<Segment> = segments
                    .into_iter()
                    .map(|s| Segment {
                        id: s.id,
                        offset: s.offset,
                        len: s.len,
                    })
                    .collect();
                Buffer::Host(HostBufferHandle::from_segments_no_retain(
                    registry.clone(),
                    segs,
                ))
            }
        }
    }

    /// Convert an outgoing `Buffer` (Rust→Java) into a `BufferAbi`. For
    /// `Host` / `HostMulti`, transfers the refcount-share out via
    /// `mem::forget`.
    fn from_buffer(buf: Buffer) -> Self {
        match buf {
            Buffer::InMemory(b) => BufferAbi::InMemory { value: b.to_vec() },
            Buffer::Host(h) => {
                let segs = h.segments();
                let abi = if segs.len() == 1 {
                    let s = segs[0];
                    BufferAbi::Host {
                        id: s.id,
                        offset: s.offset,
                        len: s.len,
                    }
                } else {
                    let segments = segs
                        .iter()
                        .map(|s| HostSegmentAbi {
                            id: s.id,
                            offset: s.offset,
                            len: s.len,
                        })
                        .collect();
                    BufferAbi::HostMulti { segments }
                };
                std::mem::forget(h);
                abi
            }
        }
    }
}

// --------- Host buffer registry (WASM bridge → Java)

/// Rust-side implementor of [`HostBufferRegistry`] that forwards every
/// call across the WASM boundary to the Java-side registry. The Java
/// side owns one registry per `SharedCoreInstance` and resolves it
/// directly via the imports object, so the WASM call doesn't need to
/// carry a vm/registry identifier.
struct WasmHostBufferRegistry;

impl HostBufferRegistry for WasmHostBufferRegistry {
    fn retain(&self, id: u32) {
        unsafe { _host_buffer_retain(id) }
    }

    fn release(&self, id: u32) {
        unsafe { _host_buffer_release(id) }
    }

    fn read_into(&self, id: u32, offset: u32, len: u32, dst: &mut [u8]) {
        unsafe {
            _host_buffer_read_into(id, offset, len, dst.as_mut_ptr() as u32);
        }
    }

    fn eq(
        &self,
        a_id: u32,
        a_offset: u32,
        a_len: u32,
        b_id: u32,
        b_offset: u32,
        b_len: u32,
    ) -> bool {
        unsafe { _host_buffer_eq(a_id, a_offset, a_len, b_id, b_offset, b_len) != 0 }
    }
}

// --------- VM

pub struct WasmVM {
    vm: CoreVM,
    /// Host buffer registry that forwards across the WASM boundary. The
    /// Rust-side adapter is stateless ([`WasmHostBufferRegistry`]); the
    /// real state lives on the Java side, owned by `SharedCoreInstance`.
    registry: Arc<dyn HostBufferRegistry>,
}

pub struct WasmHeaders(Vec<(String, String)>);

impl HeaderMap for WasmHeaders {
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

#[export_name = "vm_new"]
pub unsafe extern "C" fn _vm_new(ptr: *mut u8, len: usize) -> u64 {
    let input = ptr_to_input(ptr, len);
    let response = vm_new(input);
    output_to_ptr(response)
}

fn vm_new(input: VmNewParameters) -> VmNewReturn {
    let core_vm = match CoreVM::new(WasmHeaders(input.headers), VMOptions::default()) {
        Ok(vm) => vm,
        Err(e) => return VmNewReturn::from_err(e),
    };
    let wasm_vm = WasmVM {
        vm: core_vm,
        registry: Arc::new(WasmHostBufferRegistry) as Arc<dyn HostBufferRegistry>,
    };
    let rc = Rc::new(RefCell::new(wasm_vm));
    let raw = Rc::as_ptr(&rc) as u32;
    // Consume the Rc into a raw pointer — the returned pointer holds the
    // live share. `vm_free` later does the matching `Rc::from_raw`.
    let _ = Rc::into_raw(rc);
    VmNewReturn::Ok { pointer: raw }
}

#[export_name = "vm_get_response_head"]
pub unsafe extern "C" fn _vm_get_response_head(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let response: ResponseHeadReturn = VM::get_response_head(&rc_vm.borrow().vm).into();
    output_to_ptr(response)
}

#[export_name = "vm_notify_input"]
pub unsafe extern "C" fn _vm_notify_input(
    vm_pointer: *const RefCell<WasmVM>,
    id: u32,
    offset: u32,
    len: u32,
) {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let registry = rc_vm.borrow().registry.clone();
    let handle = HostBufferHandle::from_parts(registry, id, offset, len);
    VM::notify_input(&mut rc_vm.borrow_mut().vm, Buffer::Host(handle));
}

#[export_name = "vm_notify_input_closed"]
pub unsafe extern "C" fn _vm_notify_input_closed(vm_pointer: *const RefCell<WasmVM>) {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    VM::notify_input_closed(&mut rc_vm.borrow_mut().vm);
}

#[export_name = "vm_notify_error"]
pub unsafe extern "C" fn _vm_notify_error(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    vm_notify_error(&rc_vm, input);
}

fn vm_notify_error(rc_vm: &Rc<RefCell<WasmVM>>, input: VmNotifyError) {
    let mut error = Error::new(500u16, Cow::Owned(input.message));
    if let Some(st) = input.stacktrace {
        error = error.with_stacktrace(st);
    }
    VM::notify_error(&mut rc_vm.borrow_mut().vm, error, None)
}

#[export_name = "vm_take_output"]
pub unsafe extern "C" fn _vm_take_output(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = match VM::take_output_next(&mut rc_vm.borrow_mut().vm) {
        Some(buffer) => TakeOutputReturn::Buffer {
            buffer: BufferAbi::from_buffer(buffer),
        },
        None => TakeOutputReturn::None,
    };
    output_to_ptr(res)
}

#[export_name = "vm_is_ready_to_execute"]
pub unsafe extern "C" fn _vm_is_ready_to_execute(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_is_ready_to_execute(&rc_vm);
    output_to_ptr(res)
}

fn vm_is_ready_to_execute(rc_vm: &Rc<RefCell<WasmVM>>) -> IsReadyReturn {
    match VM::is_ready_to_execute(&rc_vm.borrow().vm) {
        Ok(ready) => IsReadyReturn::Ok { ready },
        Err(e) => IsReadyReturn::from_err(e),
    }
}

#[export_name = "vm_do_progress"]
pub unsafe extern "C" fn _vm_do_progress(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_do_progress(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_do_progress(rc_vm: &Rc<RefCell<WasmVM>>, input: VmDoProgressParameters) -> DoProgressReturn {
    match VM::do_await(&mut rc_vm.borrow_mut().vm, input.future.into()) {
        Ok(AwaitResponse::AnyCompleted) => DoProgressReturn::AnyCompleted,
        Ok(AwaitResponse::WaitingExternalProgress { .. }) => {
            DoProgressReturn::WaitingExternalProgress
        }
        Ok(AwaitResponse::CancelSignalReceived) => DoProgressReturn::CancelSignalReceived,
        Ok(AwaitResponse::ExecuteRun(handle)) => DoProgressReturn::ExecuteRun {
            handle: handle.into(),
        },
        Err(e) if e.is_suspended_error() => DoProgressReturn::Suspended,
        Err(e) => DoProgressReturn::from_err(e),
    }
}

#[export_name = "vm_take_notification"]
pub unsafe extern "C" fn _vm_take_notification(
    vm_pointer: *const RefCell<WasmVM>,
    handle: u32,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_take_notification(&rc_vm, NotificationHandle::from(handle));
    output_to_ptr(res)
}

fn vm_take_notification(
    rc_vm: &Rc<RefCell<WasmVM>>,
    handle: NotificationHandle,
) -> TakeNotificationReturn {
    match VM::take_notification(&mut rc_vm.borrow_mut().vm, handle) {
        Ok(None) => TakeNotificationReturn::NotReady,
        Ok(Some(v)) => TakeNotificationReturn::Value {
            value: NotificationValue::from(v),
        },
        Err(e) => TakeNotificationReturn::from_err(e),
    }
}

#[export_name = "vm_sys_input"]
pub unsafe extern "C" fn _vm_sys_input(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_sys_input(&rc_vm);
    output_to_ptr(res)
}

fn vm_sys_input(rc_vm: &Rc<RefCell<WasmVM>>) -> SysInputReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_input(&mut vm.vm);
    let state = VM::state(&vm.vm) as u8 as u32;
    match result {
        Ok(input) => SysInputReturn::Ok {
            input: WasmInput {
                invocation_id: input.invocation_id,
                key: input.key,
                headers: input
                    .headers
                    .into_iter()
                    .map(|h| (h.key.into_owned(), h.value.into_owned()))
                    .collect(),
                input: BufferAbi::from_buffer(input.input),
                random_seed: input.random_seed as i64,
            },
            state,
        },
        Err(e) => SysInputReturn::from_err(e, state),
    }
}

#[export_name = "vm_sys_state_get"]
pub unsafe extern "C" fn _vm_sys_state_get(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_state_get(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_state_get(rc_vm: &Rc<RefCell<WasmVM>>, input: VmSysStateGetParameters) -> HandleReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_state_get(&mut vm.vm, input.key, PayloadOptions::default());
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_state_get_keys"]
pub unsafe extern "C" fn _vm_sys_state_get_keys(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_sys_state_get_keys(&rc_vm);
    output_to_ptr(res)
}

fn vm_sys_state_get_keys(rc_vm: &Rc<RefCell<WasmVM>>) -> HandleReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_state_get_keys(&mut vm.vm);
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_state_set"]
pub unsafe extern "C" fn _vm_sys_state_set(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_state_set(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_state_set(rc_vm: &Rc<RefCell<WasmVM>>, input: VmSysStateSetParameters) -> EmptyReturn {
    let registry = rc_vm.borrow().registry.clone();
    let value = input.value.into_buffer(&registry);
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_state_set(&mut vm.vm, input.key, value, PayloadOptions::default());
    EmptyReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_state_clear"]
pub unsafe extern "C" fn _vm_sys_state_clear(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_state_clear(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_state_clear(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysStateClearParameters,
) -> EmptyReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_state_clear(&mut vm.vm, input.key);
    EmptyReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_state_clear_all"]
pub unsafe extern "C" fn _vm_sys_state_clear_all(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_sys_state_clear_all(&rc_vm);
    output_to_ptr(res)
}

fn vm_sys_state_clear_all(rc_vm: &Rc<RefCell<WasmVM>>) -> EmptyReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_state_clear_all(&mut vm.vm);
    EmptyReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_sleep"]
pub unsafe extern "C" fn _vm_sys_sleep(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_sleep(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_sleep(rc_vm: &Rc<RefCell<WasmVM>>, input: VmSysSleepParameters) -> HandleReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_sleep(
        &mut vm.vm,
        input.name,
        Duration::from_millis(input.wake_up_time_since_unix_epoch_millis),
        Some(Duration::from_millis(input.now_since_unix_epoch_millis)),
    );
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_awakeable"]
pub unsafe extern "C" fn _vm_sys_awakeable(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_sys_awakeable(&rc_vm);
    output_to_ptr(res)
}

fn vm_sys_awakeable(rc_vm: &Rc<RefCell<WasmVM>>) -> AwakeableReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_awakeable(&mut vm.vm);
    let state = VM::state(&vm.vm) as u8 as u32;
    match result {
        Ok((awakeable_id, handle)) => AwakeableReturn::Ok {
            id: awakeable_id,
            handle: handle.into(),
            state,
        },
        Err(e) => AwakeableReturn::from_err(e, state),
    }
}

#[export_name = "vm_sys_complete_awakeable"]
pub unsafe extern "C" fn _vm_sys_complete_awakeable(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_complete_awakeable(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_complete_awakeable(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysCompleteAwakeableParameters,
) -> EmptyReturn {
    let registry = rc_vm.borrow().registry.clone();
    let value = input.result.into_non_empty_value(&registry);
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_complete_awakeable(&mut vm.vm, input.id, value, PayloadOptions::default());
    EmptyReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_call"]
pub unsafe extern "C" fn _vm_sys_call(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_call(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_call(rc_vm: &Rc<RefCell<WasmVM>>, input: VmSysCallParameters) -> SysCallReturn {
    let registry = rc_vm.borrow().registry.clone();
    let call_input = input.input.into_buffer(&registry);
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_call(
        &mut vm.vm,
        Target {
            service: input.service,
            handler: input.handler,
            key: input.key,
            idempotency_key: input.idempotency_key,
            scope: None,
            limit_key: None,
            headers: input
                .headers
                .into_iter()
                .map(|(k, v)| Header {
                    key: k.into(),
                    value: v.into(),
                })
                .collect(),
        },
        call_input,
        None,
        PayloadOptions::default(),
    );
    let state = VM::state(&vm.vm) as u8 as u32;
    match result {
        Ok(call_handle) => SysCallReturn::Ok {
            invocation_id_handle: call_handle.invocation_id_notification_handle.into(),
            result_handle: call_handle.call_notification_handle.into(),
            state,
        },
        Err(e) => SysCallReturn::from_err(e, state),
    }
}

#[export_name = "vm_sys_send"]
pub unsafe extern "C" fn _vm_sys_send(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_send(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_send(rc_vm: &Rc<RefCell<WasmVM>>, input: VmSysSendParameters) -> HandleReturn {
    let registry = rc_vm.borrow().registry.clone();
    let send_input = input.input.into_buffer(&registry);
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_send(
        &mut vm.vm,
        Target {
            service: input.service,
            handler: input.handler,
            key: input.key,
            idempotency_key: input.idempotency_key,
            scope: None,
            limit_key: None,
            headers: input
                .headers
                .into_iter()
                .map(|(k, v)| Header {
                    key: k.into(),
                    value: v.into(),
                })
                .collect(),
        },
        send_input,
        input
            .execution_time_since_unix_epoch_millis
            .map(Duration::from_millis),
        None,
        PayloadOptions::default(),
    )
    .map(|s| s.invocation_id_notification_handle);
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_cancel_invocation"]
pub unsafe extern "C" fn _vm_sys_cancel_invocation(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_cancel_invocation(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_cancel_invocation(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysCancelInvocation,
) -> EmptyReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_cancel_invocation(&mut vm.vm, input.invocation_id);
    EmptyReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_attach_invocation"]
pub unsafe extern "C" fn _vm_sys_attach_invocation(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_attach_invocation(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_attach_invocation(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysAttachInvocation,
) -> HandleReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_attach_invocation(
        &mut vm.vm,
        AttachInvocationTarget::InvocationId(input.invocation_id),
    );
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_get_invocation_output"]
pub unsafe extern "C" fn _vm_sys_get_invocation_output(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_get_invocation_output(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_get_invocation_output(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysGetInvocationOutput,
) -> HandleReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_get_invocation_output(
        &mut vm.vm,
        AttachInvocationTarget::InvocationId(input.invocation_id),
    );
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_promise_get"]
pub unsafe extern "C" fn _vm_sys_promise_get(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_promise_get(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_promise_get(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysPromiseGetParameters,
) -> HandleReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_get_promise(&mut vm.vm, input.key);
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_promise_peek"]
pub unsafe extern "C" fn _vm_sys_promise_peek(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_promise_peek(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_promise_peek(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysPromisePeekParameters,
) -> HandleReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_peek_promise(&mut vm.vm, input.key);
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_promise_complete"]
pub unsafe extern "C" fn _vm_sys_promise_complete(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_promise_complete(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_promise_complete(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysPromiseCompleteParameters,
) -> HandleReturn {
    let registry = rc_vm.borrow().registry.clone();
    let value = input.result.into_non_empty_value(&registry);
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_complete_promise(&mut vm.vm, input.id, value, PayloadOptions::default());
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_run"]
pub unsafe extern "C" fn _vm_sys_run(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_run(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_run(rc_vm: &Rc<RefCell<WasmVM>>, input: VmSysRunParameters) -> HandleReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_run(&mut vm.vm, input.name);
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_propose_run_completion"]
pub unsafe extern "C" fn _vm_propose_run_completion(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_propose_run_completion(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_propose_run_completion(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmProposeRunCompletionParameters,
) -> EmptyReturn {
    let registry = rc_vm.borrow().registry.clone();
    let run_exit_result = match input.result {
        RunResult::Success { value } => RunExitResult::Success(value.into_buffer(&registry)),
        RunResult::TerminalFailure {
            code,
            message,
            metadata,
        } => RunExitResult::TerminalFailure(TerminalFailure {
            code: code as u16,
            message,
            metadata: metadata.unwrap_or_default(),
        }),
        RunResult::RetryableFailure {
            code,
            message,
            stacktrace,
        } => {
            let mut error = Error::new(code as u16, message);
            if let Some(st) = stacktrace {
                error = error.with_stacktrace(st);
            }
            RunExitResult::RetryableFailure {
                attempt_duration: Duration::from_millis(input.attempt_duration_millis),
                error,
            }
        }
    };

    let retry_policy = match input.retry_policy {
        None => RetryPolicy::default(),
        Some(rp) => RetryPolicy::Exponential {
            initial_interval: Duration::from_millis(rp.initial_interval_millis),
            factor: rp.factor,
            max_interval: rp.max_interval_millis.map(Duration::from_millis),
            max_attempts: rp.max_attempts,
            max_duration: rp.max_duration_millis.map(Duration::from_millis),
            on_max_attempts: Default::default(),
        },
    };

    let mut vm = rc_vm.borrow_mut();
    let result = VM::propose_run_completion(
        &mut vm.vm,
        input.handle.into(),
        run_exit_result,
        retry_policy,
    );
    EmptyReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

// Java SDK-specific: signals (not in Go SDK yet)

#[export_name = "vm_sys_create_signal_handle"]
pub unsafe extern "C" fn _vm_sys_create_signal_handle(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_create_signal_handle(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_create_signal_handle(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysCreateSignalHandleParameters,
) -> HandleReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::create_signal_handle(&mut vm.vm, input.name);
    HandleReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_complete_signal"]
pub unsafe extern "C" fn _vm_sys_complete_signal(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_complete_signal(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_complete_signal(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysCompleteSignalParameters,
) -> EmptyReturn {
    let registry = rc_vm.borrow().registry.clone();
    let value = input.result.into_non_empty_value(&registry);
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_complete_signal(&mut vm.vm, input.target, input.name, value);
    EmptyReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_write_output"]
pub unsafe extern "C" fn _vm_sys_write_output(
    vm_pointer: *const RefCell<WasmVM>,
    ptr: *mut u8,
    len: usize,
) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_input(ptr, len);
    let res = vm_sys_write_output(&rc_vm, input);
    output_to_ptr(res)
}

fn vm_sys_write_output(
    rc_vm: &Rc<RefCell<WasmVM>>,
    input: VmSysWriteOutputParameters,
) -> EmptyReturn {
    let registry = rc_vm.borrow().registry.clone();
    let value = input.result.into_non_empty_value(&registry);
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_write_output(&mut vm.vm, value, PayloadOptions::default());
    EmptyReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_sys_end"]
pub unsafe extern "C" fn _vm_sys_end(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_sys_end(&rc_vm);
    output_to_ptr(res)
}

fn vm_sys_end(rc_vm: &Rc<RefCell<WasmVM>>) -> EmptyReturn {
    let mut vm = rc_vm.borrow_mut();
    let result = VM::sys_end(&mut vm.vm);
    EmptyReturn::from_result(result, VM::state(&vm.vm) as u8 as u32)
}

#[export_name = "vm_free"]
pub unsafe extern "C" fn _vm_free(vm: *const RefCell<WasmVM>) {
    assert_not_null(vm);
    // We don't need to increment the counter, we're materializing the initial leak!
    let rc = Rc::from_raw(vm);
    match Rc::try_unwrap(rc) {
        Ok(cell) => drop(cell.into_inner()),
        Err(_) => panic!("attempted to free vm while still borrowed"),
    }
}

// --------- Logging infra

fn log(level: AbiLogLevel, message: &str) {
    unsafe {
        let (ptr, len) = string_to_ptr(message);
        _log(level as u32, ptr, len);
    }
}

#[link(wasm_import_module = "env")]
extern "C" {
    #[link_name = "log"]
    fn _log(level: u32, ptr: u32, size: u32);

    /// Increment the refcount of host buffer `id` in the registry.
    #[link_name = "host_buffer_retain"]
    fn _host_buffer_retain(id: u32);

    /// Decrement the refcount of host buffer `id`. When the refcount
    /// reaches zero, Java frees the buffer.
    #[link_name = "host_buffer_release"]
    fn _host_buffer_release(id: u32);

    /// Copy `len` bytes from `registry[id][offset..offset+len]` into WASM
    /// linear memory at `dst_ptr`.
    #[link_name = "host_buffer_read_into"]
    fn _host_buffer_read_into(id: u32, offset: u32, len: u32, dst_ptr: u32);

    /// Byte-equality between two views (offset+len pairs) of registered
    /// buffers. Returns 1 if equal, 0 otherwise.
    #[link_name = "host_buffer_eq"]
    fn _host_buffer_eq(
        a_id: u32,
        a_offset: u32,
        a_len: u32,
        b_id: u32,
        b_offset: u32,
        b_len: u32,
    ) -> u32;
}

// --------- Unsafe memory helpers

#[inline]
pub fn assert_not_null<T>(s: *const T) {
    if s.is_null() {
        panic!("Null pointer exception on input")
    }
}

#[inline]
unsafe fn ptr_to_vec(ptr: *mut u8, len: usize) -> Vec<u8> {
    assert_not_null(ptr);
    Vec::from_raw_parts(ptr, len, len)
}

/// Deserializes CBOR from caller-allocated memory (ownership transferred to us — memory freed).
#[inline]
unsafe fn ptr_to_input<T: serde::de::DeserializeOwned>(ptr: *mut u8, len: usize) -> T {
    let vec = ptr_to_vec(ptr, len);
    ciborium::from_reader(vec.as_slice()).expect("CBOR deserialization of input should not fail")
}

#[inline]
unsafe fn vec_to_ptr(v: Vec<u8>) -> u64 {
    let len = v.len();
    let ptr = Box::into_raw(v.into_boxed_slice()) as *mut u8;
    ((ptr as u64) << 32) | len as u64
}

/// Serializes `t` to CBOR and returns packed `(ptr << 32) | len`.
#[inline]
fn output_to_ptr<T: Serialize>(t: T) -> u64 {
    let mut buf = Vec::new();
    ciborium::into_writer(&t, &mut buf).expect("CBOR serialization of output should not fail");
    unsafe { vec_to_ptr(buf) }
}

unsafe fn vm_ptr_to_rc(vm_pointer: *const RefCell<WasmVM>) -> Rc<RefCell<WasmVM>> {
    assert_not_null(vm_pointer);
    Rc::increment_strong_count(vm_pointer);
    Rc::from_raw(vm_pointer)
}

unsafe fn string_to_ptr(s: &str) -> (u32, u32) {
    (s.as_ptr() as u32, s.len() as u32)
}

#[export_name = "allocate"]
pub unsafe extern "C" fn _allocate(size: usize) -> *mut u8 {
    allocate(size)
}

fn allocate(size: usize) -> *mut u8 {
    let vec: Vec<MaybeUninit<u8>> = vec![MaybeUninit::uninit(); size];
    Box::into_raw(vec.into_boxed_slice()) as *mut u8
}

#[export_name = "deallocate"]
pub unsafe extern "C" fn _deallocate(ptr: *mut u8, size: usize) {
    deallocate(ptr, size);
}

unsafe fn deallocate(ptr: *mut u8, size: usize) {
    let _: Vec<u8> = Vec::from_raw_parts(ptr, 0, size);
}

// --------- Input DTOs (Java → Rust, CBOR maps with camelCase keys)

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmNewParameters {
    headers: Vec<(String, String)>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmNotifyError {
    message: String,
    #[serde(default)]
    stacktrace: Option<String>,
}

#[derive(Deserialize)]
struct VmDoProgressParameters {
    future: UnresolvedFuture,
}

/// Tree-shaped await point. Mirrors `restate_sdk_shared_core::UnresolvedFuture` 1:1
/// and carries the original combinator semantics from the SDK to the core VM, so
/// the runtime sees the right combinator in `AwaitingOnMessage` / `SuspensionMessage`.
#[derive(Deserialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum UnresolvedFuture {
    Single { handle: u32 },
    FirstCompleted { children: Vec<UnresolvedFuture> },
    AllCompleted { children: Vec<UnresolvedFuture> },
    FirstSucceededOrAllFailed { children: Vec<UnresolvedFuture> },
    AllSucceededOrFirstFailed { children: Vec<UnresolvedFuture> },
    Unknown { children: Vec<UnresolvedFuture> },
}

impl From<UnresolvedFuture> for restate_sdk_shared_core::UnresolvedFuture {
    fn from(value: UnresolvedFuture) -> Self {
        use restate_sdk_shared_core::UnresolvedFuture as Core;
        let map_children = |children: Vec<UnresolvedFuture>| -> Vec<Core> {
            children.into_iter().map(Into::into).collect()
        };
        match value {
            UnresolvedFuture::Single { handle } => Core::Single(NotificationHandle::from(handle)),
            UnresolvedFuture::FirstCompleted { children } => {
                Core::FirstCompleted(map_children(children))
            }
            UnresolvedFuture::AllCompleted { children } => {
                Core::AllCompleted(map_children(children))
            }
            UnresolvedFuture::FirstSucceededOrAllFailed { children } => {
                Core::FirstSucceededOrAllFailed(map_children(children))
            }
            UnresolvedFuture::AllSucceededOrFirstFailed { children } => {
                Core::AllSucceededOrFirstFailed(map_children(children))
            }
            UnresolvedFuture::Unknown { children } => Core::Unknown(map_children(children)),
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysStateGetParameters {
    key: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysStateSetParameters {
    key: String,
    value: BufferAbi,
}

#[derive(Deserialize)]
struct VmSysStateClearParameters {
    key: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysSleepParameters {
    name: String,
    wake_up_time_since_unix_epoch_millis: u64,
    now_since_unix_epoch_millis: u64,
}

/// Combined success/failure for awakeable completion (mirrors Go's single endpoint).
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysCompleteAwakeableParameters {
    id: String,
    result: NonEmptyValueParam,
}

/// Combined success/failure union used by awakeable, promise, write_output, signal.
#[derive(Deserialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum NonEmptyValueParam {
    Success {
        value: BufferAbi,
    },
    Failure {
        code: u32,
        message: String,
        #[serde(default)]
        metadata: Option<Vec<(String, String)>>,
    },
}

impl NonEmptyValueParam {
    fn into_non_empty_value(self, registry: &Arc<dyn HostBufferRegistry>) -> NonEmptyValue {
        match self {
            NonEmptyValueParam::Success { value } => {
                NonEmptyValue::Success(value.into_buffer(registry))
            }
            NonEmptyValueParam::Failure {
                code,
                message,
                metadata,
            } => NonEmptyValue::Failure(TerminalFailure {
                code: code as u16,
                message,
                metadata: metadata.unwrap_or_default(),
            }),
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysCallParameters {
    service: String,
    handler: String,
    #[serde(default)]
    key: Option<String>,
    #[serde(default)]
    idempotency_key: Option<String>,
    #[serde(default)]
    headers: Vec<(String, String)>,
    input: BufferAbi,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysSendParameters {
    service: String,
    handler: String,
    #[serde(default)]
    key: Option<String>,
    #[serde(default)]
    idempotency_key: Option<String>,
    #[serde(default)]
    headers: Vec<(String, String)>,
    input: BufferAbi,
    #[serde(default)]
    execution_time_since_unix_epoch_millis: Option<u64>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysCancelInvocation {
    invocation_id: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysAttachInvocation {
    invocation_id: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysGetInvocationOutput {
    invocation_id: String,
}

#[derive(Deserialize)]
struct VmSysPromiseGetParameters {
    key: String,
}

#[derive(Deserialize)]
struct VmSysPromisePeekParameters {
    key: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysPromiseCompleteParameters {
    id: String,
    result: NonEmptyValueParam,
}

#[derive(Deserialize)]
struct VmSysRunParameters {
    name: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmProposeRunCompletionParameters {
    handle: u32,
    result: RunResult,
    attempt_duration_millis: u64,
    #[serde(default)]
    retry_policy: Option<WasmRetryPolicy>,
}

#[derive(Deserialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum RunResult {
    Success {
        value: BufferAbi,
    },
    TerminalFailure {
        code: u32,
        message: String,
        #[serde(default)]
        metadata: Option<Vec<(String, String)>>,
    },
    RetryableFailure {
        code: u32,
        message: String,
        #[serde(default)]
        stacktrace: Option<String>,
    },
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct WasmRetryPolicy {
    initial_interval_millis: u64,
    factor: f32,
    #[serde(default)]
    max_interval_millis: Option<u64>,
    #[serde(default)]
    max_attempts: Option<u32>,
    #[serde(default)]
    max_duration_millis: Option<u64>,
}

#[derive(Deserialize)]
struct VmSysCreateSignalHandleParameters {
    name: String,
}

#[derive(Deserialize)]
struct VmSysCompleteSignalParameters {
    target: String,
    name: String,
    result: NonEmptyValueParam,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysWriteOutputParameters {
    result: NonEmptyValueParam,
}

// --------- Output DTOs (Rust → Java)
// Each return type has a `from_err` helper matching Go's `.into()` from Failure conversions.

#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum VmNewReturn {
    Ok { pointer: u32 },
    Failure { code: u32, message: String },
}
impl VmNewReturn {
    fn from_err(e: Error) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
        }
    }
}

#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum TakeOutputReturn {
    /// One drained buffer from the output queue. May be inline or host.
    Buffer { buffer: BufferAbi },
    /// The output queue is empty.
    None,
}

/// Equivalent to Go's GenericEmptyReturn.
#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum EmptyReturn {
    Ok {
        state: u32,
    },
    Failure {
        code: u32,
        message: String,
        state: u32,
    },
}

/// Equivalent to Go's SimpleSysAsyncResultReturn.
#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum HandleReturn {
    Ok {
        handle: u32,
        state: u32,
    },
    Failure {
        code: u32,
        message: String,
        state: u32,
    },
}

#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum IsReadyReturn {
    Ok { ready: bool },
    Failure { code: u32, message: String },
}
impl IsReadyReturn {
    fn from_err(e: Error) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
        }
    }
}

/// Plain struct (no Ok/Failure wrapper) — mirrors Go's VmGetResponseHeadReturn.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ResponseHeadReturn {
    status_code: u32,
    headers: Vec<(String, String)>,
}

#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum DoProgressReturn {
    AnyCompleted,
    WaitingExternalProgress,
    ExecuteRun { handle: u32 },
    CancelSignalReceived,
    Suspended,
    Failure { code: u32, message: String },
}
impl DoProgressReturn {
    fn from_err(e: Error) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
        }
    }
}

/// Notification value (the payload of a completed handle).
#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum NotificationValue {
    Void,
    Success {
        value: BufferAbi,
    },
    Failure {
        code: u16,
        message: String,
        metadata: Vec<(String, String)>,
    },
    StateKeys {
        keys: Vec<String>,
    },
    InvocationId {
        id: String,
    },
}

impl From<Value> for NotificationValue {
    fn from(v: Value) -> Self {
        match v {
            Value::Void => NotificationValue::Void,
            Value::Success(b) => {
                tracing::trace!(
                    "NotificationValue::Success buffer variant: {}, len={}",
                    match &b {
                        Buffer::InMemory(_) => "InMemory",
                        Buffer::Host(_) => "Host",
                    },
                    b.len()
                );
                NotificationValue::Success {
                    value: BufferAbi::from_buffer(b),
                }
            }
            Value::Failure(TerminalFailure {
                code,
                message,
                metadata,
            }) => NotificationValue::Failure {
                code,
                message,
                metadata,
            },
            Value::StateKeys(keys) => NotificationValue::StateKeys { keys },
            Value::InvocationId(id) => NotificationValue::InvocationId { id },
        }
    }
}

#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum TakeNotificationReturn {
    NotReady,
    Value { value: NotificationValue },
    Failure { code: u32, message: String },
}
impl TakeNotificationReturn {
    fn from_err(e: Error) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
        }
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct WasmInput {
    invocation_id: String,
    key: String,
    headers: Vec<(String, String)>,
    input: BufferAbi,
    random_seed: i64,
}

#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum SysInputReturn {
    Ok {
        input: WasmInput,
        state: u32,
    },
    Failure {
        code: u32,
        message: String,
        state: u32,
    },
}
impl SysInputReturn {
    fn from_err(e: Error, state: u32) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
            state,
        }
    }
}

#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum AwakeableReturn {
    Ok {
        id: String,
        handle: u32,
        state: u32,
    },
    Failure {
        code: u32,
        message: String,
        state: u32,
    },
}
impl AwakeableReturn {
    fn from_err(e: Error, state: u32) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
            state,
        }
    }
}

#[derive(Serialize)]
#[serde(
    tag = "type",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
enum SysCallReturn {
    Ok {
        invocation_id_handle: u32,
        result_handle: u32,
        state: u32,
    },
    Failure {
        code: u32,
        message: String,
        state: u32,
    },
}
impl SysCallReturn {
    fn from_err(e: Error, state: u32) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
            state,
        }
    }
}

impl EmptyReturn {
    fn from_result(value: Result<(), Error>, state: u32) -> Self {
        match value {
            Ok(()) => EmptyReturn::Ok { state },
            Err(e) => EmptyReturn::Failure {
                code: e.code() as u32,
                message: e.to_string(),
                state,
            },
        }
    }
}

impl HandleReturn {
    fn from_result(value: Result<NotificationHandle, Error>, state: u32) -> Self {
        match value {
            Ok(h) => HandleReturn::Ok {
                handle: h.into(),
                state,
            },
            Err(e) => HandleReturn::Failure {
                code: e.code() as u32,
                message: e.to_string(),
                state,
            },
        }
    }
}

impl From<ResponseHead> for ResponseHeadReturn {
    fn from(head: ResponseHead) -> Self {
        ResponseHeadReturn {
            status_code: head.status_code as u32,
            headers: head
                .headers
                .into_iter()
                .map(|h| (h.key.into_owned(), h.value.into_owned()))
                .collect(),
        }
    }
}
