// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

/**
 * Interface to invoke generated service code.
 *
 * <p>This interface is strongly inspired by {@link io.grpc.ServerCall.Listener}.
 *
 * @param <M> type of the incoming message
 */
public interface RestateServerCallListener<M> {
  /** Invoke the service code. */
  void invoke(M message);

  /** Send cancel signal to service code. */
  void cancel();

  /** Close the service code. */
  void close();

  /** Set the underlying listener as ready. */
  void listenerReady();
}
