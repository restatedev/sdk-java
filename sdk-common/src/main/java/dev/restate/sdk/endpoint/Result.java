// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint;

import dev.restate.sdk.types.TerminalException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Result can be 3 valued:
 *
 * <ul>
 *   <li>Empty
 *   <li>Value
 *   <li>Failure
 * </ul>
 *
 * Empty and Value are used to distinguish the logical empty with the null result.
 *
 * <p>Failure in a ready result is always a user failure, and never a syscall failure, as opposed to
 * {@link SyscallCallback#onCancel(Throwable)}.
 *
 * @param <T> result type
 */
public abstract class Result<T> {

  private Result() {}

  /**
   * @return true if there is no failure.
   */
  public abstract boolean isSuccess();

  public abstract boolean isEmpty();

  /**
   * @return The success value, or null in case is empty.
   */
  @Nullable
  public abstract T getValue();

  @Nullable
  public abstract TerminalException getFailure();

  // --- Helper methods

  /**
   * Map this result success value. If the mapper throws an exception, this exception will be
   * converted to {@link TerminalException} and return a new failed {@link Result}.
   */
  public <U> Result<U> mapSuccess(Function<T, U> mapper) {
    if (this.isSuccess()) {
      try {
        return Result.success(mapper.apply(this.getValue()));
      } catch (TerminalException e) {
        return Result.failure(e);
      } catch (Exception e) {
        return Result.failure(
            new TerminalException(TerminalException.INTERNAL_SERVER_ERROR_CODE, e.getMessage()));
      }
    }
    //noinspection unchecked
    return (Result<U>) this;
  }

  // --- Factory methods

  @SuppressWarnings("unchecked")
  public static <T> Result<T> empty() {
    return (Result<T>) Empty.INSTANCE;
  }

  public static <T> Result<T> success(T value) {
    return new Success<>(value);
  }

  public static <T> Result<T> failure(TerminalException t) {
    return new Failure<>(t);
  }

  static class Empty<T> extends Result<T> {

    public static final Empty<?> INSTANCE = new Empty<>();

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
    public T getValue() {
      return null;
    }

    @Nullable
    @Override
    public TerminalException getFailure() {
      return null;
    }
  }

  static class Success<T> extends Result<T> {
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
    public T getValue() {
      return value;
    }

    @Nullable
    @Override
    public TerminalException getFailure() {
      return null;
    }
  }

  static class Failure<T> extends Result<T> {
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
    public T getValue() {
      return null;
    }

    @Nullable
    @Override
    public TerminalException getFailure() {
      return cause;
    }
  }
}
