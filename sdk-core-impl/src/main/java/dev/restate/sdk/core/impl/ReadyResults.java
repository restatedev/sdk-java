// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.TerminalException;
import dev.restate.sdk.core.syscalls.ReadyResult;
import java.util.function.Function;
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

  static <T> ReadyResultInternal<T> failure(TerminalException t) {
    return new Failure<>(t);
  }

  interface ReadyResultInternal<T> extends ReadyResult<T> {}

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

    @SuppressWarnings("unchecked")
    @Override
    public <U> ReadyResult<U> map(Function<T, U> mapper) {
      return (ReadyResult<U>) this;
    }

    @Nullable
    @Override
    public TerminalException getFailure() {
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

    @Override
    public <U> ReadyResult<U> map(Function<T, U> mapper) {
      return new Success<>(mapper.apply(value));
    }

    @Nullable
    @Override
    public TerminalException getFailure() {
      return null;
    }
  }

  static class Failure<T> implements ReadyResultInternal<T> {
    private final TerminalException cause;

    private Failure(TerminalException cause) {
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

    @SuppressWarnings("unchecked")
    @Override
    public <U> ReadyResult<U> map(Function<T, U> mapper) {
      return (ReadyResult<U>) this;
    }

    @Nullable
    @Override
    public TerminalException getFailure() {
      return cause;
    }
  }
}
