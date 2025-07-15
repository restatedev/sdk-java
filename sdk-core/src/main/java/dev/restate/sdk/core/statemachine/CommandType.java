// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

/** Enum representing the type of command. This is used for error reporting. */
enum CommandType {
  INPUT,
  OUTPUT,
  GET_STATE,
  GET_STATE_KEYS,
  SET_STATE,
  CLEAR_STATE,
  CLEAR_ALL_STATE,
  GET_PROMISE,
  PEEK_PROMISE,
  COMPLETE_PROMISE,
  SLEEP,
  CALL,
  ONE_WAY_CALL,
  SEND_SIGNAL,
  RUN,
  ATTACH_INVOCATION,
  GET_INVOCATION_OUTPUT,
  COMPLETE_AWAKEABLE,
  CANCEL_INVOCATION;

  /** Convert a CommandType to a MessageType. */
  public MessageType toMessageType() {
    return switch (this) {
      case INPUT -> MessageType.InputCommandMessage;
      case OUTPUT -> MessageType.OutputCommandMessage;
      case GET_STATE -> MessageType.GetLazyStateCommandMessage;
      case GET_STATE_KEYS -> MessageType.GetLazyStateKeysCommandMessage;
      case SET_STATE -> MessageType.SetStateCommandMessage;
      case CLEAR_STATE -> MessageType.ClearStateCommandMessage;
      case CLEAR_ALL_STATE -> MessageType.ClearAllStateCommandMessage;
      case GET_PROMISE -> MessageType.GetPromiseCommandMessage;
      case PEEK_PROMISE -> MessageType.PeekPromiseCommandMessage;
      case COMPLETE_PROMISE -> MessageType.CompletePromiseCommandMessage;
      case SLEEP -> MessageType.SleepCommandMessage;
      case CALL -> MessageType.CallCommandMessage;
      case ONE_WAY_CALL -> MessageType.OneWayCallCommandMessage;
      case SEND_SIGNAL, CANCEL_INVOCATION -> MessageType.SendSignalCommandMessage;
      case RUN -> MessageType.RunCommandMessage;
      case ATTACH_INVOCATION -> MessageType.AttachInvocationCommandMessage;
      case GET_INVOCATION_OUTPUT -> MessageType.GetInvocationOutputCommandMessage;
      case COMPLETE_AWAKEABLE -> MessageType.CompleteAwakeableCommandMessage;
    };
  }
}
