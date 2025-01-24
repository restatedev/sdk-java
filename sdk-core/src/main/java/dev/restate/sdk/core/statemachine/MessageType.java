// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;

public enum MessageType {
  StartMessage,
  SuspensionMessage,
  ErrorMessage,
  EndMessage,
  ProposeRunCompletionMessage,
  
  InputCommandMessage,
  OutputCommandMessage,
  GetLazyStateCommandMessage,
  GetLazyStateCompletionNotificationMessage,
  SetStateCommandMessage,
  ClearStateCommandMessage,
  ClearAllStateCommandMessage,
  GetLazyStateKeysCommandMessage,
  GetLazyStateKeysCompletionNotificationMessage,
  GetEagerStateCommandMessage,
  GetEagerStateKeysCommandMessage,
  GetPromiseCommandMessage,
  GetPromiseCompletionNotificationMessage,
  PeekPromiseCommandMessage,
  PeekPromiseCompletionNotificationMessage,
  CompletePromiseCommandMessage,
  CompletePromiseCompletionNotificationMessage,
  SleepCommandMessage,
  SleepCompletionNotificationMessage,
  CallCommandMessage,
  CallInvocationIdCompletionNotificationMessage,
  CallCompletionNotificationMessage,
  OneWayCallCommandMessage,
  SendSignalCommandMessage,
  RunCommandMessage,
  RunCompletionNotificationMessage,
  AttachInvocationCommandMessage,
  AttachInvocationCompletionNotificationMessage,
  GetInvocationOutputCommandMessage,
  GetInvocationOutputCompletionNotificationMessage,
  CompleteAwakeableCommandMessage,
  SignalNotificationMessage;

  public static final short StartMessage_TYPE = (short) 0x0000;
  public static final short SuspensionMessage_TYPE = (short) 0x0001;
  public static final short ErrorMessage_TYPE = (short) 0x0002;
  public static final short EndMessage_TYPE = (short) 0x0003;
  public static final short ProposeRunCompletionMessage_TYPE = (short) 0x0005;
  public static final short InputCommandMessage_TYPE = (short) 0x0400;
  public static final short OutputCommandMessage_TYPE = (short) 0x0401;
  public static final short GetLazyStateCommandMessage_TYPE = (short) 0x0402;
  public static final short GetLazyStateCompletionNotificationMessage_TYPE = (short) 0x8002;
  public static final short SetStateCommandMessage_TYPE = (short) 0x0403;
  public static final short ClearStateCommandMessage_TYPE = (short) 0x0404;
  public static final short ClearAllStateCommandMessage_TYPE = (short) 0x0405;
  public static final short GetLazyStateKeysCommandMessage_TYPE = (short) 0x0406;
  public static final short GetLazyStateKeysCompletionNotificationMessage_TYPE = (short) 0x8006;
  public static final short GetEagerStateCommandMessage_TYPE = (short) 0x0407;
  public static final short GetEagerStateKeysCommandMessage_TYPE = (short) 0x0408;
  public static final short GetPromiseCommandMessage_TYPE = (short) 0x0409;
  public static final short GetPromiseCompletionNotificationMessage_TYPE = (short) 0x8009;
  public static final short PeekPromiseCommandMessage_TYPE = (short) 0x040A;
  public static final short PeekPromiseCompletionNotificationMessage_TYPE = (short) 0x800A;
  public static final short CompletePromiseCommandMessage_TYPE = (short) 0x040B;
  public static final short CompletePromiseCompletionNotificationMessage_TYPE = (short) 0x800B;
  public static final short SleepCommandMessage_TYPE = (short) 0x040C;
  public static final short SleepCompletionNotificationMessage_TYPE = (short) 0x800C;
  public static final short CallCommandMessage_TYPE = (short) 0x040D;
  public static final short CallInvocationIdCompletionNotificationMessage_TYPE = (short) 0x800E;
  public static final short CallCompletionNotificationMessage_TYPE = (short) 0x800D;
  public static final short OneWayCallCommandMessage_TYPE = (short) 0x040E;
  public static final short SendSignalCommandMessage_TYPE = (short) 0x0410;
  public static final short RunCommandMessage_TYPE = (short) 0x0411;
  public static final short RunCompletionNotificationMessage_TYPE = (short) 0x8011;
  public static final short AttachInvocationCommandMessage_TYPE = (short) 0x0412;
  public static final short AttachInvocationCompletionNotificationMessage_TYPE = (short) 0x8012;
  public static final short GetInvocationOutputCommandMessage_TYPE = (short) 0x0413;
  public static final short GetInvocationOutputCompletionNotificationMessage_TYPE = (short) 0x8013;
  public static final short CompleteAwakeableCommandMessage_TYPE = (short) 0x0414;
  public static final short SignalNotificationMessage_TYPE = (short) 0xFBFF;

