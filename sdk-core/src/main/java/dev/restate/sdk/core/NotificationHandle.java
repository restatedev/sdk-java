// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.Output;
import dev.restate.common.Slice;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.endpoint.definition.HandlerContext.Result;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@link HandlerContext.Handle} implementation, wrapping the {@link StateMachine} handle together
 * with the conversion of its notification to the operation result.
 */
record NotificationHandle<T>(
    int value, Function<StateMachine.NotificationValue, Result<T>> converter)
    implements HandlerContext.Handle<T> {

  /**
   * @throws ProtocolException if the notification variant is not expected for this operation.
   */
  Result<T> toResult(StateMachine.NotificationValue notificationValue) {
    return converter.apply(notificationValue);
  }

  static NotificationHandle<Void> empty(int handle) {
    return new NotificationHandle<>(
        handle,
        value -> {
          if (value instanceof StateMachine.NotificationValue.Empty) {
            return new Result.Success<>(null);
          }
          throw ProtocolException.unexpectedNotificationVariant(value.getClass());
        });
  }

  static NotificationHandle<Optional<Slice>> emptyOrSuccess(int handle) {
    return new NotificationHandle<>(
        handle,
        value -> {
          if (value instanceof StateMachine.NotificationValue.Empty) {
            return new Result.Success<>(Optional.empty());
          } else if (value instanceof StateMachine.NotificationValue.Success success) {
            return new Result.Success<>(Optional.of(success.slice()));
          }
          throw ProtocolException.unexpectedNotificationVariant(value.getClass());
        });
  }

  static NotificationHandle<Slice> successOrFailure(int handle) {
    return new NotificationHandle<>(
        handle,
        value -> {
          if (value instanceof StateMachine.NotificationValue.Success success) {
            return new Result.Success<>(success.slice());
          } else if (value instanceof StateMachine.NotificationValue.Failure failure) {
            return new Result.Failure<>(failure.exception());
          }
          throw ProtocolException.unexpectedNotificationVariant(value.getClass());
        });
  }

  static NotificationHandle<Output<Slice>> emptyOrSuccessOrFailure(int handle) {
    return new NotificationHandle<>(
        handle,
        value -> {
          if (value instanceof StateMachine.NotificationValue.Empty) {
            return new Result.Success<>(Output.notReady());
          } else if (value instanceof StateMachine.NotificationValue.Success success) {
            return new Result.Success<>(Output.ready(success.slice()));
          } else if (value instanceof StateMachine.NotificationValue.Failure failure) {
            return new Result.Failure<>(failure.exception());
          }
          throw ProtocolException.unexpectedNotificationVariant(value.getClass());
        });
  }

  static NotificationHandle<Void> emptyOrFailure(int handle) {
    return new NotificationHandle<>(
        handle,
        value -> {
          if (value instanceof StateMachine.NotificationValue.Empty) {
            return new Result.Success<>(null);
          } else if (value instanceof StateMachine.NotificationValue.Failure failure) {
            return new Result.Failure<>(failure.exception());
          }
          throw ProtocolException.unexpectedNotificationVariant(value.getClass());
        });
  }

  static NotificationHandle<String> invocationId(int handle) {
    return new NotificationHandle<>(
        handle,
        value -> {
          if (value instanceof StateMachine.NotificationValue.InvocationId invocationId) {
            return new Result.Success<>(invocationId.invocationId());
          }
          throw ProtocolException.unexpectedNotificationVariant(value.getClass());
        });
  }

  static NotificationHandle<Collection<String>> stateKeys(int handle) {
    return new NotificationHandle<>(
        handle,
        value -> {
          if (value instanceof StateMachine.NotificationValue.StateKeys stateKeys) {
            return new Result.Success<>(stateKeys.stateKeys());
          }
          throw ProtocolException.unexpectedNotificationVariant(value.getClass());
        });
  }
}
