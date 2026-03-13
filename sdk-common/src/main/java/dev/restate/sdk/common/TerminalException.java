// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import java.util.Map;
import java.util.Objects;

/** When thrown in a Restate service method, it will complete the invocation with an error. */
public class TerminalException extends RuntimeException {

  public static final int ABORTED_CODE = 409;
  public static final int CANCELLED_CODE = ABORTED_CODE;
  public static final int BAD_REQUEST_CODE = 400;
  public static final int INTERNAL_SERVER_ERROR_CODE = 500;

  private final int code;
  private final Map<String, String> metadata;

  public TerminalException() {
    this(INTERNAL_SERVER_ERROR_CODE);
  }

  /** Like {@link #TerminalException(int, String)}, without message. */
  public TerminalException(int code) {
    this(code, "Error " + code);
  }

  /**
   * Create a new {@link TerminalException}.
   *
   * @param code HTTP response status code
   * @param message error message
   */
  public TerminalException(int code, String message) {
    this(code, message, null);
  }

  /**
   * Like {@link #TerminalException(int, String)}, with code {@link #INTERNAL_SERVER_ERROR_CODE}.
   */
  public TerminalException(String message) {
    this(INTERNAL_SERVER_ERROR_CODE, message, null);
  }

  /**
   * Create a new {@link TerminalException}.
   *
   * @param code HTTP response status code
   * @param message error message
   * @param metadata error metadata (supported only from Restate > 1.6)
   */
  public TerminalException(int code, String message, Map<String, String> metadata) {
    super(message);
    this.code = code;
    this.metadata = Objects.requireNonNullElse(metadata, Map.of());
  }

  /**
   * Create a new {@link TerminalException}.
   *
   * @param message error message
   * @param metadata error metadata (supported only from Restate > 1.6)
   */
  public TerminalException(String message, Map<String, String> metadata) {
    this(INTERNAL_SERVER_ERROR_CODE, message, metadata);
  }

  /**
   * @return status code
   */
  public int getCode() {
    return code;
  }

  /**
   * @return the error metadata
   */
  public Map<String, String> getMetadata() {
    return metadata;
  }
}
