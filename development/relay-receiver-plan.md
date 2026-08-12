# Embedded native relay receiver — implementation plan

**Status:** **landed** (Phases 0–3 of the Java side). **Date:** 2026-08-11.
**Scope:** the Rust engine now lives in **`restatedev/sdk-shared-core`** (branch
`relay`, behind the `tunnel` feature — see that repo's
`docs/relay-receiver-in-shared-core.md`), and this repo holds the Java binding +
Vert.x wiring. A sibling TS/napi track reuses the same engine.

> **Implemented.** The `sdk-core` native cdylib crate
> (`sdk-core/src/main/rust`) now depends on `restate-sdk-shared-core` at
> `{ git = restatedev/sdk-shared-core, branch = "relay", features = ["tunnel"] }`
> and adds the `relay_tunnel_*` C ABI (`src/relay_tunnel.rs`) over the engine's
> `relay::{Config, Engine, Handle}` API. On the Java side: `RelayTunnel` +
> `RelayTunnelException` (base `dev.restate.sdk.core`, transport-neutral,
> reflective FFM load), `FfmRelayTunnel` (java23 FFM overlay), and
> `RestateRelayServer.listen(endpoint, config)` (sdk-http-vertx). Verified:
> the cdylib builds + cbindgen emits the symbols; jextract binds them; the base,
> java23, and sdk-http-vertx sources all compile. Remaining is operator-time —
> a live run against a real relay (Phase 0's round-trip is proven in shared-core's
> `tests/relay_loopback.rs`).

## Goal

Let a Restate Java service register with the **relay** (an M:N HTTP/2
reverse-tunnel rendezvous broker) with **no service code changes** and **no
separate ops artifact**, by embedding the Rust `relay-receiver` runtime as
native code inside the JVM. The Rust core does all relay-facing work on its own
tokio runtime and bridges each forwarded request to the SDK's **own** HTTP/2
server over a loopback socket. The FFI boundary is a control plane only
(`start`/`stop`/`status`) — no request data crosses it.

Background on the relay protocol/roles lives in the relay repo
(`design.md`, `docs/reconnection-contract.md`, `docs/native-js-receiver.md`).
The service SDK plays the **receiver** role: it dials *out* to the relay `:8080`,
the relay drives HTTP/2 as the *client* back over that socket (the "role-flip"),
and the SDK serves the forwarded requests.

## Architecture

```
 relay :8080                       ONE JVM process
┌──────────┐   h2 (role-flip)   ┌───────────────────────────────────────────────┐
│  relay   │◄──────────────────►│  tunnel code inside librestate_sdk_core         │
└──────────┘                    │  (relay-receiver + LoopbackHandler)             │
                                │      owns its OWN tokio runtime + threads       │
                                │                     │ h2c dial per stream       │
                                │                     ▼                           │
                                │   Vert.x HttpServer on 127.0.0.1:<ephemeral>    │
                                │   → HttpEndpointRequestHandler (UNCHANGED)       │
                                └───────────────────────────────────────────────┘
    FFM = start / stop / status downcalls only. Data rides the loopback socket.
```

**Invariant:** each runtime stays sovereign on its own side of a local socket —
the Rust core never calls into the JVM per request; the JVM never touches Rust
threads. The loopback connection is indistinguishable from a normal Restate→SDK
h2c bidi connection, so SDK dispatch is untouched.

**The contract (sdk-core ↔ transport):** *"give me a localhost h2c bidi
server's port and I'll drive it."* That is the entire coupling — `sdk-core`
knows nothing about Vert.x.

## Key decision: one native lib, not two (measured)

Fold the tunnel into the **existing** `librestate_sdk_core` cdylib — it exports
`vm_*` (today) plus new `relay_tunnel_*` symbols. Justified by a real build of
the tunnel cdylib (relay workspace `lto=fat`, stripped, `ring` crypto backend,
**TLS confirmed linked** via `nm`):

| | size |
|---|---|
| VM lib today (baseline, sans-IO) | 1.60 MB |
| tunnel code, stripped | ~1.5–1.8 MB |
| tunnel code, gzipped (jar-stored) | ~0.7–0.85 MB |

At ~1.5–2 MB the tunnel is an order of magnitude under the ~15–20 MB that would
justify a separate lib. Merging buys dedup (one copy of the
`bytes`/`http`/`tracing`/`serde` overlap) + one artifact, one loader, one
jextract header. Cost to the Lambda/serverless path is ~0.7 MB gzipped of dead
bytes it never loads — tokio only spawns on `relay_tunnel_start`, which those
deployments never call. No `NativeLibraryLoader` basename change needed.

> If a size-sensitive Lambda build ever objects, that's when to revisit the
> split — not preemptively.

## Component layout

