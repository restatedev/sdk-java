package dev.restate.sdk.core;

import io.grpc.Context;

/**
 * This represents a stable identifier created by Restate for this invocation. It can be used as
 * idempotency key when accessing external systems.
 *
 * <p>You can embed it in external system requests by using {@link #toString()}.
 */
public interface InvocationId {

  Context.Key<InvocationId> INVOCATION_ID_KEY = Context.key("restate.dev/service_invocation_id");

  /** Retrieves the current invocation id from the current gRPC {@link Context}. */
  static InvocationId current() {
    return INVOCATION_ID_KEY.get();
  }

  @Override
  String toString();
}
