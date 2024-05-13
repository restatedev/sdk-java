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
import com.google.protobuf.Parser;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;

public enum MessageType {
  StartMessage,
  CompletionMessage,
  SuspensionMessage,
  ErrorMessage,
  EndMessage,
  EntryAckMessage,

  // IO
  InputEntryMessage,
  OutputEntryMessage,

  // State access
  GetStateEntryMessage,
  SetStateEntryMessage,
  ClearStateEntryMessage,
  ClearAllStateEntryMessage,
  GetStateKeysEntryMessage,
  GetPromiseEntryMessage,
  PeekPromiseEntryMessage,
  CompletePromiseEntryMessage,

  // Syscalls
  SleepEntryMessage,
  CallEntryMessage,
  OneWayCallEntryMessage,
  AwakeableEntryMessage,
  CompleteAwakeableEntryMessage,
  RunEntryMessage,

  // SDK specific
  CombinatorAwaitableEntryMessage;

  public static final short START_MESSAGE_TYPE = 0x0000;
  public static final short COMPLETION_MESSAGE_TYPE = 0x0001;
  public static final short SUSPENSION_MESSAGE_TYPE = 0x0002;
  public static final short ERROR_MESSAGE_TYPE = 0x0003;
  public static final short ENTRY_ACK_MESSAGE_TYPE = 0x0004;
  public static final short END_MESSAGE_TYPE = 0x0005;
  public static final short INPUT_ENTRY_MESSAGE_TYPE = 0x0400;
  public static final short OUTPUT_ENTRY_MESSAGE_TYPE = 0x0401;
  public static final short GET_STATE_ENTRY_MESSAGE_TYPE = 0x0800;
  public static final short SET_STATE_ENTRY_MESSAGE_TYPE = 0x0801;
  public static final short CLEAR_STATE_ENTRY_MESSAGE_TYPE = 0x0802;
  public static final short CLEAR_ALL_STATE_ENTRY_MESSAGE_TYPE = 0x0803;
  public static final short GET_STATE_KEYS_ENTRY_MESSAGE_TYPE = 0x0804;
  public static final short GET_PROMISE_ENTRY_MESSAGE_TYPE = 0x0808;
  public static final short PEEK_PROMISE_ENTRY_MESSAGE_TYPE = 0x0809;
  public static final short COMPLETE_PROMISE_ENTRY_MESSAGE_TYPE = 0x080A;
  public static final short SLEEP_ENTRY_MESSAGE_TYPE = 0x0C00;
  public static final short INVOKE_ENTRY_MESSAGE_TYPE = 0x0C01;
  public static final short BACKGROUND_INVOKE_ENTRY_MESSAGE_TYPE = 0x0C02;
  public static final short AWAKEABLE_ENTRY_MESSAGE_TYPE = 0x0C03;
  public static final short COMPLETE_AWAKEABLE_ENTRY_MESSAGE_TYPE = 0x0C04;
  public static final short COMBINATOR_AWAITABLE_ENTRY_MESSAGE_TYPE = (short) 0xFC00;
  public static final short SIDE_EFFECT_ENTRY_MESSAGE_TYPE = (short) 0x0C05;

