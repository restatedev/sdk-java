// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import org.jspecify.annotations.Nullable;

/**
 * The failure that pending producer futures complete with when the ingestion stream errors or
 * closes. {@link #getKind()} maps the server's {@code ErrorKind}, or {@link Kind#UNKNOWN} for
 * transport-level failures.
 *
 * <p>An error is either <b>record-scoped</b> or <b>stream-scoped</b>. A record-scoped error rejects
 * a single record (a missing key, an unknown handler, an internal ingestion failure, ...) while the
 * stream itself stays well-formed; {@link #getInvocationOffset()} then carries the offset of the
 * offending record. A stream-scoped error describes the stream as a whole (shutdown, a protocol
 * violation, an invalid {@code Start}/{@code IngestionDefaults}) and leaves the offset unset.
 * Either way the server tears the stream down after the error; the offset lets an exactly-once
 * caller resume from the last committed offset and skip the rejected record before resending the
 * rest.
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
  private final @Nullable Long invocationOffset;

  public IntegrationClientException(Kind kind, String message) {
    this(kind, message, (Long) null);
  }

  public IntegrationClientException(Kind kind, String message, @Nullable Long invocationOffset) {
    super(message != null ? message : kind.name());
    this.kind = kind;
    this.invocationOffset = invocationOffset;
  }

  public IntegrationClientException(Kind kind, String message, Throwable cause) {
    this(kind, message, cause, null);
  }

  public IntegrationClientException(
      Kind kind, String message, Throwable cause, @Nullable Long invocationOffset) {
    super(message != null ? message : kind.name(), cause);
    this.kind = kind;
    this.invocationOffset = invocationOffset;
  }

  public Kind getKind() {
    return kind;
  }

  /**
   * The offset of the record this error is associated with, or {@code null} when the error is
   * stream-scoped (not attributable to a single record).
   *
   * @return the offset of the rejected record, or {@code null} for a stream-scoped error
   */
  public @Nullable Long getInvocationOffset() {
    return invocationOffset;
  }
}
