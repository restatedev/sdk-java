// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.TerminalException;
import java.util.Optional;
import java.util.function.Predicate;

public final class ExceptionUtils {
  private ExceptionUtils() {}

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }

  /**
   * Finds a throwable fulfilling the condition in the cause chain of the given throwable. If there
   * is none, then the method returns an empty optional.
   *
   * @param throwable to check for the given condition
   * @param condition condition that a cause needs to fulfill
   * @return Some cause that fulfills the condition; otherwise an empty optional
   */
  @SuppressWarnings("unchecked")
  static <T extends Throwable> Optional<T> findCause(
      Throwable throwable, Predicate<? super Throwable> condition) {
    Throwable currentThrowable = throwable;

    while (currentThrowable != null) {
      if (condition.test(currentThrowable)) {
        return (Optional<T>) Optional.of(currentThrowable);
      }

      if (currentThrowable == currentThrowable.getCause()) {
        break;
      } else {
        currentThrowable = currentThrowable.getCause();
      }
    }

    return Optional.empty();
  }

  public static Optional<ProtocolException> findProtocolException(Throwable throwable) {
    return findCause(throwable, t -> t instanceof ProtocolException);
  }

  public static boolean containsSuspendedException(Throwable throwable) {
    return findCause(throwable, t -> t == AbortedExecutionException.INSTANCE).isPresent();
  }

  public static boolean isTerminalException(Throwable throwable) {
    return throwable instanceof TerminalException;
  }
}