| Where | What | Vert.x dep? |
|---|---|---|
| **sdk-shared-core** `src/relay/{protocol,bridge,receiver}` (`tunnel` feature) | forked receiver runtime + protocol + bridge: dial-out, role-flip, `/whoami`, liveness, redial, multi-home (R4/R5) | — |
| **sdk-shared-core** `src/relay/loopback.rs` (`tunnel` feature) | binding-agnostic **engine**: `LoopbackHandler` (bridges each `Invocation` → h2c dial to `127.0.0.1:localPort`, **tail-only `:path`**, in-crate bridge pump) + `Engine::start` → `Handle::{status,stop}` + serde JSON `Config` | — |
| **this repo** `sdk-core/src/main/rust` (`src/relay_tunnel.rs`, extends the existing cdylib) | `relay_tunnel_*` C ABI over the engine → same `librestate_sdk_core`; depends on the shared-core `relay` branch w/ `tunnel` | — |
| **this repo** `sdk-core` (java) | jextract bindings + `FfmRelayTunnel` (java23 source set) + **`RelayTunnel`** / `RelayTunnelException` neutral control classes | **no** |
| **this repo** `sdk-http-vertx` (java) | **`RestateRelayServer.listen(endpoint, config)`** — the only Vert.x-coupled piece | yes (already) |

The same shared-core engine is later wrapped by a **napi** shim for the TS SDK
— the engine (~80% of the work) is shared; only the binding (C-ABI vs napi) and
the SDK-side wrapper (Vert.x vs node-http2) differ.

## FFM surface (control plane only)

C ABI (via `cbindgen`, added to the existing header). It **reuses the existing
`vm_*` conventions** — `ForeignSlice` in, `Slice` out (freed via `free_buffer`),
a `#[repr(C, u32)]` tagged-union result — rather than a bespoke
`const char*`/`free_string` pair:

```c
typedef struct RelayTunnelHandle RelayTunnelHandle;                // opaque (like VmHandle)

// Ok { handle } | Err { error: Slice } — mirrors VmNewResult.
typedef struct RelayTunnelStartResult { RelayTunnelStartResult_Tag tag; union { … }; } RelayTunnelStartResult;

void relay_tunnel_start(struct ForeignSlice config_json, struct RelayTunnelStartResult *out);
void relay_tunnel_status(const struct RelayTunnelHandle *handle, struct Slice *out);  // owned JSON; free_buffer
void relay_tunnel_stop(struct RelayTunnelHandle *handle);           // signal shutdown, join runtime, free
```

Config passed as a borrowed UTF-8 JSON `ForeignSlice` (version-tolerant; no
struct-layout coupling), built in a confined `Arena`. **Zero upcalls.**
`status()` reads an `Arc<Status>` shared with the runtime task and returns owned
JSON (`{"running":bool,"last_error":…}`) the caller copies out + frees via the
existing `free_buffer`. `FfmRelayTunnel` mirrors `FfmStateMachine`'s static-init
(load the lib before the generated bindings class is initialized, per the
`loaderLookup()` ordering rule) — but deliberately does **not** call
`SharedCoreNative.init(...)`: that once-only tracing-subscriber install is
`FfmStateMachine`'s job, and a tunnel may start before any state machine exists.

## sdk-core: neutral control class

```java
package dev.restate.sdk.core.relay;   // transport-neutral, no Vert.x

public final class RelayTunnel implements AutoCloseable {
  public record Config(
      List<String> relay,   // "host:port" per node, or one name w/ many A records
      String env, String tunnel, String apiKey,
      int localPort,        // the h2c server to dial; filled by the transport wrapper
      boolean tls,          // to the relay (false = h2c)
      Integer connections,  // R4; null = all resolved nodes
      String instanceId,    // R5; generated by the caller
      H2Tuning h2) { /* toJson() */ }

  public record Status(int connections, String lastError) {}

  public static RelayTunnel start(Config cfg);   // FfmRelayTunnel under the hood
  public Status status();
  public void stop();
  @Override public void close() { stop(); }
}
```

## sdk-http-vertx: the Vert.x wiring

```java
public final class RestateRelayServer {
  public static RelayTunnel listen(Endpoint endpoint, RelayConfig relayConfig) {
    Vertx vertx = Vertx.vertx();
    HttpServer server = RestateHttpServer.fromEndpoint(vertx, endpoint);   // existing, unchanged
    int port = server.listen(0, "127.0.0.1")
                     .toCompletionStage().toCompletableFuture().join().actualPort();
    RelayTunnel tunnel = RelayTunnel.start(relayConfig.toCoreConfig(port));
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      tunnel.stop(); server.close(); vertx.close();
    }));
    return tunnel;
  }
}
```

The service author swaps `RestateHttpServer.listen(endpoint)` →
`RestateRelayServer.listen(endpoint, relayConfig)`. Nothing else changes:
`HttpEndpointRequestHandler` reads `uri.getPath()`, which is the tail (the Rust
`LoopbackHandler` sends a tail-only `:path`), so dispatch + request-identity are
intact.

