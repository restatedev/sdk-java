// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

/**
 * This represents a stable identifier created by Restate for this invocation. It can be used as
 * idempotency key when accessing external systems.
 *
 * <p>You can embed it in external system requests by using {@link #toString()}.
 */
public interface InvocationId {

  /**
   * @return a seed to be used with {@link java.util.Random}.
   */
  long toRandomSeed();

  @Override
  String toString();
}
