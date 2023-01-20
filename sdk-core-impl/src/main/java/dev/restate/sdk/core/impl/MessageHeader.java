package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;

public class MessageHeader {

  private static final long DONE_MASK = 0x1_0000_0000L;

  private final MessageType type;
  private final int length;

  private final boolean doneFlag;

  private MessageHeader(MessageType type, int length, boolean doneFlag) {
    this.type = type;
    this.length = length;
    this.doneFlag = doneFlag;
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
    res |= (long) length;
    if (doneFlag) {
      res |= DONE_MASK;
    }
    return res;
  }

  public static MessageHeader parse(long encoded) throws ProtocolException {
    var ty_code = (short) (encoded >> 48);
    var len = (int) encoded;

    return new MessageHeader(MessageType.decode(ty_code), len, (encoded & DONE_MASK) != 0);
  }

  public static MessageHeader fromMessage(MessageLite msg) {
    if (msg instanceof Protocol.StartMessage) {
      return new MessageHeader(MessageType.StartMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.CompletionMessage) {
      return new MessageHeader(MessageType.CompletionMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.SupportedVersionRangeMessage) {
      return new MessageHeader(
          MessageType.SupportedVersionRangeMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.SuspensionMessage) {
      return new MessageHeader(MessageType.SuspensionMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.PollInputStreamEntryMessage) {
      return new MessageHeader(
          MessageType.PollInputStreamEntryMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.OutputStreamEntryMessage) {
      return new MessageHeader(
          MessageType.OutputStreamEntryMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.GetStateEntryMessage) {
      return new MessageHeader(
          MessageType.GetStateEntryMessage,
          msg.getSerializedSize(),
          ((Protocol.GetStateEntryMessage) msg).getResultCase()
              != Protocol.GetStateEntryMessage.ResultCase.RESULT_NOT_SET);
    } else if (msg instanceof Protocol.SetStateEntryMessage) {
      return new MessageHeader(MessageType.SetStateEntryMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.ClearStateEntryMessage) {
      return new MessageHeader(MessageType.ClearStateEntryMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.SleepEntryMessage) {
      return new MessageHeader(
          MessageType.SleepEntryMessage,
          msg.getSerializedSize(),
          ((Protocol.SleepEntryMessage) msg).hasResult());
    } else if (msg instanceof Protocol.InvokeEntryMessage) {
      return new MessageHeader(
          MessageType.InvokeEntryMessage,
          msg.getSerializedSize(),
          ((Protocol.InvokeEntryMessage) msg).getResultCase()
              != Protocol.InvokeEntryMessage.ResultCase.RESULT_NOT_SET);
    } else if (msg instanceof Protocol.BackgroundInvokeEntryMessage) {
      return new MessageHeader(
          MessageType.BackgroundInvokeEntryMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.AwakeableEntryMessage) {
      return new MessageHeader(
          MessageType.AwakeableEntryMessage,
          msg.getSerializedSize(),
          ((Protocol.AwakeableEntryMessage) msg).getResultCase()
              == Protocol.AwakeableEntryMessage.ResultCase.RESULT_NOT_SET);
    } else if (msg instanceof Protocol.CompleteAwakeableEntryMessage) {
      return new MessageHeader(
          MessageType.CompleteAwakeableEntryMessage, msg.getSerializedSize(), false);
    } else if (msg instanceof Protocol.SideEffectEntryMessage) {
      return new MessageHeader(MessageType.SideEffectEntryMessage, msg.getSerializedSize(), false);
    }
    throw new IllegalStateException();
  }
}