## Packaging / CI / Lambda

- Extend `sdk-core/src/main/rust` so the one cdylib exports both symbol sets;
  extend the jextract task to bind `relay_tunnel_*`. `NativeLibraryLoader`
  unchanged (same lib).
- The `native.yaml` CI matrix already cross-compiles `librestate_sdk_core` for
  all release classifiers — the tunnel rides along; the VM lib grows to
  ~3.2–3.4 MB/platform.
- **Lambda:** `sdk-lambda` never calls `RelayTunnel` → tokio never spawns; the
  only cost is ~0.7 MB gzipped/platform of dead bytes. `sdk-core` gains **no
  Vert.x dependency** (the wiring lives in `sdk-http-vertx`).

## Phased milestones (with acceptance criteria)

- **Phase 0 — spike / retire the make-or-break risks.** Add a minimal
  `relay_tunnel_start`/`stop` + `LoopbackHandler` to `sdk-core/src/main/rust`;
  extend jextract; a Java 23 harness that binds `RestateHttpServer.fromEndpoint`
  on `127.0.0.1:0` and calls `start`. **Accept:** against `cargo run --bin relay
  examples/relay.toml` (whoami mode), a sender request on `:9080` round-trips
  relay → Rust → loopback → Vert.x → back. Proves (a) FFM start/stop +
  encapsulated tokio runtime, (b) **prior-knowledge h2c bidi into the Vert.x
  server** (risk #1).
- **Phase 1 — real dispatch.** Bind a real `Endpoint`; run a signed discovery +
  invocation end to end. **Accept:** signed invocation → 200, request-identity
  intact (tail-only path).
- **Phase 2 — lifecycle.** `RelayTunnel` + `RestateRelayServer`, `status()`,
  shutdown hook, auth failure surfaced via `status().lastError`. **Accept:**
  SIGTERM drains cleanly; a bad `apiKey` surfaces a readable error, no hang.
- **Phase 3 — HA parity.** Wire `connections` (R4) + `instanceId` (R5) into the
  config JSON. **Accept:** multi-home + affinity match the pure-JS `connect()`
  client against the relay's cluster e2e.
- **Phase 4 — packaging.** Fold `relay_tunnel_*` into the released cdylib across
  the CI matrix; verify the lazy behaviour on the Lambda path; smoke-install on
  linux/mac.

## Risks

1. **Prior-knowledge h2c bidi into Vert.x** (#1). The Rust h2 client must send
   the h2 preface (not an h1→h2 upgrade), and the Vert.x server must set
   `request.version() == HTTP_2` so bidi engages. Strongly expected (Restate
   already talks h2c bidi to this exact server on default `HttpServerOptions`),
   but verify in Phase 0 — it is make-or-break.
2. **Java 23 floor** for the FFM path. Fallback: the same cdylib built as a
   binary, spawned as a subprocess over the identical loopback socket — build
   only if pre-23 support is required.
3. **One-lib discipline.** The tunnel symbols must be genuinely inert unless
   `relay_tunnel_start` is called (no global constructors spinning up tokio), so
   the Lambda path stays load-only.

## Scope cuts for v1

- **`/whoami` mode** targeting the relay (what `relay-receiver` speaks today).
  `/_/start-tunnel` (real Restate Cloud tunnel server) is a later extension to
  `relay-receiver` itself.
- **Loopback TCP**, not UDS.
- **No drain** capability advertised.
- **FFM only** (Java 23+); subprocess fallback deferred.

## Open items — resolved / remaining

Resolved (superseded by the shared-core decision):

1. ~~`relay-receiver` must be consumable as a crate dependency~~ → the stack was
   **copied into `sdk-shared-core`** (forked), so there is no `publish = false`
   dependency to unblock and no cross-repo coupling. The sdk-java cdylib depends
   on the shared-core `relay` branch with `features = ["tunnel"]`.
2. ~~Confirm the engine home in the relay repo~~ → the engine is
   `sdk-shared-core/src/relay/loopback.rs`; both the C-ABI and (future) napi
   shims depend on shared-core's `relay::{Config, Engine, Handle}`.

Remaining (operator-time):

3. A live end-to-end run against a real relay (`cargo run --bin relay
   examples/relay.toml`, whoami mode): sender request on `:9080` → relay → tunnel
   → loopback → Vert.x → back. The mechanism is proven in shared-core's
   `tests/relay_loopback.rs`; this is the on-a-real-relay confirmation.
4. Swap the sdk-java cdylib's git dependency back to a crates.io
   `restate-sdk-shared-core = "…"` once the `tunnel` feature ships in a release.

*(R5 `instance_id` support — previously open — is present in `ReceiverConfig`.)*
