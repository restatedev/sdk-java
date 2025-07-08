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

interface CommandAccessor<E extends MessageLite> {

  String getName(E expected);

  void checkEntryHeader(E expected, MessageLite actual) throws ProtocolException;

  CommandAccessor<Protocol.InputCommandMessage> INPUT =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.InputCommandMessage expected) {
          return "";
        }

        @Override
        public void checkEntryHeader(Protocol.InputCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          // Nothing to check
        }
      };
  CommandAccessor<Protocol.OutputCommandMessage> OUTPUT =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.OutputCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(Protocol.OutputCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.OutputCommandMessage.class, expected, actual)
              .checkField("name", Protocol.OutputCommandMessage::getName)
              .checkField("result", Protocol.OutputCommandMessage::getResultCase)
              .checkField("value", Protocol.OutputCommandMessage::getValue)
              .checkField("failure", Protocol.OutputCommandMessage::getFailure)
              .verify();
        }
      };
  CommandAccessor<Protocol.GetEagerStateCommandMessage> GET_EAGER_STATE =
      new CommandAccessor<>() {
        @Override
        public void checkEntryHeader(
            Protocol.GetEagerStateCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.GetEagerStateCommandMessage.class, expected, actual)
              .checkField("name", Protocol.GetEagerStateCommandMessage::getName)
              .checkField("key", Protocol.GetEagerStateCommandMessage::getKey)
              .verify();
        }

        @Override
        public String getName(Protocol.GetEagerStateCommandMessage expected) {
          return expected.getName();
        }
      };
  CommandAccessor<Protocol.GetLazyStateCommandMessage> GET_LAZY_STATE =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.GetLazyStateCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(
            Protocol.GetLazyStateCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.GetLazyStateCommandMessage.class, expected, actual)
              .checkField("name", Protocol.GetLazyStateCommandMessage::getName)
              .checkField("key", Protocol.GetLazyStateCommandMessage::getKey)
              .checkField(
                  "result_completion_id",
                  Protocol.GetLazyStateCommandMessage::getResultCompletionId)
              .verify();
        }
      };
  CommandAccessor<Protocol.GetEagerStateKeysCommandMessage> GET_EAGER_STATE_KEYS =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.GetEagerStateKeysCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(
            Protocol.GetEagerStateKeysCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.GetEagerStateKeysCommandMessage.class, expected, actual)
              .checkField("name", Protocol.GetEagerStateKeysCommandMessage::getName)
              .verify();
        }
      };
  CommandAccessor<Protocol.GetLazyStateKeysCommandMessage> GET_LAZY_STATE_KEYS =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.GetLazyStateKeysCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(
            Protocol.GetLazyStateKeysCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.GetLazyStateKeysCommandMessage.class, expected, actual)
              .checkField("name", Protocol.GetLazyStateKeysCommandMessage::getName)
              .verify();
        }
      };
  CommandAccessor<Protocol.ClearStateCommandMessage> CLEAR_STATE =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.ClearStateCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(Protocol.ClearStateCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.ClearStateCommandMessage.class, expected, actual)
              .checkField("name", Protocol.ClearStateCommandMessage::getName)
              .checkField("key", Protocol.ClearStateCommandMessage::getKey)
              .verify();
        }
      };
  CommandAccessor<Protocol.ClearAllStateCommandMessage> CLEAR_ALL_STATE =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.ClearAllStateCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(
            Protocol.ClearAllStateCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.ClearAllStateCommandMessage.class, expected, actual)
              .checkField("name", Protocol.ClearAllStateCommandMessage::getName)
              .verify();
        }
      };
  CommandAccessor<Protocol.SetStateCommandMessage> SET_STATE =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.SetStateCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(Protocol.SetStateCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.SetStateCommandMessage.class, expected, actual)
              .checkField("name", Protocol.SetStateCommandMessage::getName)
              .checkField("key", Protocol.SetStateCommandMessage::getKey)
              .checkField("value", Protocol.SetStateCommandMessage::getValue)
              .verify();
        }
      };

  CommandAccessor<Protocol.SleepCommandMessage> SLEEP =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.SleepCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(Protocol.SleepCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.SleepCommandMessage.class, expected, actual)
              .checkField("name", Protocol.SleepCommandMessage::getName)
              .checkField(
                  "result_completion_id", Protocol.SleepCommandMessage::getResultCompletionId)
              .verify();
        }
      };

  CommandAccessor<Protocol.CallCommandMessage> CALL =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.CallCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(Protocol.CallCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.CallCommandMessage.class, expected, actual)
              .checkField("name", Protocol.CallCommandMessage::getName)
              .checkField("service_name", Protocol.CallCommandMessage::getServiceName)
              .checkField("handler_name", Protocol.CallCommandMessage::getHandlerName)
              .checkField("parameter", Protocol.CallCommandMessage::getParameter)
              .checkField("key", Protocol.CallCommandMessage::getKey)
              .checkField("idempotency_key", Protocol.CallCommandMessage::getIdempotencyKey)
              .checkField("headers", Protocol.CallCommandMessage::getHeadersList)
              .checkField(
                  "invocation_id_notification_idx",
                  Protocol.CallCommandMessage::getInvocationIdNotificationIdx)
              .checkField(
                  "result_completion_id", Protocol.CallCommandMessage::getResultCompletionId)
              .verify();
        }
      };
  CommandAccessor<Protocol.OneWayCallCommandMessage> ONE_WAY_CALL =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.OneWayCallCommandMessage expected) {
          return "";
        }

        @Override
        public void checkEntryHeader(Protocol.OneWayCallCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.OneWayCallCommandMessage.class, expected, actual)
              .checkField("name", Protocol.OneWayCallCommandMessage::getName)
              .checkField("service_name", Protocol.OneWayCallCommandMessage::getServiceName)
              .checkField("handler_name", Protocol.OneWayCallCommandMessage::getHandlerName)
              .checkField("parameter", Protocol.OneWayCallCommandMessage::getParameter)
              .checkField("key", Protocol.OneWayCallCommandMessage::getKey)
              .checkField("headers", Protocol.OneWayCallCommandMessage::getHeadersList)
              .checkField("idempotency_key", Protocol.OneWayCallCommandMessage::getIdempotencyKey)
              .checkField(
                  "invocation_id_notification_idx",
                  Protocol.OneWayCallCommandMessage::getInvocationIdNotificationIdx)
              .verify();
        }
      };

  CommandAccessor<Protocol.CompleteAwakeableCommandMessage> COMPLETE_AWAKEABLE =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.CompleteAwakeableCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(
            Protocol.CompleteAwakeableCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.CompleteAwakeableCommandMessage.class, expected, actual)
              .checkField("name", Protocol.CompleteAwakeableCommandMessage::getName)
              .checkField("awakeable_id", Protocol.CompleteAwakeableCommandMessage::getAwakeableId)
              .checkField("result", Protocol.CompleteAwakeableCommandMessage::getResultCase)
              .checkField("value", Protocol.CompleteAwakeableCommandMessage::getValue)
              .checkField("failure", Protocol.CompleteAwakeableCommandMessage::getFailure)
              .verify();
        }
      };
  CommandAccessor<Protocol.RunCommandMessage> RUN =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.RunCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(Protocol.RunCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.RunCommandMessage.class, expected, actual)
              .checkField("name", Protocol.RunCommandMessage::getName)
              .verify();
        }
      };

  CommandAccessor<Protocol.GetPromiseCommandMessage> GET_PROMISE =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.GetPromiseCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(Protocol.GetPromiseCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.GetPromiseCommandMessage.class, expected, actual)
              .checkField("name", Protocol.GetPromiseCommandMessage::getName)
              .checkField("key", Protocol.GetPromiseCommandMessage::getKey)
              .checkField(
                  "result_completion_id", Protocol.GetPromiseCommandMessage::getResultCompletionId)
              .verify();
        }
      };
  CommandAccessor<Protocol.PeekPromiseCommandMessage> PEEK_PROMISE =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.PeekPromiseCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(
            Protocol.PeekPromiseCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.PeekPromiseCommandMessage.class, expected, actual)
              .checkField("name", Protocol.PeekPromiseCommandMessage::getName)
              .checkField("key", Protocol.PeekPromiseCommandMessage::getKey)
              .checkField(
                  "result_completion_id", Protocol.PeekPromiseCommandMessage::getResultCompletionId)
              .verify();
        }
      };
  CommandAccessor<Protocol.CompletePromiseCommandMessage> COMPLETE_PROMISE =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.CompletePromiseCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(
            Protocol.CompletePromiseCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.CompletePromiseCommandMessage.class, expected, actual)
              .checkField("name", Protocol.CompletePromiseCommandMessage::getName)
              .checkField("key", Protocol.CompletePromiseCommandMessage::getKey)
              .checkField(
                  "result_completion_id",
                  Protocol.CompletePromiseCommandMessage::getResultCompletionId)
              .checkField("completion", Protocol.CompletePromiseCommandMessage::getCompletionCase)
              .checkField(
                  "completionValue", Protocol.CompletePromiseCommandMessage::getCompletionValue)
              .checkField(
                  "completionFailure", Protocol.CompletePromiseCommandMessage::getCompletionFailure)
              .verify();
        }
      };

  CommandAccessor<Protocol.SendSignalCommandMessage> SEND_SIGNAL =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.SendSignalCommandMessage expected) {
          return expected.getEntryName();
        }

        @Override
        public void checkEntryHeader(Protocol.SendSignalCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.SendSignalCommandMessage.class, expected, actual)
              .checkField("entry_name", Protocol.SendSignalCommandMessage::getEntryName)
              .checkField(
                  "target_invocation_id", Protocol.SendSignalCommandMessage::getTargetInvocationId)
              .checkField("signal_id", Protocol.SendSignalCommandMessage::getSignalIdCase)
              .checkField("result", Protocol.SendSignalCommandMessage::getResultCase)
              .checkField("void", Protocol.SendSignalCommandMessage::getVoid)
              .checkField("value", Protocol.SendSignalCommandMessage::getValue)
              .checkField("failure", Protocol.SendSignalCommandMessage::getFailure)
              .verify();
        }
      };

  CommandAccessor<Protocol.AttachInvocationCommandMessage> ATTACH_INVOCATION =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.AttachInvocationCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(
            Protocol.AttachInvocationCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(Protocol.AttachInvocationCommandMessage.class, expected, actual)
              .checkField("name", Protocol.AttachInvocationCommandMessage::getName)
              .checkField("invocation_id", Protocol.AttachInvocationCommandMessage::getInvocationId)
              .checkField(
                  "result_completion_id",
                  Protocol.AttachInvocationCommandMessage::getResultCompletionId)
              .verify();
        }
      };

  CommandAccessor<Protocol.GetInvocationOutputCommandMessage> GET_INVOCATION_OUTPUT =
      new CommandAccessor<>() {
        @Override
        public String getName(Protocol.GetInvocationOutputCommandMessage expected) {
          return expected.getName();
        }

        @Override
        public void checkEntryHeader(
            Protocol.GetInvocationOutputCommandMessage expected, MessageLite actual)
            throws ProtocolException {
          EntryHeaderChecker.check(
                  Protocol.GetInvocationOutputCommandMessage.class, expected, actual)
              .checkField("name", Protocol.GetInvocationOutputCommandMessage::getName)
              .checkField(
                  "invocation_id", Protocol.GetInvocationOutputCommandMessage::getInvocationId)
              .checkField(
                  "result_completion_id",
                  Protocol.GetInvocationOutputCommandMessage::getResultCompletionId)
              .verify();
        }
      };
}