  public Parser<? extends MessageLite> messageParser() {
    return switch (this) {
      case StartMessage -> Protocol.StartMessage.parser();
              case SuspensionMessage -> Protocol.SuspensionMessage.parser();
              case ErrorMessage -> Protocol.ErrorMessage.parser();
              case EndMessage -> Protocol.EndMessage.parser();
              case ProposeRunCompletionMessage -> Protocol.ProposeRunCompletionMessage.parser();
              case InputCommandMessage -> Protocol.InputCommandMessage.parser();
              case OutputCommandMessage -> Protocol.OutputCommandMessage.parser();
              case GetLazyStateCommandMessage -> Protocol.GetLazyStateCommandMessage.parser();
              case SetStateCommandMessage -> Protocol.SetStateCommandMessage.parser();
              case ClearStateCommandMessage -> Protocol.ClearStateCommandMessage.parser();
              case ClearAllStateCommandMessage -> Protocol.ClearAllStateCommandMessage.parser();
              case GetLazyStateKeysCommandMessage -> Protocol.GetLazyStateKeysCommandMessage.parser();
              case GetEagerStateCommandMessage -> Protocol.GetEagerStateCommandMessage.parser();
              case GetEagerStateKeysCommandMessage -> Protocol.GetEagerStateKeysCommandMessage.parser();
              case GetPromiseCommandMessage -> Protocol.GetPromiseCommandMessage.parser();
              case PeekPromiseCommandMessage -> Protocol.PeekPromiseCommandMessage.parser();
              case CompletePromiseCommandMessage -> Protocol.CompletePromiseCommandMessage.parser();
              case SleepCommandMessage -> Protocol.SleepCommandMessage.parser();
              case CallCommandMessage -> Protocol.CallCommandMessage.parser();
              case OneWayCallCommandMessage -> Protocol.OneWayCallCommandMessage.parser();
              case SendSignalCommandMessage -> Protocol.SendSignalCommandMessage.parser();
              case RunCommandMessage -> Protocol.RunCommandMessage.parser();
              case AttachInvocationCommandMessage -> Protocol.AttachInvocationCommandMessage.parser();
              case GetInvocationOutputCommandMessage -> Protocol.GetInvocationOutputCommandMessage.parser();
             case CompleteAwakeableCommandMessage -> Protocol.CompleteAwakeableCommandMessage.parser();
      case GetLazyStateCompletionNotificationMessage, SignalNotificationMessage,
           GetLazyStateKeysCompletionNotificationMessage, GetPromiseCompletionNotificationMessage,
           PeekPromiseCompletionNotificationMessage, CompletePromiseCompletionNotificationMessage,
           SleepCompletionNotificationMessage, CallInvocationIdCompletionNotificationMessage,
           CallCompletionNotificationMessage, RunCompletionNotificationMessage,
           AttachInvocationCompletionNotificationMessage, GetInvocationOutputCompletionNotificationMessage -> Protocol.NotificationTemplate.parser();
    };
  }

