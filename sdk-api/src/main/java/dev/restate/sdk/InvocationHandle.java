// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.Output;

/**
 * Handle to interact with an invocation.
 *
 * @param <Res> Response type
 */
public interface InvocationHandle<Res> {
  /**
   * @return the invocation id of this invocation
   */
  String invocationId();

  /** Cancel this invocation. */
  void cancel();

  /** Attach to this invocation. This will wait for the invocation to complete */
  DurableFuture<Res> attach();

  /**
   * @return the output of this invocation, if present.
   */
  Output<Res> getOutput();
}
