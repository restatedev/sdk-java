// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

/**
 * Thrown by {@code send} when the producer cannot admit an invocation within the configured {@link
 * ProducerOptions#maxBlockTime()}, or the thread is interrupted while waiting. Admission may be
 * blocked by a full local buffer, or by protocol or transport backpressure when local buffering is
 * disabled.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public class ProducerBufferExhaustedException extends RuntimeException {
  public ProducerBufferExhaustedException(String message) {
    super(message);
  }

  public ProducerBufferExhaustedException(String message, Throwable cause) {
    super(message, cause);
  }
}