  public short encode() {
    return switch (this) {
      case StartMessage -> StartMessage_TYPE;
              case SuspensionMessage -> SuspensionMessage_TYPE;
              case ErrorMessage -> ErrorMessage_TYPE;
              case EndMessage -> EndMessage_TYPE;
              case ProposeRunCompletionMessage -> ProposeRunCompletionMessage_TYPE;
              case InputCommandMessage -> InputCommandMessage_TYPE;
              case OutputCommandMessage -> OutputCommandMessage_TYPE;
              case GetLazyStateCommandMessage -> GetLazyStateCommandMessage_TYPE;
              case GetLazyStateCompletionNotificationMessage -> GetLazyStateCompletionNotificationMessage_TYPE;
              case SetStateCommandMessage -> SetStateCommandMessage_TYPE;
              case ClearStateCommandMessage -> ClearStateCommandMessage_TYPE;
              case ClearAllStateCommandMessage -> ClearAllStateCommandMessage_TYPE;
              case GetLazyStateKeysCommandMessage -> GetLazyStateKeysCommandMessage_TYPE;
              case GetLazyStateKeysCompletionNotificationMessage -> GetLazyStateKeysCompletionNotificationMessage_TYPE;
              case GetEagerStateCommandMessage -> GetEagerStateCommandMessage_TYPE;
              case GetEagerStateKeysCommandMessage -> GetEagerStateKeysCommandMessage_TYPE;
              case GetPromiseCommandMessage -> GetPromiseCommandMessage_TYPE;
              case GetPromiseCompletionNotificationMessage -> GetPromiseCompletionNotificationMessage_TYPE;
              case PeekPromiseCommandMessage -> PeekPromiseCommandMessage_TYPE;
              case PeekPromiseCompletionNotificationMessage -> PeekPromiseCompletionNotificationMessage_TYPE;
              case CompletePromiseCommandMessage -> CompletePromiseCommandMessage_TYPE;
              case CompletePromiseCompletionNotificationMessage -> CompletePromiseCompletionNotificationMessage_TYPE;
              case SleepCommandMessage -> SleepCommandMessage_TYPE;
              case SleepCompletionNotificationMessage -> SleepCompletionNotificationMessage_TYPE;
              case CallCommandMessage -> CallCommandMessage_TYPE;
              case CallInvocationIdCompletionNotificationMessage -> CallInvocationIdCompletionNotificationMessage_TYPE;
              case CallCompletionNotificationMessage -> CallCompletionNotificationMessage_TYPE;
              case OneWayCallCommandMessage -> OneWayCallCommandMessage_TYPE;
              case SendSignalCommandMessage -> SendSignalCommandMessage_TYPE;
              case RunCommandMessage -> RunCommandMessage_TYPE;
              case RunCompletionNotificationMessage -> RunCompletionNotificationMessage_TYPE;
              case AttachInvocationCommandMessage -> AttachInvocationCommandMessage_TYPE;
              case AttachInvocationCompletionNotificationMessage -> AttachInvocationCompletionNotificationMessage_TYPE;
              case GetInvocationOutputCommandMessage -> GetInvocationOutputCommandMessage_TYPE;
              case GetInvocationOutputCompletionNotificationMessage -> GetInvocationOutputCompletionNotificationMessage_TYPE;
              case CompleteAwakeableCommandMessage -> CompleteAwakeableCommandMessage_TYPE;
              case SignalNotificationMessage -> SignalNotificationMessage_TYPE;
    };
  }

  public boolean isCommand() {
    return switch (this) {
        case InputCommandMessage, GetLazyStateCommandMessage, OutputCommandMessage, SetStateCommandMessage,
             ClearStateCommandMessage, ClearAllStateCommandMessage, GetLazyStateKeysCommandMessage,
             GetEagerStateCommandMessage, GetEagerStateKeysCommandMessage, GetPromiseCommandMessage,
             PeekPromiseCommandMessage, CompletePromiseCommandMessage, SleepCommandMessage, CallCommandMessage,
             OneWayCallCommandMessage, SendSignalCommandMessage, RunCommandMessage, AttachInvocationCommandMessage,
             GetInvocationOutputCommandMessage, CompleteAwakeableCommandMessage -> true;
        default -> false;
    };
  }

  public boolean isNotification() {
    return switch (this) {
        case GetLazyStateCompletionNotificationMessage, SignalNotificationMessage,
             GetLazyStateKeysCompletionNotificationMessage, GetPromiseCompletionNotificationMessage,
             PeekPromiseCompletionNotificationMessage, CompletePromiseCompletionNotificationMessage,
             SleepCompletionNotificationMessage, CallInvocationIdCompletionNotificationMessage,
             CallCompletionNotificationMessage, RunCompletionNotificationMessage,
             AttachInvocationCompletionNotificationMessage, GetInvocationOutputCompletionNotificationMessage -> true;
        default -> false;
    };
  }

