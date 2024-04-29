// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.*;
import dev.restate.sdk.common.syscalls.Result;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Entries {
  static final String AWAKEABLE_IDENTIFIER_PREFIX = "prom_1";

  private Entries() {}

  abstract static class JournalEntry<E extends MessageLite> {
    abstract String getName(E expected);

    void checkEntryHeader(E expected, MessageLite actual) throws ProtocolException {}

    abstract void trace(E expected, Span span);

    void updateUserStateStoreWithEntry(E expected, UserStateStore userStateStore) {}
  }

  abstract static class CompletableJournalEntry<E extends MessageLite, R> extends JournalEntry<E> {
    abstract boolean hasResult(E actual);

    abstract Result<R> parseEntryResult(E actual);

    Result<R> parseCompletionResult(CompletionMessage actual) {
      throw ProtocolException.completionDoesNotMatch(
          this.getClass().getName(), actual.getResultCase());
    }

    E tryCompleteWithUserStateStorage(E expected, UserStateStore userStateStore) {
      return expected;
    }

    void updateUserStateStorageWithCompletion(
        E expected, CompletionMessage actual, UserStateStore userStateStore) {}
  }

  static final class OutputEntry extends JournalEntry<OutputEntryMessage> {

    static final OutputEntry INSTANCE = new OutputEntry();

    private OutputEntry() {}

    @Override
    String getName(OutputEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(OutputEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      Util.assertEntryEquals(expected, actual);
    }

    @Override
    public void trace(OutputEntryMessage expected, Span span) {
      span.addEvent("Output");
    }
  }

  static final class GetStateEntry
      extends CompletableJournalEntry<GetStateEntryMessage, ByteString> {

    static final GetStateEntry INSTANCE = new GetStateEntry();

    private GetStateEntry() {}

    @Override
    void trace(GetStateEntryMessage expected, Span span) {
      span.addEvent(
          "GetState", Attributes.of(Tracing.RESTATE_STATE_KEY, expected.getKey().toString()));
    }

    @Override
    public boolean hasResult(GetStateEntryMessage actual) {
      return actual.getResultCase() != GetStateEntryMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    String getName(GetStateEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(GetStateEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      if (!(actual instanceof GetStateEntryMessage)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
      if (!expected.getKey().equals(((GetStateEntryMessage) actual).getKey())) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
    }

    @Override
    public Result<ByteString> parseEntryResult(GetStateEntryMessage actual) {
      if (actual.getResultCase() == GetStateEntryMessage.ResultCase.VALUE) {
        return Result.success(actual.getValue());
      } else if (actual.getResultCase() == GetStateEntryMessage.ResultCase.FAILURE) {
        return Result.failure(Util.toRestateException(actual.getFailure()));
      } else if (actual.getResultCase() == GetStateEntryMessage.ResultCase.EMPTY) {
        return Result.empty();
      } else {
        throw new IllegalStateException("GetStateEntry has not been completed.");
      }
    }

    @Override
    public Result<ByteString> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.VALUE) {
        return Result.success(actual.getValue());
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.EMPTY) {
        return Result.empty();
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.FAILURE) {
        return Result.failure(Util.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }

    @Override
    void updateUserStateStoreWithEntry(
        GetStateEntryMessage expected, UserStateStore userStateStore) {
      userStateStore.set(expected.getKey(), expected.getValue());
    }

    @Override
    GetStateEntryMessage tryCompleteWithUserStateStorage(
        GetStateEntryMessage expected, UserStateStore userStateStore) {
      UserStateStore.State value = userStateStore.get(expected.getKey());
      if (value instanceof UserStateStore.Value) {
        return expected.toBuilder().setValue(((UserStateStore.Value) value).getValue()).build();
      } else if (value instanceof UserStateStore.Empty) {
        return expected.toBuilder().setEmpty(Empty.getDefaultInstance()).build();
      }
      return expected;
    }

    @Override
    void updateUserStateStorageWithCompletion(
        GetStateEntryMessage expected, CompletionMessage actual, UserStateStore userStateStore) {
      if (actual.hasEmpty()) {
        userStateStore.clear(expected.getKey());
      } else {
        userStateStore.set(expected.getKey(), actual.getValue());
      }
    }
  }

  static final class GetStateKeysEntry
      extends CompletableJournalEntry<GetStateKeysEntryMessage, Collection<String>> {

    static final GetStateKeysEntry INSTANCE = new GetStateKeysEntry();

    private GetStateKeysEntry() {}

    @Override
    void trace(GetStateKeysEntryMessage expected, Span span) {
      span.addEvent("GetStateKeys");
    }

    @Override
    public boolean hasResult(GetStateKeysEntryMessage actual) {
      return actual.getResultCase() != GetStateKeysEntryMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    String getName(GetStateKeysEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(GetStateKeysEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      if (!(actual instanceof GetStateKeysEntryMessage)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
    }

    @Override
    public Result<Collection<String>> parseEntryResult(GetStateKeysEntryMessage actual) {
      if (actual.getResultCase() == GetStateKeysEntryMessage.ResultCase.VALUE) {
        return Result.success(
            actual.getValue().getKeysList().stream()
                .map(ByteString::toStringUtf8)
                .collect(Collectors.toUnmodifiableList()));
      } else if (actual.getResultCase() == GetStateKeysEntryMessage.ResultCase.FAILURE) {
        return Result.failure(Util.toRestateException(actual.getFailure()));
      } else {
        throw new IllegalStateException("GetStateKeysEntryMessage has not been completed.");
      }
    }

    @Override
    public Result<Collection<String>> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.VALUE) {
        GetStateKeysEntryMessage.StateKeys stateKeys;
        try {
          stateKeys = GetStateKeysEntryMessage.StateKeys.parseFrom(actual.getValue());
        } catch (InvalidProtocolBufferException e) {
          throw new ProtocolException(
              "Cannot parse get state keys completion",
              ProtocolException.PROTOCOL_VIOLATION_CODE,
              e);
        }
        return Result.success(
            stateKeys.getKeysList().stream()
                .map(ByteString::toStringUtf8)
                .collect(Collectors.toUnmodifiableList()));
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.FAILURE) {
        return Result.failure(Util.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }

    @Override
    GetStateKeysEntryMessage tryCompleteWithUserStateStorage(
        GetStateKeysEntryMessage expected, UserStateStore userStateStore) {
      if (userStateStore.isComplete()) {
        return expected.toBuilder()
            .setValue(
                GetStateKeysEntryMessage.StateKeys.newBuilder().addAllKeys(userStateStore.keys()))
            .build();
      }
      return expected;
    }
  }

  static final class ClearStateEntry extends JournalEntry<ClearStateEntryMessage> {

    static final ClearStateEntry INSTANCE = new ClearStateEntry();

    private ClearStateEntry() {}

    @Override
    public void trace(ClearStateEntryMessage expected, Span span) {
      span.addEvent(
          "ClearState", Attributes.of(Tracing.RESTATE_STATE_KEY, expected.getKey().toString()));
    }

    @Override
    String getName(ClearStateEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(ClearStateEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      Util.assertEntryEquals(expected, actual);
    }

    @Override
    void updateUserStateStoreWithEntry(
        ClearStateEntryMessage expected, UserStateStore userStateStore) {
      userStateStore.clear(expected.getKey());
    }
  }

  static final class ClearAllStateEntry extends JournalEntry<ClearAllStateEntryMessage> {

    static final ClearAllStateEntry INSTANCE = new ClearAllStateEntry();

    private ClearAllStateEntry() {}

    @Override
    public void trace(ClearAllStateEntryMessage expected, Span span) {
      span.addEvent("ClearAllState");
    }

    @Override
    String getName(ClearAllStateEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(ClearAllStateEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      Util.assertEntryEquals(expected, actual);
    }

    @Override
    void updateUserStateStoreWithEntry(
        ClearAllStateEntryMessage expected, UserStateStore userStateStore) {
      userStateStore.clearAll();
    }
  }

  static final class SetStateEntry extends JournalEntry<SetStateEntryMessage> {

    static final SetStateEntry INSTANCE = new SetStateEntry();

    private SetStateEntry() {}

    @Override
    public void trace(SetStateEntryMessage expected, Span span) {
      span.addEvent(
          "SetState", Attributes.of(Tracing.RESTATE_STATE_KEY, expected.getKey().toString()));
    }

    @Override
    String getName(SetStateEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(SetStateEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      if (!(actual instanceof SetStateEntryMessage)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
      if (!expected.getKey().equals(((SetStateEntryMessage) actual).getKey())) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
    }

    @Override
    void updateUserStateStoreWithEntry(
        SetStateEntryMessage expected, UserStateStore userStateStore) {
      userStateStore.set(expected.getKey(), expected.getValue());
    }
  }

  static final class SleepEntry extends CompletableJournalEntry<SleepEntryMessage, Void> {

    static final SleepEntry INSTANCE = new SleepEntry();

    private SleepEntry() {}

    @Override
    String getName(SleepEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void trace(SleepEntryMessage expected, Span span) {
      span.addEvent(
          "Sleep", Attributes.of(Tracing.RESTATE_SLEEP_WAKE_UP_TIME, expected.getWakeUpTime()));
    }

    @Override
    public boolean hasResult(SleepEntryMessage actual) {
      return actual.getResultCase() != Protocol.SleepEntryMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public Result<Void> parseEntryResult(SleepEntryMessage actual) {
      if (actual.getResultCase() == SleepEntryMessage.ResultCase.FAILURE) {
        return Result.failure(Util.toRestateException(actual.getFailure()));
      } else if (actual.getResultCase() == SleepEntryMessage.ResultCase.EMPTY) {
        return Result.empty();
      } else {
        throw new IllegalStateException("SleepEntry has not been completed.");
      }
    }

    @Override
    public Result<Void> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.EMPTY) {
        return Result.empty();
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.FAILURE) {
        return Result.failure(Util.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class InvokeEntry<R> extends CompletableJournalEntry<CallEntryMessage, R> {

    private final Function<ByteString, Result<R>> valueParser;

    InvokeEntry(Function<ByteString, Result<R>> valueParser) {
      this.valueParser = valueParser;
    }

    @Override
    void trace(CallEntryMessage expected, Span span) {
      span.addEvent(
          "Invoke",
          Attributes.of(
              Tracing.RESTATE_COORDINATION_CALL_SERVICE,
              expected.getServiceName(),
              Tracing.RESTATE_COORDINATION_CALL_METHOD,
              expected.getHandlerName()));
    }

    @Override
    public boolean hasResult(CallEntryMessage actual) {
      return actual.getResultCase() != Protocol.CallEntryMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    String getName(CallEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(CallEntryMessage expected, MessageLite actual) throws ProtocolException {
      if (!(actual instanceof CallEntryMessage)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
      CallEntryMessage actualInvoke = (CallEntryMessage) actual;

      if (!(expected.getServiceName().equals(actualInvoke.getServiceName())
          && expected.getHandlerName().equals(actualInvoke.getHandlerName())
          && expected.getParameter().equals(actualInvoke.getParameter()))) {
        throw ProtocolException.entryDoesNotMatch(expected, actualInvoke);
      }
    }

    @Override
    public Result<R> parseEntryResult(CallEntryMessage actual) {
      if (actual.hasValue()) {
        return valueParser.apply(actual.getValue());
      }
      return Result.failure(Util.toRestateException(actual.getFailure()));
    }

    @Override
    public Result<R> parseCompletionResult(CompletionMessage actual) {
      if (actual.hasValue()) {
        return valueParser.apply(actual.getValue());
      }
      if (actual.hasFailure()) {
        return Result.failure(Util.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class BackgroundInvokeEntry extends JournalEntry<OneWayCallEntryMessage> {

    static final BackgroundInvokeEntry INSTANCE = new BackgroundInvokeEntry();

    private BackgroundInvokeEntry() {}

    @Override
    public void trace(OneWayCallEntryMessage expected, Span span) {
      span.addEvent(
          "BackgroundInvoke",
          Attributes.of(
              Tracing.RESTATE_COORDINATION_CALL_SERVICE,
              expected.getServiceName(),
              Tracing.RESTATE_COORDINATION_CALL_METHOD,
              expected.getHandlerName()));
    }

    @Override
    String getName(OneWayCallEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(OneWayCallEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      Util.assertEntryEquals(expected, actual);
    }
  }

  static final class AwakeableEntry
      extends CompletableJournalEntry<AwakeableEntryMessage, ByteString> {
    static final AwakeableEntry INSTANCE = new AwakeableEntry();

    private AwakeableEntry() {}

    @Override
    String getName(AwakeableEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void trace(AwakeableEntryMessage expected, Span span) {
      span.addEvent("Awakeable");
    }

    @Override
    public boolean hasResult(AwakeableEntryMessage actual) {
      return actual.getResultCase() != Protocol.AwakeableEntryMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public Result<ByteString> parseEntryResult(AwakeableEntryMessage actual) {
      if (actual.hasValue()) {
        return Result.success(actual.getValue());
      }
      return Result.failure(Util.toRestateException(actual.getFailure()));
    }

    @Override
    public Result<ByteString> parseCompletionResult(CompletionMessage actual) {
      if (actual.hasValue()) {
        return Result.success(actual.getValue());
      }
      if (actual.hasFailure()) {
        return Result.failure(Util.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class CompleteAwakeableEntry extends JournalEntry<CompleteAwakeableEntryMessage> {

    static final CompleteAwakeableEntry INSTANCE = new CompleteAwakeableEntry();

    private CompleteAwakeableEntry() {}

    @Override
    public void trace(CompleteAwakeableEntryMessage expected, Span span) {
      span.addEvent("CompleteAwakeable");
    }

    @Override
    String getName(CompleteAwakeableEntryMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(CompleteAwakeableEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      Util.assertEntryEquals(expected, actual);
    }
  }
}