  public Parser<? extends MessageLite> messageParser() {
    switch (this) {
      case StartMessage:
        return Protocol.StartMessage.parser();
      case CompletionMessage:
        return Protocol.CompletionMessage.parser();
      case SuspensionMessage:
        return Protocol.SuspensionMessage.parser();
      case EndMessage:
        return Protocol.EndMessage.parser();
      case ErrorMessage:
        return Protocol.ErrorMessage.parser();
      case EntryAckMessage:
        return Protocol.EntryAckMessage.parser();
      case InputEntryMessage:
        return Protocol.InputEntryMessage.parser();
      case OutputEntryMessage:
        return Protocol.OutputEntryMessage.parser();
      case GetStateEntryMessage:
        return Protocol.GetStateEntryMessage.parser();
      case SetStateEntryMessage:
        return Protocol.SetStateEntryMessage.parser();
      case ClearStateEntryMessage:
        return Protocol.ClearStateEntryMessage.parser();
      case ClearAllStateEntryMessage:
        return Protocol.ClearAllStateEntryMessage.parser();
      case GetStateKeysEntryMessage:
        return Protocol.GetStateKeysEntryMessage.parser();
      case GetPromiseEntryMessage:
        return Protocol.GetPromiseEntryMessage.parser();
      case PeekPromiseEntryMessage:
        return Protocol.PeekPromiseEntryMessage.parser();
      case CompletePromiseEntryMessage:
        return Protocol.CompletePromiseEntryMessage.parser();
      case SleepEntryMessage:
        return Protocol.SleepEntryMessage.parser();
      case CallEntryMessage:
        return Protocol.CallEntryMessage.parser();
      case OneWayCallEntryMessage:
        return Protocol.OneWayCallEntryMessage.parser();
      case AwakeableEntryMessage:
        return Protocol.AwakeableEntryMessage.parser();
      case CompleteAwakeableEntryMessage:
        return Protocol.CompleteAwakeableEntryMessage.parser();
      case CombinatorAwaitableEntryMessage:
        return Java.CombinatorAwaitableEntryMessage.parser();
      case RunEntryMessage:
        return Protocol.RunEntryMessage.parser();
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
      case EndMessage:
        return END_MESSAGE_TYPE;
      case ErrorMessage:
        return ERROR_MESSAGE_TYPE;
      case EntryAckMessage:
        return ENTRY_ACK_MESSAGE_TYPE;
      case InputEntryMessage:
        return INPUT_ENTRY_MESSAGE_TYPE;
      case OutputEntryMessage:
        return OUTPUT_ENTRY_MESSAGE_TYPE;
      case GetStateEntryMessage:
        return GET_STATE_ENTRY_MESSAGE_TYPE;
      case SetStateEntryMessage:
        return SET_STATE_ENTRY_MESSAGE_TYPE;
      case ClearStateEntryMessage:
        return CLEAR_STATE_ENTRY_MESSAGE_TYPE;
      case ClearAllStateEntryMessage:
        return CLEAR_ALL_STATE_ENTRY_MESSAGE_TYPE;
      case GetStateKeysEntryMessage:
        return GET_STATE_KEYS_ENTRY_MESSAGE_TYPE;
      case GetPromiseEntryMessage:
        return GET_PROMISE_ENTRY_MESSAGE_TYPE;
      case PeekPromiseEntryMessage:
        return PEEK_PROMISE_ENTRY_MESSAGE_TYPE;
      case CompletePromiseEntryMessage:
        return COMPLETE_PROMISE_ENTRY_MESSAGE_TYPE;
      case SleepEntryMessage:
        return SLEEP_ENTRY_MESSAGE_TYPE;
      case CallEntryMessage:
        return INVOKE_ENTRY_MESSAGE_TYPE;
      case OneWayCallEntryMessage:
        return BACKGROUND_INVOKE_ENTRY_MESSAGE_TYPE;
      case AwakeableEntryMessage:
        return AWAKEABLE_ENTRY_MESSAGE_TYPE;
      case CompleteAwakeableEntryMessage:
        return COMPLETE_AWAKEABLE_ENTRY_MESSAGE_TYPE;
      case CombinatorAwaitableEntryMessage:
        return COMBINATOR_AWAITABLE_ENTRY_MESSAGE_TYPE;
      case RunEntryMessage:
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
      case END_MESSAGE_TYPE:
        return EndMessage;
      case ERROR_MESSAGE_TYPE:
        return ErrorMessage;
      case ENTRY_ACK_MESSAGE_TYPE:
        return EntryAckMessage;
      case INPUT_ENTRY_MESSAGE_TYPE:
        return InputEntryMessage;
      case OUTPUT_ENTRY_MESSAGE_TYPE:
        return OutputEntryMessage;
      case GET_STATE_ENTRY_MESSAGE_TYPE:
        return GetStateEntryMessage;
      case SET_STATE_ENTRY_MESSAGE_TYPE:
        return SetStateEntryMessage;
      case CLEAR_STATE_ENTRY_MESSAGE_TYPE:
        return ClearStateEntryMessage;
      case CLEAR_ALL_STATE_ENTRY_MESSAGE_TYPE:
        return ClearAllStateEntryMessage;
      case GET_STATE_KEYS_ENTRY_MESSAGE_TYPE:
        return GetStateKeysEntryMessage;
      case GET_PROMISE_ENTRY_MESSAGE_TYPE:
        return GetPromiseEntryMessage;
      case PEEK_PROMISE_ENTRY_MESSAGE_TYPE:
        return PeekPromiseEntryMessage;
      case COMPLETE_PROMISE_ENTRY_MESSAGE_TYPE:
        return CompletePromiseEntryMessage;
      case SLEEP_ENTRY_MESSAGE_TYPE:
        return SleepEntryMessage;
      case INVOKE_ENTRY_MESSAGE_TYPE:
        return CallEntryMessage;
      case BACKGROUND_INVOKE_ENTRY_MESSAGE_TYPE:
        return OneWayCallEntryMessage;
      case AWAKEABLE_ENTRY_MESSAGE_TYPE:
        return AwakeableEntryMessage;
      case COMPLETE_AWAKEABLE_ENTRY_MESSAGE_TYPE:
        return CompleteAwakeableEntryMessage;
      case COMBINATOR_AWAITABLE_ENTRY_MESSAGE_TYPE:
        return CombinatorAwaitableEntryMessage;
      case SIDE_EFFECT_ENTRY_MESSAGE_TYPE:
        return RunEntryMessage;
    }
    throw ProtocolException.unknownMessageType(value);
  }

