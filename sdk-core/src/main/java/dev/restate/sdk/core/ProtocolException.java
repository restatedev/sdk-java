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
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.TerminalException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ProtocolException extends RuntimeException {

  static final int JOURNAL_MISMATCH_CODE = 32;
  static final int PROTOCOL_VIOLATION = 33;

  @SuppressWarnings("StaticAssignmentOfThrowable")
  static final ProtocolException CLOSED = new ProtocolException("Invocation closed");

  private final int failureCode;

  private ProtocolException(String message) {
    this(message, TerminalException.Code.INTERNAL.value());
  }

  private ProtocolException(String message, int failureCode) {
    this(message, null, failureCode);
  }

  public ProtocolException(String message, Throwable cause, int failureCode) {
    super(message, cause);
    this.failureCode = failureCode;
  }

  public int getFailureCode() {
    return failureCode;
  }

  public Protocol.ErrorMessage toErrorMessage() {
    // Convert stacktrace to string
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    pw.println("Stacktrace:");
    this.printStackTrace(pw);

    return Protocol.ErrorMessage.newBuilder()
        .setCode(failureCode)
        .setMessage(this.toString())
        .setDescription(sw.toString())
        .build();
  }

  static ProtocolException unexpectedMessage(
      Class<? extends MessageLite> expected, MessageLite actual) {
    return new ProtocolException(
        "Unexpected message type received from the runtime. Expected: '"
            + expected.getCanonicalName()
            + "', Actual: '"
            + actual.getClass().getCanonicalName()
            + "'",
        PROTOCOL_VIOLATION);
  }

  static ProtocolException entryDoesNotMatch(MessageLite expected, MessageLite actual) {
    return new ProtocolException(
        "Journal entry " + expected.getClass() + " does not match: " + expected + " != " + actual,
        JOURNAL_MISMATCH_CODE);
  }

  static ProtocolException completionDoesNotMatch(
      String entry, Protocol.CompletionMessage.ResultCase actual) {
    return new ProtocolException(
        "Completion for entry " + entry + " doesn't expect completion variant " + actual,
        JOURNAL_MISMATCH_CODE);
  }

  static ProtocolException unknownMessageType(short type) {
    return new ProtocolException(
        "MessageType " + Integer.toHexString(type) + " unknown", PROTOCOL_VIOLATION);
  }

  static ProtocolException methodNotFound(String componentName, String handlerName) {
    return new ProtocolException(
        "Cannot find handler '" + componentName + "/" + handlerName + "'",
        TerminalException.Code.NOT_FOUND.value());
  }

  static ProtocolException invalidSideEffectCall() {
    return new ProtocolException(
        "A syscall was invoked from within a side effect closure.",
        null,
        TerminalException.Code.UNKNOWN.value());
  }
}
