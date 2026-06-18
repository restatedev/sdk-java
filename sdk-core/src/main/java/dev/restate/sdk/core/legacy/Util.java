// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.legacy;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.protobuf.UnsafeByteOperations;
import dev.restate.common.Slice;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class Util {

  /**
   * Compute the deterministic random seed for an invocation. When the start message carried a
   * random seed (service protocol &gt;= V6) it is used as-is; otherwise it is derived from the
   * debug id by hashing it with SHA-256, mirroring the legacy {@code InvocationIdImpl} fallback.
   */
  static long randomSeed(String debugId, @Nullable Long seed) {
    if (seed != null) {
      return seed;
    }
    // Hash the id to SHA-256 to increase entropy
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    byte[] digest = md.digest(debugId.getBytes(StandardCharsets.UTF_8));

    // Generate the long
    long n = 0;
    n |= ((long) (digest[7] & 0xFF) << (Byte.SIZE * 7));
    n |= ((long) (digest[6] & 0xFF) << (Byte.SIZE * 6));
    n |= ((long) (digest[5] & 0xFF) << (Byte.SIZE * 5));
    n |= ((long) (digest[4] & 0xFF) << (Byte.SIZE * 4));
    n |= ((long) (digest[3] & 0xFF) << (Byte.SIZE * 3));
    n |= ((digest[2] & 0xFF) << (Byte.SIZE * 2));
    n |= ((digest[1] & 0xFF) << Byte.SIZE);
    n |= (digest[0] & 0xFF);
    return n;
  }

  protected static Protocol.Failure toProtocolFailure(
      int code, String message, Map<String, String> metadata) {
    Protocol.Failure.Builder builder = Protocol.Failure.newBuilder().setCode(code);
    if (message != null) {
      builder.setMessage(message);
    }
    if (metadata != null) {
      for (Map.Entry<String, String> entry : metadata.entrySet()) {
        builder.addMetadata(
            Protocol.FailureMetadata.newBuilder()
                .setKey(entry.getKey())
                .setValue(entry.getValue()));
      }
    }
    return builder.build();
  }

  static Protocol.Failure toProtocolFailure(Throwable throwable) {
    if (throwable instanceof TerminalException) {
      return toProtocolFailure(
          ((TerminalException) throwable).getCode(),
          throwable.getMessage(),
          ((TerminalException) throwable).getMetadata());
    }
    return toProtocolFailure(
        TerminalException.INTERNAL_SERVER_ERROR_CODE, throwable.toString(), Map.of());
  }

  static TerminalException toRestateException(Protocol.Failure failure) {
    return new TerminalException(
        failure.getCode(),
        failure.getMessage(),
        failure.getMetadataList().stream()
            .collect(
                Collectors.toMap(
                    Protocol.FailureMetadata::getKey,
                    Protocol.FailureMetadata::getValue,
                    (a, b) -> b,
                    LinkedHashMap::new)));
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

  private static final String AWAKEABLE_IDENTIFIER_PREFIX = "sign_1";

  static String awakeableIdStr(ByteString invocationId, int signalId) {
    return AWAKEABLE_IDENTIFIER_PREFIX
        + Base64.getUrlEncoder()
            .encodeToString(
                invocationId
                    .concat(ByteString.copyFrom(ByteBuffer.allocate(4).putInt(signalId).flip()))
                    .toByteArray());
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
