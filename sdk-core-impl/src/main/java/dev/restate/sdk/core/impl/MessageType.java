package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;

public enum MessageType {
  StartMessage,
  CompletionMessage,
  SupportedVersionRangeMessage,
  SuspensionMessage,

  // IO
  PollInputStreamEntryMessage,
  OutputStreamEntryMessage,

  // State access
  GetStateEntryMessage,
  SetStateEntryMessage,
  ClearStateEntryMessage,

  // Syscalls
  SleepEntryMessage,
  InvokeEntryMessage,
  BackgroundInvokeEntryMessage,
  AwakeableEntryMessage,
  CompleteAwakeableEntryMessage,
  SideEffectEntryMessage,

  // SDK specific
  CombinatorAwaitableEntryMessage;

  public Parser<? extends MessageLite> messageParser() {
    switch (this) {
      case StartMessage:
        return Protocol.StartMessage.parser();
      case CompletionMessage:
        return Protocol.CompletionMessage.parser();
      case SupportedVersionRangeMessage:
        return Protocol.SupportedVersionRangeMessage.parser();
      case SuspensionMessage:
        return Protocol.SuspensionMessage.parser();
      case PollInputStreamEntryMessage:
        return Protocol.PollInputStreamEntryMessage.parser();
      case OutputStreamEntryMessage:
        return Protocol.OutputStreamEntryMessage.parser();
      case GetStateEntryMessage:
        return Protocol.GetStateEntryMessage.parser();
      case SetStateEntryMessage:
        return Protocol.SetStateEntryMessage.parser();
      case ClearStateEntryMessage:
        return Protocol.ClearStateEntryMessage.parser();
      case SleepEntryMessage:
        return Protocol.SleepEntryMessage.parser();
      case InvokeEntryMessage:
        return Protocol.InvokeEntryMessage.parser();
      case BackgroundInvokeEntryMessage:
        return Protocol.BackgroundInvokeEntryMessage.parser();
      case AwakeableEntryMessage:
        return Protocol.AwakeableEntryMessage.parser();
      case CompleteAwakeableEntryMessage:
        return Protocol.CompleteAwakeableEntryMessage.parser();
      case SideEffectEntryMessage:
        return Protocol.SideEffectEntryMessage.parser();
      case CombinatorAwaitableEntryMessage:
        return Java.CombinatorAwaitableEntryMessage.parser();
    }
    throw new IllegalStateException();
  }

  public short encode() {
    switch (this) {
      case StartMessage:
        return 0x0000;
      case CompletionMessage:
        return 0x0001;
      case SupportedVersionRangeMessage:
        return 0x0002;
      case SuspensionMessage:
        return 0x0003;
      case PollInputStreamEntryMessage:
        return 0x0400;
      case OutputStreamEntryMessage:
        return 0x0401;
      case GetStateEntryMessage:
        return 0x0800;
      case SetStateEntryMessage:
        return 0x0801;
      case ClearStateEntryMessage:
        return 0x0802;
      case SleepEntryMessage:
        return 0x0C00;
      case InvokeEntryMessage:
        return 0x0C01;
      case BackgroundInvokeEntryMessage:
        return 0x0C02;
      case AwakeableEntryMessage:
        return 0x0C03;
      case CompleteAwakeableEntryMessage:
        return 0x0C04;
      case SideEffectEntryMessage:
        return 0x0C05;
      case CombinatorAwaitableEntryMessage:
        return (short) 0xFC00;
    }
    throw new IllegalStateException();
  }

  public static MessageType decode(short value) throws ProtocolException {
    switch (value) {
      case 0x0000:
        return StartMessage;
      case 0x0001:
        return CompletionMessage;
      case 0x0002:
        return SupportedVersionRangeMessage;
      case 0x0003:
        return SuspensionMessage;
      case 0x0400:
        return PollInputStreamEntryMessage;
      case 0x0401:
        return OutputStreamEntryMessage;
      case 0x0800:
        return GetStateEntryMessage;
      case 0x0801:
        return SetStateEntryMessage;
      case 0x0802:
        return ClearStateEntryMessage;
      case 0x0C00:
        return SleepEntryMessage;
      case 0x0C01:
        return InvokeEntryMessage;
      case 0x0C02:
        return BackgroundInvokeEntryMessage;
      case 0x0C03:
        return AwakeableEntryMessage;
      case 0x0C04:
        return CompleteAwakeableEntryMessage;
      case 0x0C05:
        return SideEffectEntryMessage;
      case (short) 0xFC00:
        return CombinatorAwaitableEntryMessage;
    }
    throw ProtocolException.unknownMessageType(value);
  }
}