  public static MessageType decode(short value) throws ProtocolException {
    return switch (value) {
      case StartMessage_TYPE -> StartMessage;
              case SuspensionMessage_TYPE -> SuspensionMessage;
              case ErrorMessage_TYPE -> ErrorMessage;
              case EndMessage_TYPE -> EndMessage;
              case ProposeRunCompletionMessage_TYPE -> ProposeRunCompletionMessage;
              case InputCommandMessage_TYPE -> InputCommandMessage;
              case OutputCommandMessage_TYPE -> OutputCommandMessage;
              case GetLazyStateCommandMessage_TYPE -> GetLazyStateCommandMessage;
              case GetLazyStateCompletionNotificationMessage_TYPE -> GetLazyStateCompletionNotificationMessage;
              case SetStateCommandMessage_TYPE -> SetStateCommandMessage;
              case ClearStateCommandMessage_TYPE -> ClearStateCommandMessage;
              case ClearAllStateCommandMessage_TYPE -> ClearAllStateCommandMessage;
              case GetLazyStateKeysCommandMessage_TYPE -> GetLazyStateKeysCommandMessage;
              case GetLazyStateKeysCompletionNotificationMessage_TYPE -> GetLazyStateKeysCompletionNotificationMessage;
              case GetEagerStateCommandMessage_TYPE -> GetEagerStateCommandMessage;
              case GetEagerStateKeysCommandMessage_TYPE -> GetEagerStateKeysCommandMessage;
              case GetPromiseCommandMessage_TYPE -> GetPromiseCommandMessage;
              case GetPromiseCompletionNotificationMessage_TYPE -> GetPromiseCompletionNotificationMessage;
              case PeekPromiseCommandMessage_TYPE -> PeekPromiseCommandMessage;
              case PeekPromiseCompletionNotificationMessage_TYPE -> PeekPromiseCompletionNotificationMessage;
              case CompletePromiseCommandMessage_TYPE -> CompletePromiseCommandMessage;
              case CompletePromiseCompletionNotificationMessage_TYPE -> CompletePromiseCompletionNotificationMessage;
              case SleepCommandMessage_TYPE -> SleepCommandMessage;
              case SleepCompletionNotificationMessage_TYPE -> SleepCompletionNotificationMessage;
              case CallCommandMessage_TYPE -> CallCommandMessage;
              case CallInvocationIdCompletionNotificationMessage_TYPE -> CallInvocationIdCompletionNotificationMessage;
              case CallCompletionNotificationMessage_TYPE -> CallCompletionNotificationMessage;
              case OneWayCallCommandMessage_TYPE -> OneWayCallCommandMessage;
              case SendSignalCommandMessage_TYPE -> SendSignalCommandMessage;
              case RunCommandMessage_TYPE -> RunCommandMessage;
              case RunCompletionNotificationMessage_TYPE -> RunCompletionNotificationMessage;
              case AttachInvocationCommandMessage_TYPE -> AttachInvocationCommandMessage;
              case AttachInvocationCompletionNotificationMessage_TYPE -> AttachInvocationCompletionNotificationMessage;
              case GetInvocationOutputCommandMessage_TYPE -> GetInvocationOutputCommandMessage;
              case GetInvocationOutputCompletionNotificationMessage_TYPE -> GetInvocationOutputCompletionNotificationMessage;
              case CompleteAwakeableCommandMessage_TYPE -> CompleteAwakeableCommandMessage;
              case SignalNotificationMessage_TYPE -> SignalNotificationMessage;
      default -> throw ProtocolException.unknownMessageType(value);
    };
  }

