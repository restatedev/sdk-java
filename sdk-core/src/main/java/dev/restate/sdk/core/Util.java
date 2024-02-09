// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.MessageLite;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.TerminalException;
import io.grpc.Status;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class Util {
  private Util() {}

  static Status SUSPENDED_STATUS = Status.INTERNAL.withCause(AbortedExecutionException.INSTANCE);

  @SuppressWarnings("unchecked")
  static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
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
        return (Optional) Optional.of(currentThrowable);
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

  static Protocol.Failure toProtocolFailure(TerminalException.Code code, String message) {
    Protocol.Failure.Builder builder = Protocol.Failure.newBuilder().setCode(code.value());
    if (message != null) {
      builder.setMessage(message);
    }
    return builder.build();
  }

  static Protocol.Failure toProtocolFailure(Throwable throwable) {
    if (throwable instanceof TerminalException) {
      return toProtocolFailure(((TerminalException) throwable).getCode(), throwable.getMessage());
    }
    return toProtocolFailure(TerminalException.Code.UNKNOWN, throwable.toString());
  }

  static TerminalException toRestateException(Protocol.Failure failure) {
    return new TerminalException(
        TerminalException.Code.fromValue(failure.getCode()), failure.getMessage());
  }

  static boolean isTerminalException(Throwable throwable) {
    return throwable instanceof TerminalException;
  }

  static void assertIsEntry(MessageLite msg) {
    if (!isEntry(msg)) {
      throw new IllegalStateException("Expected input to be entry: " + msg);
    }
  }

  static void assertEntryEquals(MessageLite expected, MessageLite actual) {
    if (!Objects.equals(expected, actual)) {
      throw ProtocolException.entryDoesNotMatch(expected, actual);
    }
  }

  static void assertEntryClass(Class<? extends MessageLite> clazz, MessageLite actual) {
    if (!clazz.equals(actual.getClass())) {
      throw ProtocolException.unexpectedMessage(clazz, actual);
    }
  }

  static boolean isEntry(MessageLite msg) {
    return msg instanceof Protocol.PollInputStreamEntryMessage
        || msg instanceof Protocol.OutputStreamEntryMessage
        || msg instanceof Protocol.GetStateEntryMessage
        || msg instanceof Protocol.GetStateKeysEntryMessage
        || msg instanceof Protocol.SetStateEntryMessage
        || msg instanceof Protocol.ClearStateEntryMessage
        || msg instanceof Protocol.ClearAllStateEntryMessage
        || msg instanceof Protocol.SleepEntryMessage
        || msg instanceof Protocol.InvokeEntryMessage
        || msg instanceof Protocol.BackgroundInvokeEntryMessage
        || msg instanceof Protocol.AwakeableEntryMessage
        || msg instanceof Protocol.CompleteAwakeableEntryMessage
        || msg instanceof Java.CombinatorAwaitableEntryMessage
        || msg instanceof Java.SideEffectEntryMessage;
  }
}
