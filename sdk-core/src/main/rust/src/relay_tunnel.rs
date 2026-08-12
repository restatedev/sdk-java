//! C-ABI wrapper around the embedded relay-tunnel engine
//! (`restate_sdk_shared_core::relay`), compiled into the same `librestate_sdk_core`
//! cdylib as the `vm_*` surface and called from Java via Panama FFM.
//!
//! The boundary is control-plane only — start / status / stop. Every forwarded
//! request rides a loopback socket inside the engine's own tokio runtime, never
//! this FFI boundary. Conventions mirror the `vm_*` surface:
//!
//!   - The running tunnel is an opaque `*mut RelayTunnelHandle` from
//!     `relay_tunnel_start`, released by `relay_tunnel_stop` (a plain `Box`).
//!   - Config crosses **in** as a borrowed [`ForeignSlice`] of UTF-8 JSON (Java
//!     owns it); `relay_tunnel_start` writes a `#[repr(C, u32)]` tagged-union
//!     `RelayTunnelStartResult` (`Ok { handle }` / `Err { error }`).
//!   - `relay_tunnel_status` returns Rust-owned JSON as a [`Slice`] the caller
//!     frees with the existing `free_buffer` (no bespoke free fn needed).
//!
//! Java serializes access per handle (one thread at a time, no reentrancy), so
//! no internal locking is required here.

use crate::{assert_not_null, write_out, ForeignSlice, Slice};
use restate_sdk_shared_core::relay::{Config, Engine, Handle};

/// Opaque handle to a running tunnel. cbindgen emits it as an opaque struct
/// (like `VmHandle`) since it is only ever referenced behind a pointer.
pub struct RelayTunnelHandle {
    handle: Handle,
}

/// Result of `relay_tunnel_start`: the opaque handle on success, or an owned
/// UTF-8 error message (`Slice`, freed via `free_buffer`) on failure.
#[repr(C, u32)]
pub enum RelayTunnelStartResult {
    Ok { handle: *mut RelayTunnelHandle },
    Err { error: Slice },
}

/// Parse the JSON config and start the engine. `config_json` is borrowed Java
/// memory (read in-call, never freed here). On success the caller owns the
/// returned handle and must release it with `relay_tunnel_stop`.
#[export_name = "relay_tunnel_start"]
pub unsafe extern "C" fn _relay_tunnel_start(
    config_json: ForeignSlice,
    out: *mut RelayTunnelStartResult,
) {
    let result = match relay_tunnel_start(config_json.as_slice()) {
        Ok(handle) => RelayTunnelStartResult::Ok {
            handle: Box::into_raw(Box::new(RelayTunnelHandle { handle })),
        },
        Err(message) => RelayTunnelStartResult::Err {
            error: Slice::from_string(message),
        },
    };
    write_out(out, result);
}

#[inline]
fn relay_tunnel_start(config_json: &[u8]) -> Result<Handle, String> {
    let config: Config = serde_json::from_slice(config_json)
        .map_err(|e| format!("invalid tunnel config JSON: {e}"))?;
    Engine::start(config).map_err(|e| e.to_string())
}

/// Read the tunnel status as owned UTF-8 JSON (`{"running":bool,"last_error":…}`).
/// The returned `Slice` is freed by the caller via `free_buffer`.
#[export_name = "relay_tunnel_status"]
pub unsafe extern "C" fn _relay_tunnel_status(handle: *const RelayTunnelHandle, out: *mut Slice) {
    assert_not_null(handle);
    let json = (*handle).handle.status_json();
    write_out(out, Slice::from_string(json));
}

/// Signal a graceful shutdown, join the engine's runtime, and free the handle.
/// After this call the handle pointer is dangling and must not be reused.
#[export_name = "relay_tunnel_stop"]
pub unsafe extern "C" fn _relay_tunnel_stop(handle: *mut RelayTunnelHandle) {
    assert_not_null(handle);
    let mut boxed = Box::from_raw(handle);
    boxed.handle.stop();
    drop(boxed);
}
