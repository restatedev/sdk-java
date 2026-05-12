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
    AttachInvocationTarget, AwaitResponse, CoreVM, Error, Header, HeaderMap, NonEmptyValue,
    NotificationHandle, PayloadOptions, ResponseHead, RetryPolicy, RunExitResult, TakeOutputResult,
    Target, TerminalFailure, UnresolvedFuture, VMOptions, Value, Version, VM,
};
use serde::{Deserialize, Serialize};
use std::borrow::Cow;
use std::cell::RefCell;
use std::convert::Infallible;
use std::io::Write;
use std::mem::MaybeUninit;
use std::rc::Rc;
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

// --------- VM

pub struct WasmVM {
    vm: CoreVM,
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
    match CoreVM::new(WasmHeaders(input.headers), VMOptions::default()) {
        Ok(vm) => {
            let wasm_vm = WasmVM { vm };
            VmNewReturn::Ok {
                pointer: Rc::into_raw(Rc::new(RefCell::new(wasm_vm))) as u32,
            }
        }
        Err(e) => VmNewReturn::from_err(e),
    }
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
    ptr: *mut u8,
    len: usize,
) {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let input = ptr_to_vec(ptr, len);
    VM::notify_input(&mut rc_vm.borrow_mut().vm, input.into());
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
    if let Some(delay) = input.delay_override_millis {
        error = error.with_next_retry_delay_override(Duration::from_millis(delay));
    }
    VM::notify_error(&mut rc_vm.borrow_mut().vm, error, None)
}

#[export_name = "vm_take_output"]
pub unsafe extern "C" fn _vm_take_output(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res: Vec<u8> = match VM::take_output(&mut rc_vm.borrow_mut().vm) {
        TakeOutputResult::Buffer(b) => b.to_vec(),
        TakeOutputResult::EOF => Vec::default(),
    };
    vec_to_ptr(res)
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

#[export_name = "vm_is_completed"]
pub unsafe extern "C" fn _vm_is_completed(vm_pointer: *const RefCell<WasmVM>, handle: u32) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let result = VM::is_completed(&rc_vm.borrow().vm, NotificationHandle::from(handle));
    result as u64
}

