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
import dev.restate.sdk.core.generated.protocol.Protocol;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;

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

  static TerminalException toRestateException(Protocol.Failure failure) {
    return new TerminalException(failure.getCode(), failure.getMessage());
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

  /**
   * Returns a string representation of a command message.
   *
   * @param message The command message
   * @return A string representation of the command message
   */
  static String commandMessageToString(MessageLite message) {
    if (message instanceof Protocol.InputCommandMessage) {
      return "handler input";
    } else if (message instanceof Protocol.OutputCommandMessage) {
      return "handler return";
    } else if (message instanceof Protocol.GetLazyStateCommandMessage) {
      return "get state";
    } else if (message instanceof Protocol.GetLazyStateKeysCommandMessage) {
      return "get state keys";
    } else if (message instanceof Protocol.SetStateCommandMessage) {
      return "set state";
    } else if (message instanceof Protocol.ClearStateCommandMessage) {
      return "clear state";
    } else if (message instanceof Protocol.ClearAllStateCommandMessage) {
      return "clear all state";
    } else if (message instanceof Protocol.GetPromiseCommandMessage) {
      return "get promise";
    } else if (message instanceof Protocol.PeekPromiseCommandMessage) {
      return "peek promise";
    } else if (message instanceof Protocol.CompletePromiseCommandMessage) {
      return "complete promise";
    } else if (message instanceof Protocol.SleepCommandMessage) {
      return "sleep";
    } else if (message instanceof Protocol.CallCommandMessage) {
      return "call";
    } else if (message instanceof Protocol.OneWayCallCommandMessage) {
      return "one way call/send";
    } else if (message instanceof Protocol.SendSignalCommandMessage) {
      return "send signal";
    } else if (message instanceof Protocol.RunCommandMessage) {
      return "run";
    } else if (message instanceof Protocol.AttachInvocationCommandMessage) {
      return "attach invocation";
    } else if (message instanceof Protocol.GetInvocationOutputCommandMessage) {
      return "get invocation output";
    } else if (message instanceof Protocol.CompleteAwakeableCommandMessage) {
      return "complete awakeable";
    }

    return message.getClass().getSimpleName();
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