  public static MessageType fromMessage(MessageLite msg) {
    if (msg instanceof Protocol.SuspensionMessage) {
      return MessageType.SuspensionMessage;
    } else if (msg instanceof Protocol.ErrorMessage) {
      return MessageType.ErrorMessage;
    } else if (msg instanceof Protocol.EndMessage) {
      return MessageType.EndMessage;
    } else if (msg instanceof Protocol.EntryAckMessage) {
      return MessageType.EntryAckMessage;
    } else if (msg instanceof Protocol.InputEntryMessage) {
      return MessageType.InputEntryMessage;
    } else if (msg instanceof Protocol.OutputEntryMessage) {
      return MessageType.OutputEntryMessage;
    } else if (msg instanceof Protocol.GetStateEntryMessage) {
      return MessageType.GetStateEntryMessage;
    } else if (msg instanceof Protocol.SetStateEntryMessage) {
      return MessageType.SetStateEntryMessage;
    } else if (msg instanceof Protocol.ClearStateEntryMessage) {
      return MessageType.ClearStateEntryMessage;
    } else if (msg instanceof Protocol.ClearAllStateEntryMessage) {
      return MessageType.ClearAllStateEntryMessage;
    } else if (msg instanceof Protocol.GetStateKeysEntryMessage) {
      return MessageType.GetStateKeysEntryMessage;
    } else if (msg instanceof Protocol.GetPromiseEntryMessage) {
      return MessageType.GetPromiseEntryMessage;
    } else if (msg instanceof Protocol.PeekPromiseEntryMessage) {
      return MessageType.PeekPromiseEntryMessage;
    } else if (msg instanceof Protocol.CompletePromiseEntryMessage) {
      return MessageType.CompletePromiseEntryMessage;
    } else if (msg instanceof Protocol.SleepEntryMessage) {
      return MessageType.SleepEntryMessage;
    } else if (msg instanceof Protocol.CallEntryMessage) {
      return MessageType.CallEntryMessage;
    } else if (msg instanceof Protocol.OneWayCallEntryMessage) {
      return MessageType.OneWayCallEntryMessage;
    } else if (msg instanceof Protocol.AwakeableEntryMessage) {
      return MessageType.AwakeableEntryMessage;
    } else if (msg instanceof Protocol.CompleteAwakeableEntryMessage) {
      return MessageType.CompleteAwakeableEntryMessage;
    } else if (msg instanceof Java.CombinatorAwaitableEntryMessage) {
      return MessageType.CombinatorAwaitableEntryMessage;
    } else if (msg instanceof Protocol.RunEntryMessage) {
      return MessageType.RunEntryMessage;
    } else if (msg instanceof Protocol.CompletionMessage) {
      throw new IllegalArgumentException("SDK should never send a CompletionMessage");
    }
    throw new IllegalStateException();
  }
}
