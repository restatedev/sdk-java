// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.protobuf.UnsafeByteOperations;
import dev.restate.common.Slice;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class Util {

  static Protocol.Failure toProtocolFailure(int code, String message) {
    Protocol.Failure.Builder builder = Protocol.Failure.newBuilder().setCode(code);
    if (message != null) {
      builder.setMessage(message);
    }
    return builder.build();
  }

  static Protocol.Failure toProtocolFailure(Throwable throwable) {
    if (throwable instanceof TerminalException) {
      return toProtocolFailure(((TerminalException) throwable).getCode(), throwable.getMessage());
    }
    return toProtocolFailure(TerminalException.INTERNAL_SERVER_ERROR_CODE, throwable.toString());
  }

  static Protocol.ErrorMessage toErrorMessage(
      Throwable throwable,
      int currentCommandIndex,
      @Nullable String currentCommandName,
      @Nullable MessageType currentCommandType) {
    Protocol.ErrorMessage.Builder msg = Protocol.ErrorMessage.newBuilder();

    if (throwable.getMessage() == null) {
      // This happens only with few common exceptions, but anyway
      msg.setMessage(throwable.toString());
    } else {
      msg.setMessage(throwable.getMessage());
    }

    if (throwable instanceof ProtocolException) {
      msg.setCode(((ProtocolException) throwable).getCode());
    } else {
      msg.setCode(TerminalException.INTERNAL_SERVER_ERROR_CODE);
    }

    // Convert stacktrace to string
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    msg.setStacktrace(sw.toString());

    // Add journal entry info
    if (currentCommandIndex >= 0) {
      msg.setRelatedCommandIndex(currentCommandIndex);
    }
    if (currentCommandName != null) {
      msg.setRelatedCommandName(currentCommandName);
    }
    if (currentCommandType != null) {
      msg.setRelatedCommandType(currentCommandType.encode());
    }

    return msg.build();
  }

  static TerminalException toRestateException(Protocol.Failure failure) {
    return new TerminalException(failure.getCode(), failure.getMessage());
  }

  static void assertEntryEquals(MessageLite expected, MessageLite actual) {
    if (!Objects.equals(expected, actual)) {
      throw ProtocolException.commandDoesNotMatch(expected, actual);
    }
  }

  static void assertEntryClass(Class<? extends MessageLite> clazz, MessageLite actual) {
    if (!clazz.equals(actual.getClass())) {
      throw ProtocolException.commandClassDoesNotMatch(clazz, actual);
    }
  }

  /** NOTE! This method rewinds the buffer!!! */
  static ByteString nioBufferToProtobufBuffer(ByteBuffer nioBuffer) {
    return UnsafeByteOperations.unsafeWrap(nioBuffer);
  }

  /** NOTE! This method rewinds the buffer!!! */
  static ByteString sliceToByteString(Slice slice) {
    return nioBufferToProtobufBuffer(slice.asReadOnlyByteBuffer());
  }

  static Slice byteStringToSlice(ByteString byteString) {
    return new ByteStringSlice(byteString);
  }

  static Duration durationMin(Duration a, Duration b) {
    return (a.compareTo(b) <= 0) ? a : b;
  }

  private static final class ByteStringSlice implements Slice {
    private final ByteString byteString;

    public ByteStringSlice(ByteString bytes) {
      this.byteString = Objects.requireNonNull(bytes);
    }

    @Override
    public ByteBuffer asReadOnlyByteBuffer() {
      return byteString.asReadOnlyByteBuffer();
    }

    @Override
    public int readableBytes() {
      return byteString.size();
    }

    @Override
    public void copyTo(byte[] target) {
      copyTo(target, 0);
    }

    @Override
    public void copyTo(byte[] target, int targetOffset) {
      byteString.copyTo(target, targetOffset);
    }

    @Override
    public byte byteAt(int position) {
      return byteString.byteAt(position);
    }

    @Override
    public void copyTo(ByteBuffer buffer) {
      byteString.copyTo(buffer);
    }

    @Override
    public byte[] toByteArray() {
      return byteString.toByteArray();
    }
  }
}
