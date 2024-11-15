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
    return switch (this) {
      case StartMessage -> Protocol.StartMessage.parser();
      case CompletionMessage -> Protocol.CompletionMessage.parser();
      case SuspensionMessage -> Protocol.SuspensionMessage.parser();
      case EndMessage -> Protocol.EndMessage.parser();
      case ErrorMessage -> Protocol.ErrorMessage.parser();
      case EntryAckMessage -> Protocol.EntryAckMessage.parser();
      case InputEntryMessage -> Protocol.InputEntryMessage.parser();
      case OutputEntryMessage -> Protocol.OutputEntryMessage.parser();
      case GetStateEntryMessage -> Protocol.GetStateEntryMessage.parser();
      case SetStateEntryMessage -> Protocol.SetStateEntryMessage.parser();
      case ClearStateEntryMessage -> Protocol.ClearStateEntryMessage.parser();
      case ClearAllStateEntryMessage -> Protocol.ClearAllStateEntryMessage.parser();
      case GetStateKeysEntryMessage -> Protocol.GetStateKeysEntryMessage.parser();
      case GetPromiseEntryMessage -> Protocol.GetPromiseEntryMessage.parser();
      case PeekPromiseEntryMessage -> Protocol.PeekPromiseEntryMessage.parser();
      case CompletePromiseEntryMessage -> Protocol.CompletePromiseEntryMessage.parser();
      case SleepEntryMessage -> Protocol.SleepEntryMessage.parser();
      case CallEntryMessage -> Protocol.CallEntryMessage.parser();
      case OneWayCallEntryMessage -> Protocol.OneWayCallEntryMessage.parser();
      case AwakeableEntryMessage -> Protocol.AwakeableEntryMessage.parser();
      case CompleteAwakeableEntryMessage -> Protocol.CompleteAwakeableEntryMessage.parser();
      case CombinatorAwaitableEntryMessage -> Java.CombinatorAwaitableEntryMessage.parser();
      case RunEntryMessage -> Protocol.RunEntryMessage.parser();
    };
  }

  public short encode() {
    return switch (this) {
      case StartMessage -> START_MESSAGE_TYPE;
      case CompletionMessage -> COMPLETION_MESSAGE_TYPE;
      case SuspensionMessage -> SUSPENSION_MESSAGE_TYPE;
      case EndMessage -> END_MESSAGE_TYPE;
      case ErrorMessage -> ERROR_MESSAGE_TYPE;
      case EntryAckMessage -> ENTRY_ACK_MESSAGE_TYPE;
      case InputEntryMessage -> INPUT_ENTRY_MESSAGE_TYPE;
      case OutputEntryMessage -> OUTPUT_ENTRY_MESSAGE_TYPE;
      case GetStateEntryMessage -> GET_STATE_ENTRY_MESSAGE_TYPE;
      case SetStateEntryMessage -> SET_STATE_ENTRY_MESSAGE_TYPE;
      case ClearStateEntryMessage -> CLEAR_STATE_ENTRY_MESSAGE_TYPE;
      case ClearAllStateEntryMessage -> CLEAR_ALL_STATE_ENTRY_MESSAGE_TYPE;
      case GetStateKeysEntryMessage -> GET_STATE_KEYS_ENTRY_MESSAGE_TYPE;
      case GetPromiseEntryMessage -> GET_PROMISE_ENTRY_MESSAGE_TYPE;
      case PeekPromiseEntryMessage -> PEEK_PROMISE_ENTRY_MESSAGE_TYPE;
      case CompletePromiseEntryMessage -> COMPLETE_PROMISE_ENTRY_MESSAGE_TYPE;
      case SleepEntryMessage -> SLEEP_ENTRY_MESSAGE_TYPE;
      case CallEntryMessage -> INVOKE_ENTRY_MESSAGE_TYPE;
      case OneWayCallEntryMessage -> BACKGROUND_INVOKE_ENTRY_MESSAGE_TYPE;
      case AwakeableEntryMessage -> AWAKEABLE_ENTRY_MESSAGE_TYPE;
      case CompleteAwakeableEntryMessage -> COMPLETE_AWAKEABLE_ENTRY_MESSAGE_TYPE;
      case CombinatorAwaitableEntryMessage -> COMBINATOR_AWAITABLE_ENTRY_MESSAGE_TYPE;
      case RunEntryMessage -> SIDE_EFFECT_ENTRY_MESSAGE_TYPE;
    };
  }

  public static MessageType decode(short value) throws ProtocolException {
    return switch (value) {
      case START_MESSAGE_TYPE -> StartMessage;
      case COMPLETION_MESSAGE_TYPE -> CompletionMessage;
      case SUSPENSION_MESSAGE_TYPE -> SuspensionMessage;
      case END_MESSAGE_TYPE -> EndMessage;
      case ERROR_MESSAGE_TYPE -> ErrorMessage;
      case ENTRY_ACK_MESSAGE_TYPE -> EntryAckMessage;
      case INPUT_ENTRY_MESSAGE_TYPE -> InputEntryMessage;
      case OUTPUT_ENTRY_MESSAGE_TYPE -> OutputEntryMessage;
      case GET_STATE_ENTRY_MESSAGE_TYPE -> GetStateEntryMessage;
      case SET_STATE_ENTRY_MESSAGE_TYPE -> SetStateEntryMessage;
      case CLEAR_STATE_ENTRY_MESSAGE_TYPE -> ClearStateEntryMessage;
      case CLEAR_ALL_STATE_ENTRY_MESSAGE_TYPE -> ClearAllStateEntryMessage;
      case GET_STATE_KEYS_ENTRY_MESSAGE_TYPE -> GetStateKeysEntryMessage;
      case GET_PROMISE_ENTRY_MESSAGE_TYPE -> GetPromiseEntryMessage;
      case PEEK_PROMISE_ENTRY_MESSAGE_TYPE -> PeekPromiseEntryMessage;
      case COMPLETE_PROMISE_ENTRY_MESSAGE_TYPE -> CompletePromiseEntryMessage;
      case SLEEP_ENTRY_MESSAGE_TYPE -> SleepEntryMessage;
      case INVOKE_ENTRY_MESSAGE_TYPE -> CallEntryMessage;
      case BACKGROUND_INVOKE_ENTRY_MESSAGE_TYPE -> OneWayCallEntryMessage;
      case AWAKEABLE_ENTRY_MESSAGE_TYPE -> AwakeableEntryMessage;
      case COMPLETE_AWAKEABLE_ENTRY_MESSAGE_TYPE -> CompleteAwakeableEntryMessage;
      case COMBINATOR_AWAITABLE_ENTRY_MESSAGE_TYPE -> CombinatorAwaitableEntryMessage;
      case SIDE_EFFECT_ENTRY_MESSAGE_TYPE -> RunEntryMessage;
      default -> throw ProtocolException.unknownMessageType(value);
    };
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
