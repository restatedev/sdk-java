// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import io.grpc.Context;

/**
 * This represents a stable identifier created by Restate for this invocation. It can be used as
 * idempotency key when accessing external systems.
 *
 * <p>You can embed it in external system requests by using {@link #toString()}.
 */
public interface InvocationId {

  /** gRPC {@link Context} key for invocation id. */
  Context.Key<InvocationId> INVOCATION_ID_KEY = Context.key("restate.dev/invocation_id");

  /**
   * @return the current invocation id from the current gRPC {@link Context}.
   */
  static InvocationId current() {
    return INVOCATION_ID_KEY.get();
  }

  @Override
  String toString();
}
