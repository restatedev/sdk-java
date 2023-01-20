package dev.restate.sdk.core.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.*;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.util.function.Function;

final class Entries {

  private Entries() {}

  abstract static class JournalEntry<E extends MessageLite> {
    void checkEntryHeader(E expected, MessageLite actual) throws ProtocolException {}

    abstract void trace(E expected, Span span);
  }

  abstract static class CompletableJournalEntry<E extends MessageLite, R> extends JournalEntry<E> {
    abstract boolean hasResult(E actual);

    abstract ReadyResultInternal<R> parseEntryResult(E actual);

    ReadyResultInternal<R> parseCompletionResult(CompletionMessage actual) {
      throw ProtocolException.completionDoesNotMatch(
          this.getClass().getName(), actual.getResultCase());
    }
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
      return true;
    }

    @Override
    public ReadyResultInternal<R> parseEntryResult(PollInputStreamEntryMessage actual) {
      return valueParser.apply(actual.getValue());
    }

    @Override
    public ReadyResultInternal<R> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.VALUE) {
        return valueParser.apply(actual.getValue());
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

  static final class GetStateEntry<R> extends CompletableJournalEntry<GetStateEntryMessage, R> {

    private final Function<ByteString, ReadyResultInternal<R>> valueParser;

    GetStateEntry(Function<ByteString, ReadyResultInternal<R>> valueParser) {
      this.valueParser = valueParser;
    }

    @Override
    void trace(GetStateEntryMessage expected, Span span) {
      span.addEvent(
          "GetState", Attributes.of(Tracing.RESTATE_STATE_KEY, expected.getKey().toString()));
    }

    @Override
    public boolean hasResult(GetStateEntryMessage actual) {
      return actual.getResultCase() == GetStateEntryMessage.ResultCase.VALUE;
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
    public ReadyResultInternal<R> parseEntryResult(GetStateEntryMessage actual) {
      if (actual.getResultCase() == GetStateEntryMessage.ResultCase.VALUE) {
        return valueParser.apply(actual.getValue());
      }
      return ReadyResults.empty();
    }

    @Override
    public ReadyResultInternal<R> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.VALUE) {
        return valueParser.apply(actual.getValue());
      }
      if (actual.getResultCase() == CompletionMessage.ResultCase.EMPTY) {
        return ReadyResults.empty();
      }
      return super.parseCompletionResult(actual);
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
      Util.assertEntryEquals(expected, actual);
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
      return actual.hasResult();
    }

    @Override
    public ReadyResultInternal<Void> parseEntryResult(SleepEntryMessage actual) {
      return ReadyResults.empty();
    }

    @Override
    public ReadyResultInternal<Void> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.EMPTY) {
        return ReadyResults.empty();
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class InvokeEntry<R extends MessageLite>
      extends CompletableJournalEntry<InvokeEntryMessage, R> {

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
      return ReadyResults.failure(Util.toGrpcStatus(actual.getFailure()).asRuntimeException());
    }

    @Override
    public ReadyResultInternal<R> parseCompletionResult(CompletionMessage actual) {
      if (actual.hasValue()) {
        return valueParser.apply(actual.getValue());
      }
      if (actual.hasFailure()) {
        return ReadyResults.failure(Util.toGrpcStatus(actual.getFailure()).asRuntimeException());
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

  static final class AwakeableEntry<R> extends CompletableJournalEntry<AwakeableEntryMessage, R> {

    private final Function<ByteString, ReadyResultInternal<R>> valueParser;

    AwakeableEntry(Function<ByteString, ReadyResultInternal<R>> valueParser) {
      this.valueParser = valueParser;
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
    public ReadyResultInternal<R> parseEntryResult(AwakeableEntryMessage actual) {
      if (actual.hasValue()) {
        return valueParser.apply(actual.getValue());
      }
      return ReadyResults.failure(Util.toGrpcStatus(actual.getFailure()).asRuntimeException());
    }

    @Override
    public ReadyResultInternal<R> parseCompletionResult(CompletionMessage actual) {
      if (actual.hasValue()) {
        return valueParser.apply(actual.getValue());
      }
      if (actual.hasFailure()) {
        return ReadyResults.failure(Util.toGrpcStatus(actual.getFailure()).asRuntimeException());
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
