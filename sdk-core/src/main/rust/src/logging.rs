use restate_sdk_shared_core::tracing_pretty::{Pretty, PrettyFields};
use std::ffi::c_void;
use std::io::Write;
use tracing::level_filters::LevelFilter;
use tracing::Level;
use tracing_subscriber::fmt::format::FmtSpan;
use tracing_subscriber::fmt::MakeWriter;
use tracing_subscriber::layer::SubscriberExt;
use tracing_subscriber::util::SubscriberInitExt;
use tracing_subscriber::{Layer, Registry};

/// Log sink installed by the host: `(level, msg_ptr, msg_len)`. `level` is the
/// [`AbiLogLevel`] ordinal; the `(ptr, len)` pair is a UTF-8 view into a Rust-owned, thread-local
/// buffer that is valid **only for the synchronous duration of this call**. The callee must copy
/// the bytes out before returning and must never retain the pointer.
type LogCallback = extern "C" fn(level: u32, msg_ptr: *const u8, msg_len: usize);

pub(crate) fn init_logging(level: AbiLogLevel, log_callback: *const c_void) {
    // SAFETY: the host passed a non-null upcall stub with the `LogCallback` signature. On all
    // supported targets a data pointer and a C function pointer share a representation.
    let callback: LogCallback = unsafe { std::mem::transmute(log_callback) };

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

    Registry::default()
        .with(fmt_layer.with_filter(level_filter))
        .init();
}

struct MakeJavaCallbackWriter(LogCallback);

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

struct JavaCallbackWriter {
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
pub enum AbiLogLevel {
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
