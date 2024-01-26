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
import com.google.protobuf.Empty;
import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.*;
import dev.restate.sdk.core.ReadyResults.ReadyResultInternal;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.util.function.Function;

final class Entries {
  static final String AWAKEABLE_IDENTIFIER_PREFIX = "prom_1";

  private Entries() {}

  abstract static class JournalEntry<E extends MessageLite> {
    void checkEntryHeader(E expected, MessageLite actual) throws ProtocolException {}

    abstract void trace(E expected, Span span);

    void updateUserStateStoreWithEntry(E expected, UserStateStore userStateStore) {}
  }

  abstract static class CompletableJournalEntry<E extends MessageLite, R> extends JournalEntry<E> {
    abstract boolean hasResult(E actual);

    abstract ReadyResultInternal<R> parseEntryResult(E actual);

    ReadyResultInternal<R> parseCompletionResult(CompletionMessage actual) {
      throw ProtocolException.completionDoesNotMatch(
          this.getClass().getName(), actual.getResultCase());
    }

    E tryCompleteWithUserStateStorage(E expected, UserStateStore userStateStore) {
      return expected;
    }

    void updateUserStateStorageWithCompletion(
        E expected, CompletionMessage actual, UserStateStore userStateStore) {}
  }

  static final class PollInputEntry<R extends MessageLite>
      extends CompletableJournalEntry<PollInputStreamEntryMessage, R> {

    private final Function<ByteString, ReadyResultInternal<R>> valueParser;

    PollInputEntry(Function<ByteString, ReadyResultInternal<R>> valueParser) {
      this.valueParser = valueParser;
    }

    @Override
    public void trace(PollInputStreamEntryMessage expected, Span span) {
      span.addEvent("PollInputStream");
    }

    @Override
    public boolean hasResult(PollInputStreamEntryMessage actual) {
      return actual.getResultCase() != PollInputStreamEntryMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public ReadyResultInternal<R> parseEntryResult(PollInputStreamEntryMessage actual) {
      if (actual.getResultCase() == PollInputStreamEntryMessage.ResultCase.VALUE) {
        return valueParser.apply(actual.getValue());
      } else if (actual.getResultCase() == PollInputStreamEntryMessage.ResultCase.FAILURE) {
        return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
      } else {
        throw new IllegalStateException("PollInputEntry has not been completed.");
      }
    }

    @Override
    public ReadyResultInternal<R> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.VALUE) {
        return valueParser.apply(actual.getValue());
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.FAILURE) {
        return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class OutputStreamEntry extends JournalEntry<OutputStreamEntryMessage> {

    static final OutputStreamEntry INSTANCE = new OutputStreamEntry();

    private OutputStreamEntry() {}

    @Override
    public void trace(OutputStreamEntryMessage expected, Span span) {
      span.addEvent("OutputStream");
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
    public ReadyResultInternal<ByteString> parseEntryResult(GetStateEntryMessage actual) {
      if (actual.getResultCase() == GetStateEntryMessage.ResultCase.VALUE) {
        return ReadyResults.success(actual.getValue());
      } else if (actual.getResultCase() == GetStateEntryMessage.ResultCase.FAILURE) {
        return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
      } else if (actual.getResultCase() == GetStateEntryMessage.ResultCase.EMPTY) {
        return ReadyResults.empty();
      } else {
        throw new IllegalStateException("GetStateEntry has not been completed.");
      }
    }

    @Override
    public ReadyResultInternal<ByteString> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.VALUE) {
        return ReadyResults.success(actual.getValue());
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.EMPTY) {
        return ReadyResults.empty();
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.FAILURE) {
        return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
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

  static final class ClearStateEntry extends JournalEntry<ClearStateEntryMessage> {

    static final ClearStateEntry INSTANCE = new ClearStateEntry();

    private ClearStateEntry() {}

    @Override
    public void trace(ClearStateEntryMessage expected, Span span) {
      span.addEvent(
          "ClearState", Attributes.of(Tracing.RESTATE_STATE_KEY, expected.getKey().toString()));
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

  static final class SetStateEntry extends JournalEntry<SetStateEntryMessage> {

    static final SetStateEntry INSTANCE = new SetStateEntry();

    private SetStateEntry() {}

    @Override
    public void trace(SetStateEntryMessage expected, Span span) {
      span.addEvent(
          "SetState", Attributes.of(Tracing.RESTATE_STATE_KEY, expected.getKey().toString()));
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
    void trace(SleepEntryMessage expected, Span span) {
      span.addEvent(
          "Sleep", Attributes.of(Tracing.RESTATE_SLEEP_WAKE_UP_TIME, expected.getWakeUpTime()));
    }

    @Override
    public boolean hasResult(SleepEntryMessage actual) {
      return actual.getResultCase() != Protocol.SleepEntryMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public ReadyResultInternal<Void> parseEntryResult(SleepEntryMessage actual) {
      if (actual.getResultCase() == SleepEntryMessage.ResultCase.FAILURE) {
        return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
      } else if (actual.getResultCase() == SleepEntryMessage.ResultCase.EMPTY) {
        return ReadyResults.empty();
      } else {
        throw new IllegalStateException("SleepEntry has not been completed.");
      }
    }

    @Override
    public ReadyResultInternal<Void> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.EMPTY) {
        return ReadyResults.empty();
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.FAILURE) {
        return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class InvokeEntry<R> extends CompletableJournalEntry<InvokeEntryMessage, R> {

    private final Function<ByteString, ReadyResultInternal<R>> valueParser;

    InvokeEntry(Function<ByteString, ReadyResultInternal<R>> valueParser) {
      this.valueParser = valueParser;
    }

    @Override
    void trace(InvokeEntryMessage expected, Span span) {
      span.addEvent(
          "Invoke",
          Attributes.of(
              Tracing.RESTATE_COORDINATION_CALL_SERVICE,
              expected.getServiceName(),
              Tracing.RESTATE_COORDINATION_CALL_METHOD,
              expected.getMethodName()));
    }

    @Override
    public boolean hasResult(InvokeEntryMessage actual) {
      return actual.getResultCase() != Protocol.InvokeEntryMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    void checkEntryHeader(InvokeEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      if (!(actual instanceof InvokeEntryMessage)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
      InvokeEntryMessage actualInvoke = (InvokeEntryMessage) actual;

      if (!(expected.getServiceName().equals(actualInvoke.getServiceName())
          && expected.getMethodName().equals(actualInvoke.getMethodName())
          && expected.getParameter().equals(actualInvoke.getParameter()))) {
        throw ProtocolException.entryDoesNotMatch(expected, actualInvoke);
      }
    }

    @Override
    public ReadyResultInternal<R> parseEntryResult(InvokeEntryMessage actual) {
      if (actual.hasValue()) {
        return valueParser.apply(actual.getValue());
      }
      return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
    }

    @Override
    public ReadyResultInternal<R> parseCompletionResult(CompletionMessage actual) {
      if (actual.hasValue()) {
        return valueParser.apply(actual.getValue());
      }
      if (actual.hasFailure()) {
        return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class BackgroundInvokeEntry extends JournalEntry<BackgroundInvokeEntryMessage> {

    static final BackgroundInvokeEntry INSTANCE = new BackgroundInvokeEntry();

    private BackgroundInvokeEntry() {}

    @Override
    public void trace(BackgroundInvokeEntryMessage expected, Span span) {
      span.addEvent(
          "BackgroundInvoke",
          Attributes.of(
              Tracing.RESTATE_COORDINATION_CALL_SERVICE,
              expected.getServiceName(),
              Tracing.RESTATE_COORDINATION_CALL_METHOD,
              expected.getMethodName()));
    }

    @Override
    void checkEntryHeader(BackgroundInvokeEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      Util.assertEntryEquals(expected, actual);
    }
  }

  static final class AwakeableEntry
      extends CompletableJournalEntry<AwakeableEntryMessage, ByteString> {
    static final AwakeableEntry INSTANCE = new AwakeableEntry();

    private AwakeableEntry() {}

    @Override
    void trace(AwakeableEntryMessage expected, Span span) {
      span.addEvent("Awakeable");
    }

    @Override
    public boolean hasResult(AwakeableEntryMessage actual) {
      return actual.getResultCase() != Protocol.AwakeableEntryMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public ReadyResultInternal<ByteString> parseEntryResult(AwakeableEntryMessage actual) {
      if (actual.hasValue()) {
        return ReadyResults.success(actual.getValue());
      }
      return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
    }

    @Override
    public ReadyResultInternal<ByteString> parseCompletionResult(CompletionMessage actual) {
      if (actual.hasValue()) {
        return ReadyResults.success(actual.getValue());
      }
      if (actual.hasFailure()) {
        return ReadyResults.failure(Util.toRestateException(actual.getFailure()));
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
    void checkEntryHeader(CompleteAwakeableEntryMessage expected, MessageLite actual)
        throws ProtocolException {
      Util.assertEntryEquals(expected, actual);
    }
  }
}
