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
import dev.restate.sdk.core.ExceptionUtils;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Function;

interface CommandAccessor<E extends MessageLite> {

  String getName(E expected);

  default void checkEntryHeader(E expected, MessageLite actual) throws ProtocolException {
    Util.assertEntryEquals(expected, actual);
  }

  CommandAccessor<Protocol.OutputCommandMessage> OUTPUT = Protocol.OutputCommandMessage::getName;
  CommandAccessor<Protocol.GetEagerStateCommandMessage> GET_EAGER_STATE = new CommandAccessor<>() {
      @Override
public      void checkEntryHeader(Protocol.GetEagerStateCommandMessage expected, MessageLite actual) throws ProtocolException {
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
  CommandAccessor<Protocol.GetLazyStateCommandMessage> GET_LAZY_STATE = Protocol.GetLazyStateCommandMessage::getName;
  CommandAccessor<Protocol.GetEagerStateKeysCommandMessage> GET_EAGER_STATE_KEYS = new CommandAccessor<>() {
    @Override
    public      void checkEntryHeader(Protocol.GetEagerStateKeysCommandMessage expected, MessageLite actual) throws ProtocolException {
      Util.assertEntryClass(Protocol.GetEagerStateKeysCommandMessage.class, actual);
    }

    @Override
    public String getName(Protocol.GetEagerStateKeysCommandMessage expected) {
      return expected.getName();
    }
  };
  CommandAccessor<Protocol.GetLazyStateKeysCommandMessage> GET_LAZY_STATE_KEYS = Protocol.GetLazyStateKeysCommandMessage::getName;
  CommandAccessor<Protocol.ClearStateCommandMessage> CLEAR_STATE = Protocol.ClearStateCommandMessage::getName;
  CommandAccessor<Protocol.ClearAllStateCommandMessage> CLEAR_ALL_STATE = Protocol.ClearAllStateCommandMessage::getName;
  CommandAccessor<Protocol.SetStateCommandMessage> SET_STATE = Protocol.SetStateCommandMessage::getName;



  static final class SleepEntry extends CompletableJournalEntry<SleepCommandMessage, Void> {

    static final SleepEntry INSTANCE = new SleepEntry();

    private SleepEntry() {}

    @Override
    String getName(SleepCommandMessage expected) {
      return expected.getName();
    }

    @Override
    void trace(SleepCommandMessage expected, Span span) {
      span.addEvent(
          "Sleep", Attributes.of(Tracing.RESTATE_SLEEP_WAKE_UP_TIME, expected.getWakeUpTime()));
    }

    @Override
    public boolean hasResult(SleepCommandMessage actual) {
      return actual.getResultCase() != Protocol.SleepCommandMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public Result<Void> parseEntryResult(SleepCommandMessage actual) {
      if (actual.getResultCase() == SleepCommandMessage.ResultCase.FAILURE) {
        return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
      } else if (actual.getResultCase() == SleepCommandMessage.ResultCase.EMPTY) {
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
        return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class InvokeEntry<R> extends CompletableJournalEntry<CallCommandMessage, R> {

    private final Function<ByteBuffer, Result<R>> valueParser;

    InvokeEntry(Function<ByteBuffer, Result<R>> valueParser) {
      this.valueParser = valueParser;
    }

    @Override
    void trace(CallCommandMessage expected, Span span) {
      span.addEvent(
          "Invoke",
          Attributes.of(
              Tracing.RESTATE_COORDINATION_CALL_SERVICE,
              expected.getServiceName(),
              Tracing.RESTATE_COORDINATION_CALL_METHOD,
              expected.getHandlerName()));
    }

    @Override
    public boolean hasResult(CallCommandMessage actual) {
      return actual.getResultCase() != Protocol.CallCommandMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    String getName(CallCommandMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(CallCommandMessage expected, MessageLite actual) throws ProtocolException {
      if (!(actual instanceof CallCommandMessage actualInvoke)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }

      if (!(Objects.equals(expected.getServiceName(), actualInvoke.getServiceName())
          && Objects.equals(expected.getHandlerName(), actualInvoke.getHandlerName())
          && Objects.equals(expected.getParameter(), actualInvoke.getParameter())
          && Objects.equals(expected.getKey(), actualInvoke.getKey()))) {
        throw ProtocolException.entryDoesNotMatch(expected, actualInvoke);
      }
    }

    @Override
    public Result<R> parseEntryResult(CallCommandMessage actual) {
      if (actual.hasValue()) {
        return valueParser.apply(actual.getValue().asReadOnlyByteBuffer());
      }
      return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
    }

    @Override
    public Result<R> parseCompletionResult(CompletionMessage actual) {
      if (actual.hasValue()) {
        return valueParser.apply(actual.getValue().asReadOnlyByteBuffer());
      }
      if (actual.hasFailure()) {
        return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class OneWayCallEntry extends JournalEntry<OneWayCallCommandMessage> {

    static final OneWayCallEntry INSTANCE = new OneWayCallEntry();

    private OneWayCallEntry() {}

    @Override
    public void trace(OneWayCallCommandMessage expected, Span span) {
      span.addEvent(
          "BackgroundInvoke",
          Attributes.of(
              Tracing.RESTATE_COORDINATION_CALL_SERVICE,
              expected.getServiceName(),
              Tracing.RESTATE_COORDINATION_CALL_METHOD,
              expected.getHandlerName()));
    }

    @Override
    String getName(OneWayCallCommandMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(OneWayCallCommandMessage expected, MessageLite actual)
        throws ProtocolException {
      if (!(actual instanceof OneWayCallCommandMessage actualInvoke)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }

      if (!(Objects.equals(expected.getServiceName(), actualInvoke.getServiceName())
          && Objects.equals(expected.getHandlerName(), actualInvoke.getHandlerName())
          && Objects.equals(expected.getParameter(), actualInvoke.getParameter())
          && Objects.equals(expected.getKey(), actualInvoke.getKey()))) {
        throw ProtocolException.entryDoesNotMatch(expected, actualInvoke);
      }
    }
  }

  static final class AwakeableEntry
      extends CompletableJournalEntry<AwakeableCommandMessage, ByteBuffer> {
    static final AwakeableEntry INSTANCE = new AwakeableEntry();

    private AwakeableEntry() {}

    @Override
    String getName(AwakeableCommandMessage expected) {
      return expected.getName();
    }

    @Override
    void trace(AwakeableCommandMessage expected, Span span) {
      span.addEvent("Awakeable");
    }

    @Override
    public boolean hasResult(AwakeableCommandMessage actual) {
      return actual.getResultCase() != Protocol.AwakeableCommandMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public Result<ByteBuffer> parseEntryResult(AwakeableCommandMessage actual) {
      if (actual.hasValue()) {
        return Result.success(actual.getValue().asReadOnlyByteBuffer());
      }
      return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
    }

    @Override
    public Result<ByteBuffer> parseCompletionResult(CompletionMessage actual) {
      if (actual.hasValue()) {
        return Result.success(actual.getValue().asReadOnlyByteBuffer());
      }
      if (actual.hasFailure()) {
        return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class GetPromiseEntry
      extends CompletableJournalEntry<GetPromiseCommandMessage, ByteBuffer> {
    static final GetPromiseEntry INSTANCE = new GetPromiseEntry();

    private GetPromiseEntry() {}

    @Override
    String getName(GetPromiseCommandMessage expected) {
      return expected.getName();
    }

    @Override
    void trace(GetPromiseCommandMessage expected, Span span) {
      span.addEvent("Promise");
    }

    @Override
    void checkEntryHeader(GetPromiseCommandMessage expected, MessageLite actual)
        throws ProtocolException {
      if (!(actual instanceof GetPromiseCommandMessage)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
      if (!expected.getKey().equals(((GetPromiseCommandMessage) actual).getKey())) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
    }

    @Override
    public boolean hasResult(GetPromiseCommandMessage actual) {
      return actual.getResultCase() != Protocol.GetPromiseCommandMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public Result<ByteBuffer> parseEntryResult(GetPromiseCommandMessage actual) {
      if (actual.hasValue()) {
        return Result.success(actual.getValue().asReadOnlyByteBuffer());
      }
      return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
    }

    @Override
    public Result<ByteBuffer> parseCompletionResult(CompletionMessage actual) {
      if (actual.hasValue()) {
        return Result.success(actual.getValue().asReadOnlyByteBuffer());
      }
      if (actual.hasFailure()) {
        return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class PeekPromiseEntry
      extends CompletableJournalEntry<PeekPromiseCommandMessage, ByteBuffer> {
    static final PeekPromiseEntry INSTANCE = new PeekPromiseEntry();

    private PeekPromiseEntry() {}

    @Override
    String getName(PeekPromiseCommandMessage expected) {
      return expected.getName();
    }

    @Override
    void trace(PeekPromiseCommandMessage expected, Span span) {
      span.addEvent("PeekPromise");
    }

    @Override
    void checkEntryHeader(PeekPromiseCommandMessage expected, MessageLite actual)
        throws ProtocolException {
      if (!(actual instanceof PeekPromiseCommandMessage)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
      if (!expected.getKey().equals(((PeekPromiseCommandMessage) actual).getKey())) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
    }

    @Override
    public boolean hasResult(PeekPromiseCommandMessage actual) {
      return actual.getResultCase() != Protocol.PeekPromiseCommandMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public Result<ByteBuffer> parseEntryResult(PeekPromiseCommandMessage actual) {
      if (actual.getResultCase() == PeekPromiseCommandMessage.ResultCase.VALUE) {
        return Result.success(actual.getValue().asReadOnlyByteBuffer());
      } else if (actual.getResultCase() == PeekPromiseCommandMessage.ResultCase.FAILURE) {
        return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
      } else if (actual.getResultCase() == PeekPromiseCommandMessage.ResultCase.EMPTY) {
        return Result.empty();
      } else {
        throw new IllegalStateException("PeekPromiseEntry has not been completed.");
      }
    }

    @Override
    public Result<ByteBuffer> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.VALUE) {
        return Result.success(actual.getValue().asReadOnlyByteBuffer());
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.EMPTY) {
        return Result.empty();
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.FAILURE) {
        return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class CompletePromiseEntry
      extends CompletableJournalEntry<CompletePromiseCommandMessage, Void> {

    static final CompletePromiseEntry INSTANCE = new CompletePromiseEntry();

    private CompletePromiseEntry() {}

    @Override
    String getName(CompletePromiseCommandMessage expected) {
      return expected.getName();
    }

    @Override
    void trace(CompletePromiseCommandMessage expected, Span span) {
      span.addEvent("CompletePromise");
    }

    @Override
    void checkEntryHeader(CompletePromiseCommandMessage expected, MessageLite actual)
        throws ProtocolException {
      if (!(actual instanceof CompletePromiseCommandMessage)) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
      if (!expected.getKey().equals(((CompletePromiseCommandMessage) actual).getKey())) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
      if (!expected
          .getCompletionCase()
          .equals(((CompletePromiseCommandMessage) actual).getCompletionCase())) {
        throw ProtocolException.entryDoesNotMatch(expected, actual);
      }
    }

    @Override
    public boolean hasResult(CompletePromiseCommandMessage actual) {
      return actual.getResultCase()
          != Protocol.CompletePromiseCommandMessage.ResultCase.RESULT_NOT_SET;
    }

    @Override
    public Result<Void> parseEntryResult(CompletePromiseCommandMessage actual) {
      if (actual.getResultCase() == CompletePromiseCommandMessage.ResultCase.FAILURE) {
        return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
      } else if (actual.getResultCase() == CompletePromiseCommandMessage.ResultCase.EMPTY) {
        return Result.empty();
      } else {
        throw new IllegalStateException("CompletePromiseEntry has not been completed.");
      }
    }

    @Override
    public Result<Void> parseCompletionResult(CompletionMessage actual) {
      if (actual.getResultCase() == CompletionMessage.ResultCase.EMPTY) {
        return Result.empty();
      } else if (actual.getResultCase() == CompletionMessage.ResultCase.FAILURE) {
        return Result.failure(ExceptionUtils.toRestateException(actual.getFailure()));
      }
      return super.parseCompletionResult(actual);
    }
  }

  static final class CompleteAwakeableEntry extends JournalEntry<CompleteAwakeableCommandMessage> {

    static final CompleteAwakeableEntry INSTANCE = new CompleteAwakeableEntry();

    private CompleteAwakeableEntry() {}

    @Override
    public void trace(CompleteAwakeableCommandMessage expected, Span span) {
      span.addEvent("CompleteAwakeable");
    }

    @Override
    String getName(CompleteAwakeableCommandMessage expected) {
      return expected.getName();
    }

    @Override
    void checkEntryHeader(CompleteAwakeableCommandMessage expected, MessageLite actual)
        throws ProtocolException {
      ExceptionUtils.assertEntryEquals(expected, actual);
    }
  }
}
