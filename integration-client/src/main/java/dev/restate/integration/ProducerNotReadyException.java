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
 * Thrown by {@code send} when local buffer capacity does not become available within the configured
 * {@link ProducerOptions#maxBlockTime()}.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public class ProducerNotReadyException extends RuntimeException {
  public ProducerNotReadyException(String message) {
    super(message);
  }

  public ProducerNotReadyException(String message, Throwable cause) {
    super(message, cause);
  }
}
