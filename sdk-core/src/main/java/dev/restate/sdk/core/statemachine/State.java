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
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.Slice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

sealed interface State permits ClosedState, ProcessingState, ReplayingState, WaitingReplayEntriesState, WaitingStartState {

  Logger LOG = LogManager.getLogger(State.class);

  default void onNewMessage(InvocationInput invocationInput, StateContext stateContext, CompletableFuture<Void> waitForReadyFuture) {
    throw ProtocolException.badState(this);
  }

  default StateMachine.DoProgressResponse doProgress(List<Integer> anyHandle, StateContext stateContext) {
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

  default <E extends MessageLite> void processNonCompletableCommand(E commandMessage, CommandAccessor<E> commandAccessor, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default <E extends MessageLite> int[] processCompletableCommand(E commandMessage, CommandAccessor<E> commandAccessor, int[] completionIds, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default int createSignalHandle(NotificationId notificationId, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default int processRunCommand(String name, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default void proposeRunCompletion(int handle, Slice value, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default void proposeRunCompletion(int handle, Throwable exception, @Nullable RetryPolicy retryPolicy, StateContext stateContext) {
    throw ProtocolException.badState(this);
  }

  default void hitError(Throwable throwable, StateContext stateContext) {
    LOG.warn("Invocation failed", throwable);
    stateContext.maybeWriteMessageOut(Util.toErrorMessage(
            throwable,
            stateContext.getJournal().getCommandIndex(),
            stateContext.getJournal().getCurrentEntryName(),
            stateContext.getJournal().getCurrentEntryTy()
    ));
    stateContext.closeOutputSubscriber();
    stateContext.getStateHolder().transition(new ClosedState());
  }

  default void hitSuspended(Collection<NotificationId> awaitingOn, StateContext stateContext) {
    LOG.info("Invocation suspended awaiting on {}", awaitingOn);

    var suspensionMessageBuilder = Protocol.SuspensionMessage.newBuilder();
    for (var notificationId: awaitingOn) {
      if (notificationId instanceof NotificationId.CompletionId completionId) {
        suspensionMessageBuilder.addWaitingCompletions(completionId.id());
      } else if (notificationId instanceof NotificationId.SignalId signalId) {
        suspensionMessageBuilder.addWaitingSignals(signalId.id());
      } else if (notificationId instanceof NotificationId.SignalName signalName) {
        suspensionMessageBuilder.addWaitingNamedSignals(signalName.name());
      }
    }

    stateContext.maybeWriteMessageOut(
      suspensionMessageBuilder.build()
    );
    stateContext.closeOutputSubscriber();
    stateContext.getStateHolder().transition(new ClosedState());
  }

  default void end(StateContext stateContext) {
    LOG.info("Invocation ended");

    stateContext.writeMessageOut(Protocol.EndMessage.getDefaultInstance());
    stateContext.closeOutputSubscriber();
    stateContext.getStateHolder().transition(new ClosedState());
  }

  default void onInputClosed(StateContext stateContext) {
    stateContext.markInputClosed();
  }

  InvocationState getInvocationState();
}