#[export_name = "vm_is_processing"]
pub unsafe extern "C" fn _vm_is_processing(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let result = VM::is_processing(&rc_vm.borrow().vm);
    result as u64
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
    match VM::do_await(
        &mut rc_vm.borrow_mut().vm,
        UnresolvedFuture::FirstCompleted(
            input
                .handles
                .into_iter()
                .map(NotificationHandle::from)
                .map(UnresolvedFuture::Single)
                .collect(),
        ),
    ) {
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
        Err(e) if e.is_suspended_error() => TakeNotificationReturn::Suspended,
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
    let protocol_version = vm.vm.get_response_head().version;
    match VM::sys_input(&mut vm.vm) {
        Ok(input) => SysInputReturn::Ok {
            input: WasmInput {
                invocation_id: input.invocation_id,
                key: input.key,
                headers: input
                    .headers
                    .into_iter()
                    .map(|h| (h.key.into_owned(), h.value.into_owned()))
                    .collect(),
                input: input.input.to_vec(),
                random_seed: input.random_seed as i64,
                should_use_random_seed: protocol_version >= Version::V6,
            },
        },
        Err(e) => SysInputReturn::from_err(e),
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
    VM::sys_state_get(
        &mut rc_vm.borrow_mut().vm,
        input.key,
        PayloadOptions::default(),
    )
    .into()
}

#[export_name = "vm_sys_state_get_keys"]
pub unsafe extern "C" fn _vm_sys_state_get_keys(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_sys_state_get_keys(&rc_vm);
    output_to_ptr(res)
}

fn vm_sys_state_get_keys(rc_vm: &Rc<RefCell<WasmVM>>) -> HandleReturn {
    VM::sys_state_get_keys(&mut rc_vm.borrow_mut().vm).into()
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
    VM::sys_state_set(
        &mut rc_vm.borrow_mut().vm,
        input.key,
        Bytes::from(input.value),
        PayloadOptions::default(),
    )
    .into()
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
    VM::sys_state_clear(&mut rc_vm.borrow_mut().vm, input.key).into()
}

#[export_name = "vm_sys_state_clear_all"]
pub unsafe extern "C" fn _vm_sys_state_clear_all(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_sys_state_clear_all(&rc_vm);
    output_to_ptr(res)
}

fn vm_sys_state_clear_all(rc_vm: &Rc<RefCell<WasmVM>>) -> EmptyReturn {
    VM::sys_state_clear_all(&mut rc_vm.borrow_mut().vm).into()
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
    VM::sys_sleep(
        &mut rc_vm.borrow_mut().vm,
        input.name,
        Duration::from_millis(input.wake_up_time_since_unix_epoch_millis),
        Some(Duration::from_millis(input.now_since_unix_epoch_millis)),
    )
    .into()
}

#[export_name = "vm_sys_awakeable"]
pub unsafe extern "C" fn _vm_sys_awakeable(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_sys_awakeable(&rc_vm);
    output_to_ptr(res)
}

fn vm_sys_awakeable(rc_vm: &Rc<RefCell<WasmVM>>) -> AwakeableReturn {
    match VM::sys_awakeable(&mut rc_vm.borrow_mut().vm) {
        Ok((awakeable_id, handle)) => AwakeableReturn::Ok {
            id: awakeable_id,
            handle: handle.into(),
        },
        Err(e) => AwakeableReturn::from_err(e),
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
    VM::sys_complete_awakeable(
        &mut rc_vm.borrow_mut().vm,
        input.id,
        input.result.into(),
        PayloadOptions::default(),
    )
    .into()
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
    match VM::sys_call(
        &mut rc_vm.borrow_mut().vm,
        Target {
            service: input.service,
            handler: input.handler,
            key: input.key,
            idempotency_key: input.idempotency_key,
            headers: input
                .headers
                .into_iter()
                .map(|(k, v)| Header {
                    key: k.into(),
                    value: v.into(),
                })
                .collect(),
        },
        Bytes::from(input.input),
        None,
        PayloadOptions::default(),
    ) {
        Ok(call_handle) => SysCallReturn::Ok {
            invocation_id_handle: call_handle.invocation_id_notification_handle.into(),
            result_handle: call_handle.call_notification_handle.into(),
        },
        Err(e) => SysCallReturn::from_err(e),
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
    VM::sys_send(
        &mut rc_vm.borrow_mut().vm,
        Target {
            service: input.service,
            handler: input.handler,
            key: input.key,
            idempotency_key: input.idempotency_key,
            headers: input
                .headers
                .into_iter()
                .map(|(k, v)| Header {
                    key: k.into(),
                    value: v.into(),
                })
                .collect(),
        },
        Bytes::from(input.input),
        input
            .execution_time_since_unix_epoch_millis
            .map(Duration::from_millis),
        None,
        PayloadOptions::default(),
    )
    .map(|s| s.invocation_id_notification_handle)
    .into()
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
    VM::sys_cancel_invocation(&mut rc_vm.borrow_mut().vm, input.invocation_id).into()
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
    VM::sys_attach_invocation(
        &mut rc_vm.borrow_mut().vm,
        AttachInvocationTarget::InvocationId(input.invocation_id),
    )
    .into()
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
    VM::sys_get_invocation_output(
        &mut rc_vm.borrow_mut().vm,
        AttachInvocationTarget::InvocationId(input.invocation_id),
    )
    .into()
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
    VM::sys_get_promise(&mut rc_vm.borrow_mut().vm, input.key).into()
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
    VM::sys_peek_promise(&mut rc_vm.borrow_mut().vm, input.key).into()
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
    VM::sys_complete_promise(
        &mut rc_vm.borrow_mut().vm,
        input.id,
        input.result.into(),
        PayloadOptions::default(),
    )
    .into()
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
    VM::sys_run(&mut rc_vm.borrow_mut().vm, input.name).into()
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
    let run_exit_result = match input.result {
        RunResult::Success { value } => RunExitResult::Success(Bytes::from(value)),
        RunResult::TerminalFailure { code, message, metadata } => {
            RunExitResult::TerminalFailure(TerminalFailure {
                code: code as u16,
                message,
                metadata: metadata.unwrap_or_default(),
            })
        }
        RunResult::RetryableFailure { code, message, stacktrace } => {
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
        },
    };

    VM::propose_run_completion(
        &mut rc_vm.borrow_mut().vm,
        input.handle.into(),
        run_exit_result,
        retry_policy,
    )
    .into()
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
    VM::create_signal_handle(&mut rc_vm.borrow_mut().vm, input.name).into()
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
    VM::sys_complete_signal(
        &mut rc_vm.borrow_mut().vm,
        input.target,
        input.name,
        input.result.into(),
    )
    .into()
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
    VM::sys_write_output(
        &mut rc_vm.borrow_mut().vm,
        input.result.into(),
        PayloadOptions::default(),
    )
    .into()
}

#[export_name = "vm_sys_end"]
pub unsafe extern "C" fn _vm_sys_end(vm_pointer: *const RefCell<WasmVM>) -> u64 {
    let rc_vm = vm_ptr_to_rc(vm_pointer);
    let res = vm_sys_end(&rc_vm);
    output_to_ptr(res)
}

fn vm_sys_end(rc_vm: &Rc<RefCell<WasmVM>>) -> EmptyReturn {
    VM::sys_end(&mut rc_vm.borrow_mut().vm).into()
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
    #[serde(default)]
    delay_override_millis: Option<u64>,
}

/// Flat list of handles — Rust converts to FirstCompleted(handles.map(Single)) internally.
#[derive(Deserialize)]
struct VmDoProgressParameters {
    handles: Vec<u32>,
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
    #[serde(with = "serde_bytes")]
    value: Vec<u8>,
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
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
enum NonEmptyValueParam {
    Success {
        #[serde(with = "serde_bytes")]
        value: Vec<u8>,
    },
    Failure {
        code: u32,
        message: String,
        #[serde(default)]
        metadata: Option<Vec<(String, String)>>,
    },
}

impl From<NonEmptyValueParam> for NonEmptyValue {
    fn from(p: NonEmptyValueParam) -> Self {
        match p {
            NonEmptyValueParam::Success { value } => NonEmptyValue::Success(Bytes::from(value)),
            NonEmptyValueParam::Failure { code, message, metadata } => {
                NonEmptyValue::Failure(TerminalFailure {
                    code: code as u16,
                    message,
                    metadata: metadata.unwrap_or_default(),
                })
            }
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysCallParameters {
    service: String,
    handler: String,
    key: Option<String>,
    idempotency_key: Option<String>,
    headers: Vec<(String, String)>,
    #[serde(with = "serde_bytes")]
    input: Vec<u8>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VmSysSendParameters {
    service: String,
    handler: String,
    key: Option<String>,
    idempotency_key: Option<String>,
    headers: Vec<(String, String)>,
    #[serde(with = "serde_bytes")]
    input: Vec<u8>,
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
    retry_policy: Option<WasmRetryPolicy>,
}

#[derive(Deserialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
enum RunResult {
    Success {
        #[serde(with = "serde_bytes")]
        value: Vec<u8>,
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
    max_interval_millis: Option<u64>,
    max_attempts: Option<u32>,
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
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
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

/// Equivalent to Go's GenericEmptyReturn.
#[derive(Serialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
enum EmptyReturn {
    Ok,
    Failure { code: u32, message: String },
}

/// Equivalent to Go's SimpleSysAsyncResultReturn.
#[derive(Serialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
enum HandleReturn {
    Ok { handle: u32 },
    Failure { code: u32, message: String },
}

#[derive(Serialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
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
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
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
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
enum NotificationValue {
    Void,
    Success {
        #[serde(with = "serde_bytes")]
        value: Vec<u8>,
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
            Value::Success(b) => NotificationValue::Success { value: b.to_vec() },
            Value::Failure(TerminalFailure { code, message, metadata }) => {
                NotificationValue::Failure { code, message, metadata }
            }
            Value::StateKeys(keys) => NotificationValue::StateKeys { keys },
            Value::InvocationId(id) => NotificationValue::InvocationId { id },
        }
    }
}

#[derive(Serialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
enum TakeNotificationReturn {
    NotReady,
    Value { value: NotificationValue },
    Suspended,
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
    #[serde(with = "serde_bytes")]
    input: Vec<u8>,
    random_seed: i64,
    should_use_random_seed: bool,
}

#[derive(Serialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
enum SysInputReturn {
    Ok { input: WasmInput },
    Failure { code: u32, message: String },
}
impl SysInputReturn {
    fn from_err(e: Error) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
        }
    }
}

#[derive(Serialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
enum AwakeableReturn {
    Ok { id: String, handle: u32 },
    Failure { code: u32, message: String },
}
impl AwakeableReturn {
    fn from_err(e: Error) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
        }
    }
}

#[derive(Serialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
enum SysCallReturn {
    Ok {
        invocation_id_handle: u32,
        result_handle: u32,
    },
    Failure {
        code: u32,
        message: String,
    },
}
impl SysCallReturn {
    fn from_err(e: Error) -> Self {
        Self::Failure {
            code: e.code() as u32,
            message: e.to_string(),
        }
    }
}

// --------- From impls (enable `.into()` in inner functions, like Go's `into()` on pb types)

impl From<Result<(), Error>> for EmptyReturn {
    fn from(value: Result<(), Error>) -> Self {
        match value {
            Ok(()) => EmptyReturn::Ok,
            Err(e) => EmptyReturn::Failure {
                code: e.code() as u32,
                message: e.to_string(),
            },
        }
    }
}

impl From<Result<NotificationHandle, Error>> for HandleReturn {
    fn from(value: Result<NotificationHandle, Error>) -> Self {
        match value {
            Ok(h) => HandleReturn::Ok { handle: h.into() },
            Err(e) => HandleReturn::Failure {
                code: e.code() as u32,
                message: e.to_string(),
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
