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
 * This represents a stable identifier created by Restate for this invocation.
 *
 * <p>You can embed it in external system requests by using {@link #toString()}.
 */
public interface InvocationId {

  /**
   * @deprecated Just use the random provided by the context API.
   */
  @Deprecated(forRemoval = true)
  long toRandomSeed();

  @Override
  String toString();
}
