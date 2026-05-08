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
import dev.restate.common.Slice;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

sealed interface State
    permits ClosedState,
        ProcessingState,
        ReplayingState,
        WaitingReplayEntriesState,
        WaitingStartState {

  Logger LOG = LogManager.getLogger(State.class);

  default void onNewMessage(
      InvocationInput invocationInput,
      StateContext stateContext,
      CompletableFuture<Void> waitForReadyFuture) {
    throw ProtocolException.badState(this);
  }

  default StateMachine.AwaitResponse doAwait(UnresolvedFuture future, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default boolean isCompleted(int handle) {
    throw ProtocolException.badState(this);
  }

  default Optional<NotificationValue> takeNotification(int handle, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default StateMachine.@Nullable Input processInputCommand(StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default int processStateGetCommand(String key, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default int processStateGetKeysCommand(StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default <E extends MessageLite> void processNonCompletableCommand(
      E commandMessage, CommandAccessor<E> commandAccessor, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default <E extends MessageLite> int[] processCompletableCommand(
      E commandMessage,
      CommandAccessor<E> commandAccessor,
      int[] completionIds,
      StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default int createSignalHandle(NotificationId notificationId, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default int processRunCommand(String name, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default void proposeRunCompletion(int handle, Slice value, StateContext stateContext) {
    LOG.warn(
        "Going to ignore proposed run completion with handle {} because the state machine is not in processing state.",
        handle);
  }

  default void proposeRunCompletion(
      int handle,
      Throwable exception,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy,
      StateContext stateContext) {
    LOG.warn(
        "Going to ignore proposed run completion with handle {} because the state machine is not in processing state.",
        handle);
  }

  default void hitError(
      Throwable throwable,
      @Nullable CommandRelationship relatedCommand,
      @Nullable Duration nextRetryDelay,
      StateContext stateContext) {
    LOG.warn("Invocation failed", throwable);

    var errorMessageBuilder = Protocol.ErrorMessage.newBuilder();

    // Figure out message
    if (throwable.getMessage() == null) {
      // This happens only with few common exceptions, but anyway
      errorMessageBuilder.setMessage(throwable.toString());
    } else {
      errorMessageBuilder.setMessage(throwable.getMessage());
    }

    // Figure out code
    if (throwable instanceof ProtocolException) {
      errorMessageBuilder.setCode(((ProtocolException) throwable).getCode());
    } else {
      errorMessageBuilder.setCode(TerminalException.INTERNAL_SERVER_ERROR_CODE);
    }

    // Convert stacktrace to string
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    errorMessageBuilder.setStacktrace(sw.toString());

    // Add command metadata, if any
    CommandMetadata commandMetadata =
        (relatedCommand != null)
            ? stateContext.getJournal().resolveRelatedCommand(relatedCommand)
            : null;
    if (commandMetadata != null) {
      if (commandMetadata.index() >= 0) {
        errorMessageBuilder.setRelatedCommandIndex(commandMetadata.index());
      }
      if (commandMetadata.name() != null) {
        errorMessageBuilder.setRelatedCommandName(commandMetadata.name());
      }
      if (commandMetadata.type() != null) {
        errorMessageBuilder.setRelatedCommandType(commandMetadata.type().encode());
      }
    }

    // Add next retry delay, if any
    if (nextRetryDelay != null) {
      errorMessageBuilder.setNextRetryDelay(nextRetryDelay.toMillis());
    }

    stateContext.maybeWriteMessageOut(errorMessageBuilder.build());
    stateContext.getStateHolder().transition(new ClosedState());

    stateContext.closeOutputSubscriber();
  }

  default void hitSuspended(
      UnresolvedFuture awaitingOn, AsyncResultsState asyncResultsState, StateContext stateContext) {
    LOG.info("Invocation suspended");
    LOG.debug("Awaiting on {}", awaitingOn);

    Protocol.SuspensionMessage suspensionMessage;
    if (stateContext.getNegotiatedProtocolVersion().getNumber()
        >= Protocol.ServiceProtocolVersion.V7_VALUE) {
      var future = asyncResultsState.resolveUnresolvedFuture(awaitingOn);
      suspensionMessage = Protocol.SuspensionMessage.newBuilder().setAwaitingOn(future).build();
    } else {
      // V6 format: flatten the future tree into the flat waiting_* lists
      suspensionMessage = buildV6SuspensionMessage(awaitingOn, asyncResultsState);
    }

    stateContext.maybeWriteMessageOut(suspensionMessage);
    stateContext.getStateHolder().transition(new ClosedState());

    stateContext.closeOutputSubscriber();
  }

  private static Protocol.SuspensionMessage buildV6SuspensionMessage(
      UnresolvedFuture awaitingOn, AsyncResultsState asyncResultsState) {
    var builder = Protocol.SuspensionMessage.newBuilder();
    for (int handle : awaitingOn.handles()) {
      var notifId = asyncResultsState.resolveNotificationHandles(java.util.List.of(handle));
      for (var id : notifId) {
        if (id instanceof NotificationId.CompletionId c) {
          builder.addWaitingCompletions(c.id());
        } else if (id instanceof NotificationId.SignalId s) {
          builder.addWaitingSignals(s.id());
        } else if (id instanceof NotificationId.SignalName n) {
          builder.addWaitingNamedSignals(n.name());
        }
      }
    }
    return builder.build();
  }

  default void end(StateContext stateContext) {
    LOG.info("Invocation ended");

    stateContext.writeMessageOut(Protocol.EndMessage.getDefaultInstance());
    stateContext.getStateHolder().transition(new ClosedState());

    stateContext.closeOutputSubscriber();
  }

  default void onInputClosed(StateContext stateContext) {
    LOG.trace("Marking input closed");
    stateContext.markInputClosed();
  }

  InvocationState getInvocationState();
}
