package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.SuspendedException;
import io.grpc.*;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

public final class Util {
  private Util() {}

  static Status SUSPENDED_STATUS = Status.INTERNAL.withCause(SuspendedException.INSTANCE);

  static boolean isGoogleProtobufEmpty(Object o) {
    return o.getClass().getName().equals("com.google.protobuf.Empty");
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
    return findCause(throwable, t -> t == SuspendedException.INSTANCE).isPresent();
  }

  static Protocol.Failure toProtocolFailure(Status status) {
    Protocol.Failure.Builder builder =
        Protocol.Failure.newBuilder().setCode(status.getCode().value());
    if (status.getDescription() != null) {
      builder.setMessage(status.getDescription());
    }
    return builder.build();
  }

  static Protocol.Failure toProtocolFailure(Throwable throwable) {
    return toProtocolFailure(toGrpcStatusErasingCause(throwable));
  }

  static Status toGrpcStatus(Protocol.Failure failure) {
    return Status.fromCodeValue(failure.getCode()).withDescription(failure.getMessage());
  }

  static Status toGrpcStatusErasingCause(Throwable throwable) {
    // Here we need to erase the cause, as it's not stored in the call result structure and can
    // cause non-determinism.
    if (throwable instanceof StatusException) {
      return ((StatusException) throwable).getStatus().withCause(null);
    } else if (throwable instanceof StatusRuntimeException) {
      return ((StatusRuntimeException) throwable).getStatus().withCause(null);
    }

    return Status.UNKNOWN.withDescription(throwable.getMessage());
  }

  static void assertIsEntry(MessageLite msg) {
    if (!isEntry(msg)) {
      throw new IllegalStateException("Expected input to be entry");
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
        || msg instanceof Protocol.SetStateEntryMessage
        || msg instanceof Protocol.ClearStateEntryMessage
        || msg instanceof Protocol.SleepEntryMessage
        || msg instanceof Protocol.InvokeEntryMessage
        || msg instanceof Protocol.BackgroundInvokeEntryMessage
        || msg instanceof Protocol.SideEffectEntryMessage
        || msg instanceof Protocol.AwakeableEntryMessage
        || msg instanceof Protocol.CompleteAwakeableEntryMessage
        || msg instanceof Java.CombinatorAwaitableEntryMessage;
  }

  @SuppressWarnings("unchecked")
  static <T extends MessageLite> @Nullable ProtocolException checkEntryClassAndHeader(
      MessageLite actualMsg,
      Class<? extends MessageLite> clazz,
      Function<T, ProtocolException> checkEntryHeader) {
    if (!clazz.equals(actualMsg.getClass())) {
      return ProtocolException.unexpectedMessage(clazz, actualMsg);
    }
    T actualEntry = (T) actualMsg;
    return checkEntryHeader.apply(actualEntry);
  }
}
