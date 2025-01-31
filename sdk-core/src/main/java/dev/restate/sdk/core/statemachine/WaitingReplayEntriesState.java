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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.jspecify.annotations.Nullable;

final class WaitingReplayEntriesState implements State {

  private int receivedEntries = 0;
  private final Deque<MessageLite> commandsToProcess = new ArrayDeque<>();
  private final AsyncResultsState asyncResultsState = new AsyncResultsState();

  @Override
  public void onNewMessage(
      InvocationInput invocationInput,
      StateContext stateContext,
      CompletableFuture<Void> waitForReadyFuture) {
    if (invocationInput.header().getType().isNotification()) {
      if (!(invocationInput.message()
          instanceof Protocol.NotificationTemplate notificationTemplate)) {
        throw ProtocolException.unexpectedMessage(
            Protocol.NotificationTemplate.class, invocationInput.message());
      }

      this.asyncResultsState.enqueue(notificationTemplate);
    } else if (invocationInput.header().getType().isCommand()) {
      this.commandsToProcess.add(invocationInput.message());
    } else {
      throw ProtocolException.unexpectedMessage(
          "command or notification", invocationInput.message());
    }

    this.receivedEntries++;

    if (stateContext.getStartInfo().entriesToReplay() == this.receivedEntries) {
      stateContext
          .getStateHolder()
          .transition(new ReplayingState(commandsToProcess, asyncResultsState));
      waitForReadyFuture.complete(null);
    }
  }

  @Override
  public void onInputClosed(
      StateContext stateContext) {
    throw ProtocolException.inputClosedWhileWaitingEntries();
  }

  @Override
  public void end(
      StateContext stateContext) {
    throw ProtocolException.closedWhileWaitingEntries();
  }

  @Override
  public InvocationState getInvocationState() {
    return InvocationState.WAITING_START;
  }
}
