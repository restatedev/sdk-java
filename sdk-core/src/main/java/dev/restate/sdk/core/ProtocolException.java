// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.types.TerminalException;

public class ProtocolException extends RuntimeException {

  static final int UNAUTHORIZED_CODE = 401;
  static final int NOT_FOUND_CODE = 404;
  public static final int UNSUPPORTED_MEDIA_TYPE_CODE = 415;
  static final int INTERNAL_CODE = 500;
  static final int JOURNAL_MISMATCH_CODE = 570;
  static final int PROTOCOL_VIOLATION_CODE = 571;

  @SuppressWarnings("StaticAssignmentOfThrowable")
  static final ProtocolException CLOSED = new ProtocolException("Invocation closed");

  private final int code;

  private ProtocolException(String message) {
    this(message, TerminalException.INTERNAL_SERVER_ERROR_CODE);
  }

  public ProtocolException(String message, int code) {
    this(message, code, null);
  }

  public ProtocolException(String message, int code, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  static ProtocolException unexpectedMessage(
      Class<? extends MessageLite> expected, MessageLite actual) {
    return new ProtocolException(
        "Unexpected message type received from the runtime. Expected: '"
            + expected.getCanonicalName()
            + "', Actual: '"
            + actual.getClass().getCanonicalName()
            + "'",
        PROTOCOL_VIOLATION_CODE);
  }

  static ProtocolException unexpectedNotificationVariant(Class<?> clazz) {
    return new ProtocolException(
        "Unexpected notification variant " + clazz.getName(), PROTOCOL_VIOLATION_CODE);
  }

  static ProtocolException entryDoesNotMatch(MessageLite expected, MessageLite actual) {
    return new ProtocolException(
        "Journal entry " + expected.getClass() + " does not match: " + expected + " != " + actual,
        JOURNAL_MISMATCH_CODE);
  }

  static ProtocolException unknownMessageType(short type) {
    return new ProtocolException(
        "MessageType " + Integer.toHexString(type) + " unknown", PROTOCOL_VIOLATION_CODE);
  }

  static ProtocolException methodNotFound(String serviceName, String handlerName) {
    return new ProtocolException(
        "Cannot find handler '" + serviceName + "/" + handlerName + "'", NOT_FOUND_CODE);
  }

  static ProtocolException invalidSideEffectCall() {
    return new ProtocolException(
        "A syscall was invoked from within a side effect closure.",
        TerminalException.INTERNAL_SERVER_ERROR_CODE,
        null);
  }

  public static ProtocolException idempotencyKeyIsEmpty() {
    return new ProtocolException(
            "The provided idempotency key is empty.",
            TerminalException.INTERNAL_SERVER_ERROR_CODE,
            null);
  }

  static ProtocolException unauthorized(Throwable e) {
    return new ProtocolException("Unauthorized", UNAUTHORIZED_CODE, e);
  }
}
