package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.syscalls.DeferredResult;
import dev.restate.sdk.core.syscalls.ReadyResult;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

class ResultTreeNodes {

  private ResultTreeNodes() {}

  @SuppressWarnings("unchecked")
  static <T> ReadyResult<T> ack() {
    return (ReadyResult<T>) Ack.INSTANCE;
  }

  @SuppressWarnings("unchecked")
  static <T> ReadyResult<T> empty() {
    return (ReadyResult<T>) Empty.INSTANCE;
  }

  static <T> ReadyResult<T> success(T value) {
    return new Success<>(value);
  }

  static <T> ReadyResult<T> failure(Throwable t) {
    return new Failure<>(t);
  }

  static <T> DeferredResult<T> waiting(
      int journalIndex,
      Function<Protocol.CompletionMessage, ReadyResult<T>> completionParser,
      Class<? extends MessageLite> inputEntryClass) {
    return new Waiting<>(journalIndex, completionParser, inputEntryClass);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static <T> DeferredResult<List<T>> any(List<DeferredResult<T>> any) {
    return new Any(any);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static <T> DeferredResult<List<T>> all(List<DeferredResult<T>> all) {
    return new All(all);
  }

  static class Ack implements ReadyResult<Object> {

    public static Ack INSTANCE = new Ack();

    private Ack() {}

    @Override
    public boolean isOk() {
      return true;
    }

    @Override
    public Object getResult() throws IllegalStateException {
      throw new IllegalStateException();
    }

    @Nullable
    @Override
    public Throwable getFailure() {
      throw new IllegalStateException();
    }
  }

  static class Empty<T> implements ReadyResult<T> {

    public static Empty<?> INSTANCE = new Empty<>();

    private Empty() {}

    @Override
    public boolean isOk() {
      return true;
    }

    @Override
    public T getResult() throws IllegalStateException {
      return null;
    }

    @Nullable
    @Override
    public Throwable getFailure() {
      return null;
    }
  }

  static class Success<T> implements ReadyResult<T> {
    private final T value;

    private Success(T value) {
      this.value = value;
    }

    @Override
    public boolean isOk() {
      return true;
    }

    @Override
    public T getResult() throws IllegalStateException {
      return value;
    }

    @Nullable
    @Override
    public Throwable getFailure() {
      return null;
    }
  }

  static class Failure<T> implements ReadyResult<T> {
    private final Throwable cause;

    private Failure(Throwable cause) {
      this.cause = cause;
    }

    @Override
    public boolean isOk() {
      return false;
    }

    @Override
    public T getResult() throws IllegalStateException {
      return null;
    }

    @Nullable
    @Override
    public Throwable getFailure() {
      return cause;
    }
  }

  static class Waiting<T> implements DeferredResult<T> {
    private final int journalIndex;
    private final Function<Protocol.CompletionMessage, ReadyResult<T>> completionParser;
    private final Class<? extends MessageLite> inputEntryClass;

    private Waiting(
        int journalIndex,
        Function<Protocol.CompletionMessage, ReadyResult<T>> completionParser,
        Class<? extends MessageLite> inputEntryClass) {
      this.journalIndex = journalIndex;
      this.completionParser = completionParser;
      this.inputEntryClass = inputEntryClass;
    }

    public int getJournalIndex() {
      return journalIndex;
    }

    public Function<Protocol.CompletionMessage, ReadyResult<T>> getCompletionParser() {
      return completionParser;
    }

    public Class<? extends MessageLite> getInputEntryClass() {
      return inputEntryClass;
    }
  }

  static class Any<T> implements DeferredResult<List<T>> {
    private final List<DeferredResult<T>> any;

    private Any(List<DeferredResult<T>> any) {
      this.any = any;
    }

    public List<DeferredResult<T>> getAny() {
      return any;
    }
  }

  static class All<T> implements DeferredResult<List<T>> {
    private final List<DeferredResult<T>> all;

    private All(List<DeferredResult<T>> all) {
      this.all = all;
    }

    public List<DeferredResult<T>> getAll() {
      return all;
    }
  }
}
