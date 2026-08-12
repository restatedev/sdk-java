// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine.ffm;

import dev.restate.sdk.core.RelayTunnel;
import dev.restate.sdk.core.RelayTunnelException;
import dev.restate.sdk.core.statemachine.ffm.generated.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Panama/FFM (JDK 23+) implementation of {@link RelayTunnel.Native}, driving the {@code
 * relay_tunnel_*} C ABI in the native {@code librestate_sdk_core} library (see {@code
 * sdk-core/src/main/rust/src/relay_tunnel.rs}). The boundary is control-plane only; every forwarded
 * request rides a loopback socket inside the engine's own tokio runtime.
 *
 * <p>Unlike {@link FfmStateMachine}, the static initializer only loads the native library — it does
 * <b>not</b> call {@code SharedCoreNative.init(...)}. That installs the process-global tracing
 * subscriber and must run exactly once; {@link FfmStateMachine} owns that call (a tunnel can start
 * before any state machine exists, so calling it here would risk a double-init panic when the first
 * invocation later initializes {@code FfmStateMachine}).
 *
 * <p>Resolved reflectively by {@link RelayTunnel}, so it is never referenced by the Java-17 base
 * source set. Control calls ({@link #statusJson()} / {@link #stop()}) are serialized on the
 * instance so a concurrent {@code stop} can never free the handle out from under a {@code status}.
 */
public final class FfmRelayTunnel implements RelayTunnel.Native {

  static {
    // Load the native library BEFORE SharedCoreNative is class-initialized so its
    // SymbolLookup.loaderLookup() resolves the relay_tunnel_* symbols. (No init() here — see the
    // class doc.)
    NativeLibraryLoader.ensureLoaded();
  }

  private final MemorySegment handle;
  private boolean stopped = false;

  private FfmRelayTunnel(MemorySegment handle) {
    this.handle = handle;
  }

  /** Parse the JSON config natively and start the engine, or throw on failure. */
  public static FfmRelayTunnel start(String configJson) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = RelayTunnelStartResult.allocate(arena);
      SharedCoreNative.relay_tunnel_start(FfmEncoding.foreignUtf8(arena, configJson), out);
      if (RelayTunnelStartResult.tag(out) == SharedCoreNative.RelayTunnelStartResult_Err()) {
        String err =
            FfmEncoding.takeSliceString(
                RelayTunnelStartResult_Err_Body.error(RelayTunnelStartResult.err(out)));
        throw new RelayTunnelException(err.isEmpty() ? "relay tunnel start failed" : err);
      }
      MemorySegment handle = RelayTunnelStartResult_Ok_Body.handle(RelayTunnelStartResult.ok(out));
      return new FfmRelayTunnel(handle);
    }
  }

  @Override
  public synchronized String statusJson() {
    if (stopped) {
      return "{\"running\":false,\"last_error\":null}";
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = FfmEncoding.allocateSliceStruct(arena);
      SharedCoreNative.relay_tunnel_status(handle, out);
      return FfmEncoding.takeSliceString(out);
    }
  }

  @Override
  public synchronized void stop() {
    if (stopped) {
      return;
    }
    // Set before the call: relay_tunnel_stop frees the handle (Box::from_raw), so it must never run
    // twice, even if the native call itself throws.
    stopped = true;
    SharedCoreNative.relay_tunnel_stop(handle);
  }
}
