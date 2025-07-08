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
    switch (this) {
      case INPUT:
        return MessageType.InputCommandMessage;
      case OUTPUT:
        return MessageType.OutputCommandMessage;
      case GET_STATE:
        return MessageType.GetLazyStateCommandMessage;
      case GET_STATE_KEYS:
        return MessageType.GetLazyStateKeysCommandMessage;
      case SET_STATE:
        return MessageType.SetStateCommandMessage;
      case CLEAR_STATE:
        return MessageType.ClearStateCommandMessage;
      case CLEAR_ALL_STATE:
        return MessageType.ClearAllStateCommandMessage;
      case GET_PROMISE:
        return MessageType.GetPromiseCommandMessage;
      case PEEK_PROMISE:
        return MessageType.PeekPromiseCommandMessage;
      case COMPLETE_PROMISE:
        return MessageType.CompletePromiseCommandMessage;
      case SLEEP:
        return MessageType.SleepCommandMessage;
      case CALL:
        return MessageType.CallCommandMessage;
      case ONE_WAY_CALL:
        return MessageType.OneWayCallCommandMessage;
      case SEND_SIGNAL:
      case CANCEL_INVOCATION:
        return MessageType.SendSignalCommandMessage;
      case RUN:
        return MessageType.RunCommandMessage;
      case ATTACH_INVOCATION:
        return MessageType.AttachInvocationCommandMessage;
      case GET_INVOCATION_OUTPUT:
        return MessageType.GetInvocationOutputCommandMessage;
      case COMPLETE_AWAKEABLE:
        return MessageType.CompleteAwakeableCommandMessage;
      default:
        throw new IllegalStateException("Unexpected command type: " + this);
    }
  }
}
