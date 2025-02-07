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
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;

import java.util.Objects;

interface CommandAccessor<E extends MessageLite> {

  String getName(E expected);

  default void checkEntryHeader(E expected, MessageLite actual) throws ProtocolException {
    Util.assertEntryEquals(expected, actual);
  }

  CommandAccessor<Protocol.InputCommandMessage> INPUT = new CommandAccessor<>() {
      @Override
      public String getName(Protocol.InputCommandMessage expected) {
          return "";
      }

      @Override
      public void checkEntryHeader(Protocol.InputCommandMessage expected, MessageLite actual) throws ProtocolException {
          // Nothing to check
      }
  };
  CommandAccessor<Protocol.OutputCommandMessage> OUTPUT = Protocol.OutputCommandMessage::getName;
  CommandAccessor<Protocol.GetEagerStateCommandMessage> GET_EAGER_STATE =
      new CommandAccessor<>() {
        @Override
        public void checkEntryHeader(
            Protocol.GetEagerStateCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          Util.assertEntryClass(Protocol.GetEagerStateCommandMessage.class, actual);
          if (!expected.getKey().equals(((Protocol.GetEagerStateCommandMessage) actual).getKey())) {
            throw ProtocolException.commandDoesNotMatch(expected, actual);
          }
        }

        @Override
        public String getName(Protocol.GetEagerStateCommandMessage expected) {
          return expected.getName();
        }
      };
  CommandAccessor<Protocol.GetLazyStateCommandMessage> GET_LAZY_STATE =
      Protocol.GetLazyStateCommandMessage::getName;
  CommandAccessor<Protocol.GetEagerStateKeysCommandMessage> GET_EAGER_STATE_KEYS =
      new CommandAccessor<>() {
        @Override
        public void checkEntryHeader(
            Protocol.GetEagerStateKeysCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          Util.assertEntryClass(Protocol.GetEagerStateKeysCommandMessage.class, actual);
        }

        @Override
        public String getName(Protocol.GetEagerStateKeysCommandMessage expected) {
          return expected.getName();
        }
      };
  CommandAccessor<Protocol.GetLazyStateKeysCommandMessage> GET_LAZY_STATE_KEYS =
      Protocol.GetLazyStateKeysCommandMessage::getName;
  CommandAccessor<Protocol.ClearStateCommandMessage> CLEAR_STATE =
      Protocol.ClearStateCommandMessage::getName;
  CommandAccessor<Protocol.ClearAllStateCommandMessage> CLEAR_ALL_STATE =
      Protocol.ClearAllStateCommandMessage::getName;
  CommandAccessor<Protocol.SetStateCommandMessage> SET_STATE =
      Protocol.SetStateCommandMessage::getName;

  CommandAccessor<Protocol.SleepCommandMessage> SLEEP =
          new CommandAccessor<>() {
            @Override
            public void checkEntryHeader(
                    Protocol.SleepCommandMessage expected, MessageLite actual)
                    throws ProtocolException {
              Util.assertEntryClass(Protocol.SleepCommandMessage.class, actual);
              if (!expected.getName().equals(((Protocol.SleepCommandMessage) actual).getName())) {
                throw ProtocolException.commandDoesNotMatch(expected, actual);
              }
            }

            @Override
            public String getName(Protocol.SleepCommandMessage expected) {
              return expected.getName();
            }
          };

  CommandAccessor<Protocol.CallCommandMessage> CALL =
          Protocol.CallCommandMessage::getName;
  CommandAccessor<Protocol.OneWayCallCommandMessage> ONE_WAY_CALL =
          new CommandAccessor<>() {
              @Override
              public String getName(Protocol.OneWayCallCommandMessage expected) {
                  return "";
              }

              @Override
              public void checkEntryHeader(Protocol.OneWayCallCommandMessage expected, MessageLite actual) throws ProtocolException {
                  Util.assertEntryClass(Protocol.OneWayCallCommandMessage.class, actual);
                  var actualOneWayCall = (Protocol.OneWayCallCommandMessage) actual;

                  if (!(Objects.equals(expected.getServiceName(), actualOneWayCall.getServiceName())
                          && Objects.equals(expected.getHandlerName(), actualOneWayCall.getHandlerName())
                          && Objects.equals(expected.getParameter(), actualOneWayCall.getParameter())
                          && Objects.equals(expected.getKey(), actualOneWayCall.getKey())
                          && Objects.equals(expected.getHeadersList(), actualOneWayCall.getHeadersList())
                          && Objects.equals(expected.getIdempotencyKey(), actualOneWayCall.getIdempotencyKey())
                          && Objects.equals(expected.getName(), actualOneWayCall.getName())
                          && Objects.equals(expected.getInvocationIdNotificationIdx(), actualOneWayCall.getInvocationIdNotificationIdx())
                  )) {
                      throw ProtocolException.commandDoesNotMatch(expected, actual);
                  }
              }
          };

  CommandAccessor<Protocol.CompleteAwakeableCommandMessage> COMPLETE_AWAKEABLE =
          Protocol.CompleteAwakeableCommandMessage::getName;
    CommandAccessor<Protocol.RunCommandMessage> RUN =
            Protocol.RunCommandMessage::getName;

  CommandAccessor<Protocol.GetPromiseCommandMessage> GET_PROMISE =
          Protocol.GetPromiseCommandMessage::getName;
  CommandAccessor<Protocol.PeekPromiseCommandMessage> PEEK_PROMISE =
          Protocol.PeekPromiseCommandMessage::getName;
  CommandAccessor<Protocol.CompletePromiseCommandMessage> COMPLETE_PROMISE =
          Protocol.CompletePromiseCommandMessage::getName;

  CommandAccessor<Protocol.SendSignalCommandMessage> SEND_SIGNAL =
          Protocol.SendSignalCommandMessage::getName;
}
