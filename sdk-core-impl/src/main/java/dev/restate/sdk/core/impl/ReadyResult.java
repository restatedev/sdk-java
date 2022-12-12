package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.syscalls.DeferredResultCallback;

abstract class ReadyResult<T> {

  abstract void publish(DeferredResultCallback<T> subscription);

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

  static class Empty<T> extends ReadyResult<T> {

    public static Empty<?> INSTANCE = new Empty<>();

    private Empty() {}

    @Override
    public void publish(DeferredResultCallback<T> subscription) {
      subscription.onEmptyResult();
    }
  }

  static class Success<T> extends ReadyResult<T> {
    private final T value;

    private Success(T value) {
      this.value = value;
    }

    @Override
    public void publish(DeferredResultCallback<T> subscription) {
      subscription.onResult(value);
    }
  }

  static class Failure<T> extends ReadyResult<T> {
    private final Throwable cause;

    private Failure(Throwable cause) {
      this.cause = cause;
    }

    @Override
    public void publish(DeferredResultCallback<T> subscription) {
      subscription.onFailure(cause);
    }
  }
}
