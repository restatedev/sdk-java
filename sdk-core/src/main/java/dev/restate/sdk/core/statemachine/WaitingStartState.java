// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.types.TerminalException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

final class WaitingStartState implements State {

  @Override
  public void onNewMessage(
      InvocationInput invocationInput,
      StateContext stateContext,
      CompletableFuture<Void> waitForReadyFuture) {
    if (!(invocationInput.message() instanceof Protocol.StartMessage startMessage)) {
      throw ProtocolException.unexpectedMessage(
          Protocol.StartMessage.class, invocationInput.message());
    }

    // Sanity checks
    if (startMessage.getKnownEntries() == 0) {
      throw new ProtocolException(
          "Expected at least one entry with Input, got 0 entries",
          TerminalException.INTERNAL_SERVER_ERROR_CODE);
    }

    // Register start info and eager state
    stateContext.setStartInfo(
        new StartInfo(
            startMessage.getId(),
            startMessage.getDebugId(),
            startMessage.getDebugId(),
            startMessage.getKnownEntries(),
            startMessage.getRetryCountSinceLastStoredEntry(),
            Duration.ofMillis(startMessage.getDurationSinceLastStoredEntry())));
    stateContext.setEagerState(new EagerState(startMessage));

    // Tracing and logging setup
    LOG.info("Start invocation");

    // Execute state transition
    stateContext.getStateHolder().transition(new WaitingReplayEntriesState());
  }

  @Override
  public void onInputClosed(StateContext stateContext) {
    throw ProtocolException.inputClosedWhileWaitingEntries();
  }

  @Override
  public void end(StateContext stateContext) {
    throw ProtocolException.closedWhileWaitingEntries();
  }

  @Override
  public InvocationState getInvocationState() {
    return InvocationState.WAITING_START;
  }
}
