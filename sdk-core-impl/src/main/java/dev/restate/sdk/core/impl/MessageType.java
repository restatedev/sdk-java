package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;

public enum MessageType {
  StartMessage,
  CompletionMessage,
  SuspensionMessage,
  ErrorMessage,

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

  public static final short START_MESSAGE_TYPE = 0x0000;
  public static final short COMPLETION_MESSAGE_TYPE = 0x0001;
  public static final short SUSPENSION_MESSAGE_TYPE = 0x0002;
  public static final short ERROR_MESSAGE_TYPE = 0x0003;
  public static final short POLL_INPUT_STREAM_ENTRY_MESSAGE_TYPE = 0x0400;
  public static final short OUTPUT_STREAM_ENTRY_MESSAGE_TYPE = 0x0401;
  public static final short GET_STATE_ENTRY_MESSAGE_TYPE = 0x0800;
  public static final short SET_STATE_ENTRY_MESSAGE_TYPE = 0x0801;
  public static final short CLEAR_STATE_ENTRY_MESSAGE_TYPE = 0x0802;
  public static final short SLEEP_ENTRY_MESSAGE_TYPE = 0x0C00;
  public static final short INVOKE_ENTRY_MESSAGE_TYPE = 0x0C01;
  public static final short BACKGROUND_INVOKE_ENTRY_MESSAGE_TYPE = 0x0C02;
  public static final short AWAKEABLE_ENTRY_MESSAGE_TYPE = 0x0C03;
  public static final short COMPLETE_AWAKEABLE_ENTRY_MESSAGE_TYPE = 0x0C04;
  public static final short COMBINATOR_AWAITABLE_ENTRY_MESSAGE_TYPE = (short) 0xFC00;
  public static final short SIDE_EFFECT_ENTRY_MESSAGE_TYPE = (short) 0xFC01;

  public Parser<? extends MessageLite> messageParser() {
    switch (this) {
      case StartMessage:
        return Protocol.StartMessage.parser();
      case CompletionMessage:
        return Protocol.CompletionMessage.parser();
      case SuspensionMessage:
        return Protocol.SuspensionMessage.parser();
      case ErrorMessage:
        return Protocol.ErrorMessage.parser();
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
      case CombinatorAwaitableEntryMessage:
        return Java.CombinatorAwaitableEntryMessage.parser();
      case SideEffectEntryMessage:
        return Java.SideEffectEntryMessage.parser();
    }
    throw new IllegalStateException();
  }

  public short encode() {
    switch (this) {
      case StartMessage:
        return START_MESSAGE_TYPE;
      case CompletionMessage:
        return COMPLETION_MESSAGE_TYPE;
      case SuspensionMessage:
        return SUSPENSION_MESSAGE_TYPE;
      case ErrorMessage:
        return ERROR_MESSAGE_TYPE;
      case PollInputStreamEntryMessage:
        return POLL_INPUT_STREAM_ENTRY_MESSAGE_TYPE;
      case OutputStreamEntryMessage:
        return OUTPUT_STREAM_ENTRY_MESSAGE_TYPE;
      case GetStateEntryMessage:
        return GET_STATE_ENTRY_MESSAGE_TYPE;
      case SetStateEntryMessage:
        return SET_STATE_ENTRY_MESSAGE_TYPE;
      case ClearStateEntryMessage:
        return CLEAR_STATE_ENTRY_MESSAGE_TYPE;
      case SleepEntryMessage:
        return SLEEP_ENTRY_MESSAGE_TYPE;
      case InvokeEntryMessage:
        return INVOKE_ENTRY_MESSAGE_TYPE;
      case BackgroundInvokeEntryMessage:
        return BACKGROUND_INVOKE_ENTRY_MESSAGE_TYPE;
      case AwakeableEntryMessage:
        return AWAKEABLE_ENTRY_MESSAGE_TYPE;
      case CompleteAwakeableEntryMessage:
        return COMPLETE_AWAKEABLE_ENTRY_MESSAGE_TYPE;
      case CombinatorAwaitableEntryMessage:
        return COMBINATOR_AWAITABLE_ENTRY_MESSAGE_TYPE;
      case SideEffectEntryMessage:
        return SIDE_EFFECT_ENTRY_MESSAGE_TYPE;
    }
    throw new IllegalStateException();
  }

  public static MessageType decode(short value) throws ProtocolException {
    switch (value) {
      case START_MESSAGE_TYPE:
        return StartMessage;
      case COMPLETION_MESSAGE_TYPE:
        return CompletionMessage;
      case SUSPENSION_MESSAGE_TYPE:
        return SuspensionMessage;
      case ERROR_MESSAGE_TYPE:
        return ErrorMessage;
      case POLL_INPUT_STREAM_ENTRY_MESSAGE_TYPE:
        return PollInputStreamEntryMessage;
      case OUTPUT_STREAM_ENTRY_MESSAGE_TYPE:
        return OutputStreamEntryMessage;
      case GET_STATE_ENTRY_MESSAGE_TYPE:
        return GetStateEntryMessage;
      case SET_STATE_ENTRY_MESSAGE_TYPE:
        return SetStateEntryMessage;
      case CLEAR_STATE_ENTRY_MESSAGE_TYPE:
        return ClearStateEntryMessage;
      case SLEEP_ENTRY_MESSAGE_TYPE:
        return SleepEntryMessage;
      case INVOKE_ENTRY_MESSAGE_TYPE:
        return InvokeEntryMessage;
      case BACKGROUND_INVOKE_ENTRY_MESSAGE_TYPE:
        return BackgroundInvokeEntryMessage;
      case AWAKEABLE_ENTRY_MESSAGE_TYPE:
        return AwakeableEntryMessage;
      case COMPLETE_AWAKEABLE_ENTRY_MESSAGE_TYPE:
        return CompleteAwakeableEntryMessage;
      case COMBINATOR_AWAITABLE_ENTRY_MESSAGE_TYPE:
        return CombinatorAwaitableEntryMessage;
      case SIDE_EFFECT_ENTRY_MESSAGE_TYPE:
        return SideEffectEntryMessage;
    }
    throw ProtocolException.unknownMessageType(value);
  }
}