  public static MessageType fromMessage(MessageLite msg) {
    if (msg instanceof Protocol.StartMessage) { return MessageType.StartMessage; } else 
            if (msg instanceof Protocol.SuspensionMessage) { return MessageType.SuspensionMessage; } else 
            if (msg instanceof Protocol.ErrorMessage) { return MessageType.ErrorMessage; } else 
            if (msg instanceof Protocol.EndMessage) { return MessageType.EndMessage; } else 
            if (msg instanceof Protocol.ProposeRunCompletionMessage) { return MessageType.ProposeRunCompletionMessage; } else 
            if (msg instanceof Protocol.InputCommandMessage) { return MessageType.InputCommandMessage; } else 
            if (msg instanceof Protocol.OutputCommandMessage) { return MessageType.OutputCommandMessage; } else 
            if (msg instanceof Protocol.GetLazyStateCommandMessage) { return MessageType.GetLazyStateCommandMessage; } else 
            if (msg instanceof Protocol.GetLazyStateCompletionNotificationMessage) { return MessageType.GetLazyStateCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.SetStateCommandMessage) { return MessageType.SetStateCommandMessage; } else 
            if (msg instanceof Protocol.ClearStateCommandMessage) { return MessageType.ClearStateCommandMessage; } else 
            if (msg instanceof Protocol.ClearAllStateCommandMessage) { return MessageType.ClearAllStateCommandMessage; } else 
            if (msg instanceof Protocol.GetLazyStateKeysCommandMessage) { return MessageType.GetLazyStateKeysCommandMessage; } else 
            if (msg instanceof Protocol.GetLazyStateKeysCompletionNotificationMessage) { return MessageType.GetLazyStateKeysCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.GetEagerStateCommandMessage) { return MessageType.GetEagerStateCommandMessage; } else 
            if (msg instanceof Protocol.GetEagerStateKeysCommandMessage) { return MessageType.GetEagerStateKeysCommandMessage; } else 
            if (msg instanceof Protocol.GetPromiseCommandMessage) { return MessageType.GetPromiseCommandMessage; } else 
            if (msg instanceof Protocol.GetPromiseCompletionNotificationMessage) { return MessageType.GetPromiseCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.PeekPromiseCommandMessage) { return MessageType.PeekPromiseCommandMessage; } else 
            if (msg instanceof Protocol.PeekPromiseCompletionNotificationMessage) { return MessageType.PeekPromiseCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.CompletePromiseCommandMessage) { return MessageType.CompletePromiseCommandMessage; } else 
            if (msg instanceof Protocol.CompletePromiseCompletionNotificationMessage) { return MessageType.CompletePromiseCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.SleepCommandMessage) { return MessageType.SleepCommandMessage; } else 
            if (msg instanceof Protocol.SleepCompletionNotificationMessage) { return MessageType.SleepCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.CallCommandMessage) { return MessageType.CallCommandMessage; } else 
            if (msg instanceof Protocol.CallInvocationIdCompletionNotificationMessage) { return MessageType.CallInvocationIdCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.CallCompletionNotificationMessage) { return MessageType.CallCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.OneWayCallCommandMessage) { return MessageType.OneWayCallCommandMessage; } else 
            if (msg instanceof Protocol.SendSignalCommandMessage) { return MessageType.SendSignalCommandMessage; } else 
            if (msg instanceof Protocol.RunCommandMessage) { return MessageType.RunCommandMessage; } else 
            if (msg instanceof Protocol.RunCompletionNotificationMessage) { return MessageType.RunCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.AttachInvocationCommandMessage) { return MessageType.AttachInvocationCommandMessage; } else 
            if (msg instanceof Protocol.AttachInvocationCompletionNotificationMessage) { return MessageType.AttachInvocationCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.GetInvocationOutputCommandMessage) { return MessageType.GetInvocationOutputCommandMessage; } else 
            if (msg instanceof Protocol.GetInvocationOutputCompletionNotificationMessage) { return MessageType.GetInvocationOutputCompletionNotificationMessage; } else 
            if (msg instanceof Protocol.CompleteAwakeableCommandMessage) { return MessageType.CompleteAwakeableCommandMessage; } else 
            if (msg instanceof Protocol.SignalNotificationMessage) { return MessageType.SignalNotificationMessage; }

    throw new IllegalStateException("Unexpected protobuf message");
  }
}
