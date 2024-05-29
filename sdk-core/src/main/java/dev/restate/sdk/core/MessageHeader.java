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
      return fromCompletableMessage(
          (Protocol.GetStateEntryMessage) msg, Entries.GetStateEntry.INSTANCE);
    } else if (msg instanceof Protocol.GetStateKeysEntryMessage) {
      return fromCompletableMessage(
          (Protocol.GetStateKeysEntryMessage) msg, Entries.GetStateKeysEntry.INSTANCE);
    } else if (msg instanceof Protocol.GetPromiseEntryMessage) {
      return fromCompletableMessage(
          (Protocol.GetPromiseEntryMessage) msg, Entries.GetPromiseEntry.INSTANCE);
    } else if (msg instanceof Protocol.PeekPromiseEntryMessage) {
      return fromCompletableMessage(
          (Protocol.PeekPromiseEntryMessage) msg, Entries.PeekPromiseEntry.INSTANCE);
    } else if (msg instanceof Protocol.CompletePromiseEntryMessage) {
      return fromCompletableMessage(
          (Protocol.CompletePromiseEntryMessage) msg, Entries.CompletePromiseEntry.INSTANCE);
    } else if (msg instanceof Protocol.SleepEntryMessage) {
      return fromCompletableMessage((Protocol.SleepEntryMessage) msg, Entries.SleepEntry.INSTANCE);
    } else if (msg instanceof Protocol.CallEntryMessage) {
      return new MessageHeader(
          MessageType.CallEntryMessage,
          ((Protocol.CallEntryMessage) msg).getResultCase()
                  != Protocol.CallEntryMessage.ResultCase.RESULT_NOT_SET
              ? DONE_FLAG
              : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Protocol.AwakeableEntryMessage) {
      return fromCompletableMessage(
          (Protocol.AwakeableEntryMessage) msg, Entries.AwakeableEntry.INSTANCE);
    } else if (msg instanceof Protocol.RunEntryMessage) {
      return new MessageHeader(
          MessageType.RunEntryMessage, REQUIRES_ACK_FLAG, msg.getSerializedSize());
    } else if (msg instanceof Java.CombinatorAwaitableEntryMessage) {
      return new MessageHeader(
          MessageType.CombinatorAwaitableEntryMessage, REQUIRES_ACK_FLAG, msg.getSerializedSize());
    }
    // Messages with no flags
    return new MessageHeader(MessageType.fromMessage(msg), 0, msg.getSerializedSize());
  }

  private static <MSG extends MessageLite, E extends Entries.CompletableJournalEntry<MSG, ?>>
      MessageHeader fromCompletableMessage(MSG msg, E entry) {
    return new MessageHeader(
        MessageType.fromMessage(msg),
        entry.hasResult(msg) ? DONE_FLAG : 0,
        msg.getSerializedSize());
  }
}
