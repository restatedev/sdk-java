// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import dev.restate.sdk.core.EndpointRequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class StateHolder {

  Logger LOG = LogManager.getLogger(StateHolder.class);

  private State state;
  private final EndpointRequestHandler.LoggingContextSetter loggingContextSetter;

  StateHolder(EndpointRequestHandler.LoggingContextSetter loggingContextSetter) {
      this.loggingContextSetter = loggingContextSetter;
      this.state = new WaitingStartState();
  }

  State getState() {
    return state;
  }

  void transition(State state) {
    this.state = state;
    LOG.debug("Transitioning state machine to {}", state.getInvocationState());
    this.loggingContextSetter.set(
            EndpointRequestHandler.LoggingContextSetter.INVOCATION_STATUS_KEY, state.getInvocationState().toString());
  }
}
