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
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;

public class MessageHeader {

  static final short SUPPORTED_PROTOCOL_VERSION = 2;

  static final short VERSION_MASK = 0x03FF;
  static final short DONE_FLAG = 0x0001;
  static final int REQUIRES_ACK_FLAG = 0x8000;

  private final MessageType type;
  private final int flags;
  private final int length;

  public MessageHeader(MessageType type, int flags, int length) {
    this.type = type;
    this.flags = flags;
    this.length = length;
  }

  public MessageType getType() {
    return type;
  }

  public int getLength() {
    return length;
  }

  public long encode() {
    long res = 0L;
    res |= ((long) type.encode() << 48);
    res |= ((long) flags << 32);
    res |= length;
    return res;
  }

  public static MessageHeader parse(long encoded) throws ProtocolException {
    var ty_code = (short) (encoded >> 48);
    var flags = (short) (encoded >> 32);
    var len = (int) encoded;

    return new MessageHeader(MessageType.decode(ty_code), flags, len);
  }

  public static MessageHeader fromMessage(MessageLite msg) {
    if (msg instanceof Protocol.GetStateEntryMessage) {
      return new MessageHeader(
          MessageType.GetStateEntryMessage,
          ((Protocol.GetStateEntryMessage) msg).getResultCase()
                  != Protocol.GetStateEntryMessage.ResultCase.RESULT_NOT_SET
              ? DONE_FLAG
              : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Protocol.GetStateKeysEntryMessage) {
      return new MessageHeader(
          MessageType.GetStateKeysEntryMessage,
          ((Protocol.GetStateKeysEntryMessage) msg).getResultCase()
                  != Protocol.GetStateKeysEntryMessage.ResultCase.RESULT_NOT_SET
              ? DONE_FLAG
              : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Protocol.SleepEntryMessage) {
      return new MessageHeader(
          MessageType.SleepEntryMessage,
          ((Protocol.SleepEntryMessage) msg).getResultCase()
                  != Protocol.SleepEntryMessage.ResultCase.RESULT_NOT_SET
              ? DONE_FLAG
              : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Protocol.InvokeEntryMessage) {
      return new MessageHeader(
          MessageType.InvokeEntryMessage,
          ((Protocol.InvokeEntryMessage) msg).getResultCase()
                  != Protocol.InvokeEntryMessage.ResultCase.RESULT_NOT_SET
              ? DONE_FLAG
              : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Protocol.AwakeableEntryMessage) {
      return new MessageHeader(
          MessageType.AwakeableEntryMessage,
          ((Protocol.AwakeableEntryMessage) msg).getResultCase()
                  != Protocol.AwakeableEntryMessage.ResultCase.RESULT_NOT_SET
              ? DONE_FLAG
              : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Java.SideEffectEntryMessage) {
      return new MessageHeader(
          MessageType.SideEffectEntryMessage, REQUIRES_ACK_FLAG, msg.getSerializedSize());
    }
    // Messages with no flags
    return new MessageHeader(MessageType.fromMessage(msg), 0, msg.getSerializedSize());
  }

  public static void checkProtocolVersion(MessageHeader header) {
    if (header.type != MessageType.StartMessage) {
      throw new IllegalStateException("Expected StartMessage, got " + header.type);
    }

    short version = (short) (header.flags & VERSION_MASK);
    if (version != SUPPORTED_PROTOCOL_VERSION) {
      throw new IllegalStateException(
          "Unsupported protocol version "
              + version
              + ", only version "
              + SUPPORTED_PROTOCOL_VERSION
              + " is supported");
    }
  }
}
