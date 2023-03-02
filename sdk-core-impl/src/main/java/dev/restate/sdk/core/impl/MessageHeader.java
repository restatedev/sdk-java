package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;

public class MessageHeader {

  private static final short DONE_FLAG = 0x0001;
  private static final short REQUIRES_ACK_FLAG = 0x0001;

  private final MessageType type;
  private final short flags;
  private final int length;

  private MessageHeader(MessageType type, short flags, int length) {
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
    res |= ((long) (type.encode()) << 48);
    res |= ((long) (flags) << 32);
    res |= length;
    return res;
  }

  public boolean hasFlag(short flag) {
    return (this.flags & flag) > 0;
  }

  public static MessageHeader parse(long encoded) throws ProtocolException {
    var ty_code = (short) (encoded >> 48);
    var flags = (short) (encoded >> 32);
    var len = (int) encoded;

    return new MessageHeader(MessageType.decode(ty_code), flags, len);
  }

  public static MessageHeader fromMessage(MessageLite msg) {
    if (msg instanceof Protocol.StartMessage) {
      return new MessageHeader(MessageType.StartMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.CompletionMessage) {
      return new MessageHeader(MessageType.CompletionMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.SuspensionMessage) {
      return new MessageHeader(MessageType.SuspensionMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.PollInputStreamEntryMessage) {
      return new MessageHeader(
          MessageType.PollInputStreamEntryMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.OutputStreamEntryMessage) {
      return new MessageHeader(
          MessageType.OutputStreamEntryMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.GetStateEntryMessage) {
      return new MessageHeader(
          MessageType.GetStateEntryMessage,
          ((Protocol.GetStateEntryMessage) msg).getResultCase()
                  != Protocol.GetStateEntryMessage.ResultCase.RESULT_NOT_SET
              ? DONE_FLAG
              : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Protocol.SetStateEntryMessage) {
      return new MessageHeader(
          MessageType.SetStateEntryMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.ClearStateEntryMessage) {
      return new MessageHeader(
          MessageType.ClearStateEntryMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.SleepEntryMessage) {
      return new MessageHeader(
          MessageType.SleepEntryMessage,
          ((Protocol.SleepEntryMessage) msg).hasResult() ? DONE_FLAG : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Protocol.InvokeEntryMessage) {
      return new MessageHeader(
          MessageType.InvokeEntryMessage,
          ((Protocol.InvokeEntryMessage) msg).getResultCase()
                  != Protocol.InvokeEntryMessage.ResultCase.RESULT_NOT_SET
              ? DONE_FLAG
              : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Protocol.BackgroundInvokeEntryMessage) {
      return new MessageHeader(
          MessageType.BackgroundInvokeEntryMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.AwakeableEntryMessage) {
      return new MessageHeader(
          MessageType.AwakeableEntryMessage,
          ((Protocol.AwakeableEntryMessage) msg).getResultCase()
                  != Protocol.AwakeableEntryMessage.ResultCase.RESULT_NOT_SET
              ? DONE_FLAG
              : 0,
          msg.getSerializedSize());
    } else if (msg instanceof Protocol.CompleteAwakeableEntryMessage) {
      return new MessageHeader(
          MessageType.CompleteAwakeableEntryMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Java.CombinatorAwaitableEntryMessage) {
      return new MessageHeader(
          MessageType.CombinatorAwaitableEntryMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Java.SideEffectEntryMessage) {
      return new MessageHeader(
          MessageType.SideEffectEntryMessage, REQUIRES_ACK_FLAG, msg.getSerializedSize());
    }
    throw new IllegalStateException();
  }
}
