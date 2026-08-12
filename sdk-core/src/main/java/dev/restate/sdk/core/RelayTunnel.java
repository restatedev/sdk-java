// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

/**
 * Transport-neutral control handle for an embedded <b>relay tunnel</b>: the SDK registers as a
 * relay <i>receiver</i> and the native engine bridges each forwarded request to a local HTTP/2
 * server over a loopback socket (see {@code restate_sdk_shared_core::relay}). The engine owns its
 * own tokio runtime; this class only drives the control plane — {@link #start(Config)}, {@link
 * #status()}, {@link #stop()}.
 *
 * <p>This class carries <b>no transport dependency</b>: it just needs the port of a local h2c
 * server to bridge to. {@code sdk-http-vertx}'s {@code RestateRelayServer} wires a Vert.x server to
 * it.
 *
 * <p><b>Requires JDK 23+</b> (the FFM native path); there is no pure-Java fallback for the tunnel.
 * The native implementation is resolved reflectively (mirroring {@code StateMachineFactory}) so
 * this base-level class never references the java23 FFM overlay directly.
 */
public final class RelayTunnel implements AutoCloseable {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int FFM_MIN_JAVA_FEATURE = 23;

  /**
   * The native delegate, implemented by the java23 FFM class and resolved reflectively by {@link
   * #start(Config)}. Public only because the implementation lives in another package (the FFM
   * overlay); not intended for direct use.
   */
  public interface Native {
    /** The engine status as JSON: {@code {"running": bool, "last_error": string|null}}. */
    String statusJson();

    /** Signal a graceful shutdown, join the runtime, and free the native handle. Idempotent. */
    void stop();
  }

  private final Native delegate;

  private RelayTunnel(Native delegate) {
    this.delegate = delegate;
  }

  /** Start a tunnel from the given configuration. Returns once the engine's runtime is spawned. */
  public static RelayTunnel start(Config config) {
    return new RelayTunnel(loadNative(config.toJson()));
  }

  /** Current engine status. */
  public Status status() {
    String json = delegate.statusJson();
    try {
      JsonNode n = MAPPER.readTree(json);
      boolean running = n.path("running").asBoolean(false);
      String lastError = n.hasNonNull("last_error") ? n.get("last_error").asText() : null;
      return new Status(running, lastError);
    } catch (Exception e) {
      throw new RelayTunnelException("could not parse tunnel status: " + json, e);
    }
  }

  /** Signal a graceful shutdown and join the engine's runtime. Idempotent. */
  public void stop() {
    delegate.stop();
  }

  @Override
  public void close() {
    stop();
  }

  // -------------------------------------------------------------------------
  // Reflective FFM loader (mirrors StateMachineFactory.Loader; no legacy fallback — the tunnel
  // hard-requires the native FFM path).
  // -------------------------------------------------------------------------

  private static Native loadNative(String configJson) {
    if (Runtime.version().feature() < FFM_MIN_JAVA_FEATURE) {
      throw new RelayTunnelException(
          "The relay tunnel requires Java "
              + FFM_MIN_JAVA_FEATURE
              + "+ (Foreign Function & Memory API).");
    }
    try {
      // Load the native library first; a linkage failure here means this platform isn't supported.
      Class.forName("dev.restate.sdk.core.statemachine.ffm.NativeLibraryLoader")
          .getMethod("ensureLoaded")
          .invoke(null);
      Method start =
          Class.forName("dev.restate.sdk.core.statemachine.ffm.FfmRelayTunnel")
              .getMethod("start", String.class);
      return (Native) start.invoke(null, configJson);
    } catch (InvocationTargetException e) {
      // Unwrap so a start failure (bad config, engine error) propagates as-is.
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RuntimeException re) throw re;
      if (cause instanceof Error er) throw er;
      throw new RelayTunnelException("relay tunnel start failed", cause);
    } catch (ReflectiveOperationException e) {
      throw new RelayTunnelException(
          "native relay tunnel unavailable on this platform ("
              + System.getProperty("os.name")
              + " "
              + System.getProperty("os.arch")
              + ")",
          e);
    }
  }

  // -------------------------------------------------------------------------
  // Config & status
  // -------------------------------------------------------------------------

  /**
   * Tunnel configuration. {@code localPort} is the local h2c server to bridge forwarded requests
   * to; a transport wrapper (e.g. {@code RestateRelayServer}) fills it after binding via {@link
   * #withLocalPort(int)}. {@code connections} (R4 multi-homing) and {@code instanceId} (R5
   * affinity) are optional. The field names below are the JSON contract with the native engine.
   */
  public record Config(
      String relayAddress,
      String env,
      String tunnel,
      String apiKey,
      int localPort,
      @Nullable Integer connections,
      @Nullable String instanceId) {

    /** The relay coordinates, with {@code localPort} unset (0) and no optional knobs. */
    public Config(String relayAddress, String env, String tunnel, String apiKey) {
      this(relayAddress, env, tunnel, apiKey, 0, null, null);
    }

    public Config withLocalPort(int localPort) {
      return new Config(relayAddress, env, tunnel, apiKey, localPort, connections, instanceId);
    }

    public Config withConnections(int connections) {
      return new Config(relayAddress, env, tunnel, apiKey, localPort, connections, instanceId);
    }

    public Config withInstanceId(String instanceId) {
      return new Config(relayAddress, env, tunnel, apiKey, localPort, connections, instanceId);
    }

    /** Serialise to the JSON wire shape the native engine's {@code Config} deserialises. */
    String toJson() {
      ObjectNode n = MAPPER.createObjectNode();
      n.put("relay_addr", relayAddress);
      n.put("env", env);
      n.put("tunnel", tunnel);
      n.put("api_key", apiKey);
      n.put("local_port", localPort);
      if (connections != null) {
        n.put("connections", connections);
      }
      if (instanceId != null) {
        n.put("instance_id", instanceId);
      }
      return n.toString();
    }
  }

  /** A snapshot of the tunnel's runtime status. */
  public record Status(boolean running, @Nullable String lastError) {}
}
