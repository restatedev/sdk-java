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
 * The failure that pending producer futures complete with when the ingestion stream errors or
 * closes. {@link #getKind()} maps the server's {@code ErrorKind}, or {@link Kind#UNKNOWN} for
 * transport-level failures.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public class IntegrationClientException extends RuntimeException {

  /** Classification of an ingestion stream failure. */
  public enum Kind {
    UNKNOWN,
    SHUTTING_DOWN,
    GO_AWAY,
    NOT_FOUND,
    BAD_REQUEST,
  }

  private final Kind kind;

  public IntegrationClientException(Kind kind, String message) {
    this(kind, message, null);
  }

  public IntegrationClientException(Kind kind, String message, Throwable cause) {
    super(message != null ? message : kind.name(), cause);
    this.kind = kind;
  }

  public Kind getKind() {
    return kind;
  }
}
