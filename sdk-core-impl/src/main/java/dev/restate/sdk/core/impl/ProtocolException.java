package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import io.grpc.Status;

public class ProtocolException extends RuntimeException {

  private final Status.Code grpcCode;

  private ProtocolException(String message) {
    this(message, Status.Code.INTERNAL);
  }

  private ProtocolException(String message, Status.Code grpcCode) {
    this(message, null, grpcCode);
  }

  public ProtocolException(String message, Throwable cause, Status.Code grpcCode) {
    super(message, cause);
    this.grpcCode = grpcCode;
  }

  public Status.Code getGrpcCode() {
    return grpcCode;
  }

  static ProtocolException unexpectedMessage(
      Class<? extends MessageLite> expected, MessageLite actual) {
    return new ProtocolException(
        "Unexpected message type received from the runtime. Expected: '"
            + expected.getCanonicalName()
            + "', Actual: '"
            + actual.getClass().getCanonicalName()
            + "'");
  }

  static ProtocolException unexpectedMessage(String expected, MessageLite actual) {
    return new ProtocolException(
        "Unexpected message type received from the runtime. Expected "
            + expected
            + ", Actual: '"
            + actual.getClass().getCanonicalName()
            + "'");
  }

  static ProtocolException entryDoNotMatch(MessageLite expected, MessageLite actual) {
    return new ProtocolException(
        "Journal entry " + expected.getClass() + " do not match: " + expected + " != " + actual);
  }

  static ProtocolException completionDoNotMatch(
      Class<? extends MessageLite> expected, Protocol.CompletionMessage.ResultCase actual) {
    return new ProtocolException(
        "Completion for entry " + expected + " don't expect completion variant " + actual);
  }

  static ProtocolException unknownMessageType(short type) {
    return new ProtocolException("MessageType " + Integer.toHexString(type) + " unknown");
  }

  static ProtocolException methodNotFound(String svcName, String methodName) {
    return new ProtocolException(
        "Cannot find method '" + svcName + "/" + methodName + "'", Status.Code.NOT_FOUND);
  }

  static ProtocolException inputPublisherError(Throwable cause) {
    return new ProtocolException("Error when reading from input", cause, Status.Code.INTERNAL);
  }
}
