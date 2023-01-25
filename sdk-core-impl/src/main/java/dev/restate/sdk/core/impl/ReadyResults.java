package dev.restate.sdk.core.impl;

import io.grpc.StatusRuntimeException;
import javax.annotation.Nullable;

abstract class ReadyResults {

  private ReadyResults() {}

  @SuppressWarnings("unchecked")
  static <T> ReadyResultInternal<T> empty() {
    return (ReadyResultInternal<T>) Empty.INSTANCE;
  }

  static <T> ReadyResultInternal<T> success(T value) {
    return new Success<>(value);
  }

  static <T> ReadyResultInternal<T> failure(StatusRuntimeException t) {
    return new Failure<>(t);
  }

  static class Empty<T> implements ReadyResultInternal<T> {

    public static Empty<?> INSTANCE = new Empty<>();

    private Empty() {}

    @Override
    public boolean isSuccess() {
      return true;
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Nullable
    @Override
    public T getResult() {
      return null;
    }

    @Nullable
    @Override
    public StatusRuntimeException getFailure() {
      return null;
    }
  }

  static class Success<T> implements ReadyResultInternal<T> {
    private final T value;

    private Success(T value) {
      this.value = value;
    }

    @Override
    public boolean isSuccess() {
      return true;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Nullable
    @Override
    public T getResult() {
      return value;
    }

    @Nullable
    @Override
    public StatusRuntimeException getFailure() {
      return null;
    }
  }

  static class Failure<T> implements ReadyResultInternal<T> {
    private final StatusRuntimeException cause;

    private Failure(StatusRuntimeException cause) {
      this.cause = cause;
    }

    @Override
    public boolean isSuccess() {
      return false;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Nullable
    @Override
    public T getResult() {
      return null;
    }

    @Nullable
    @Override
    public StatusRuntimeException getFailure() {
      return cause;
    }
  }
}
