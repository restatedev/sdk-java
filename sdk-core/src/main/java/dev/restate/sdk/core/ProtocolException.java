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
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.statemachine.NotificationId;

public class ProtocolException extends RuntimeException {

  static final int UNAUTHORIZED_CODE = 401;
  static final int NOT_FOUND_CODE = 404;
  public static final int UNSUPPORTED_MEDIA_TYPE_CODE = 415;
  public static final int INTERNAL_CODE = 500;
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

  public static ProtocolException unexpectedMessage(
      Class<? extends MessageLite> expected, MessageLite actual) {
    return new ProtocolException(
        "Unexpected message type received from the runtime. Expected: '"
            + expected.getCanonicalName()
            + "', Actual: '"
            + actual.getClass().getCanonicalName()
            + "'",
        PROTOCOL_VIOLATION_CODE);
  }

  public static ProtocolException unexpectedMessage(String type, MessageLite actual) {
    return new ProtocolException(
        "Unexpected message type received from the runtime. Expected: '"
            + type
            + "', Actual: '"
            + actual.getClass().getCanonicalName()
            + "'",
        PROTOCOL_VIOLATION_CODE);
  }

  static ProtocolException unexpectedNotificationVariant(Class<?> clazz) {
    return new ProtocolException(
        "Unexpected notification variant " + clazz.getName(), PROTOCOL_VIOLATION_CODE);
  }

  public static ProtocolException commandDoesNotMatch(MessageLite expected, MessageLite actual) {
    return new ProtocolException(
        "Replayed journal doesn't match the handler code.\nThe handler code generated: "
            + expected
            + "\nwhile the replayed entry is: "
            + actual,
        JOURNAL_MISMATCH_CODE);
  }

  public static ProtocolException commandClassDoesNotMatch(
      Class<? extends MessageLite> expectedClazz, MessageLite actual) {
    return new ProtocolException(
        "Replayed journal doesn't match the handler code.\nThe handler code generated: "
            + expectedClazz.getName()
            + "\nwhile the replayed entry is: "
            + actual,
        JOURNAL_MISMATCH_CODE);
  }

  public static ProtocolException commandsToProcessIsEmpty() {
    return new ProtocolException("Expecting command queue to be non empty", JOURNAL_MISMATCH_CODE);
  }

  public static ProtocolException unknownMessageType(short type) {
    return new ProtocolException(
        "MessageType " + Integer.toHexString(type) + " unknown", PROTOCOL_VIOLATION_CODE);
  }

  public static ProtocolException methodNotFound(String serviceName, String handlerName) {
    return new ProtocolException(
        "Cannot find handler '" + serviceName + "/" + handlerName + "'", NOT_FOUND_CODE);
  }

  public static ProtocolException badState(Object thisState) {
    return new ProtocolException(
        "Cannot process operation because the handler is in unexpected state: " + thisState,
        INTERNAL_CODE);
  }

  public static ProtocolException badNotificationMessage(String missingField) {
    return new ProtocolException(
        "Bad notification message, missing field " + missingField, PROTOCOL_VIOLATION_CODE);
  }

  public static ProtocolException badRunNotificationId(NotificationId notificationId) {
    return new ProtocolException(
        "Bad run handle, should be mapped to a completion notification id, but was "
            + notificationId,
        PROTOCOL_VIOLATION_CODE);
  }

  public static ProtocolException commandMissingField(Class<?> clazz, String missingField) {
    return new ProtocolException(
        "Bad command " + clazz.getName() + ", missing field " + missingField,
        PROTOCOL_VIOLATION_CODE);
  }

  public static ProtocolException inputClosedWhileWaitingEntries() {
    return new ProtocolException(
        "The input was closed while still waiting to receive all the `known_entries`",
        PROTOCOL_VIOLATION_CODE);
  }

  public static ProtocolException closedWhileWaitingEntries() {
    return new ProtocolException(
        "The state machine was closed while still waiting to receive all the `known_entries`",
        PROTOCOL_VIOLATION_CODE);
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

  public static ProtocolException unauthorized(Throwable e) {
    return new ProtocolException("Unauthorized", UNAUTHORIZED_CODE, e);
  }
}
