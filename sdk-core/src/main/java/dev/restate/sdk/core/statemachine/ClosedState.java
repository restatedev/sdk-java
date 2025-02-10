// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

final class ClosedState implements State {

  @Override
  public void hitError(
      Throwable throwable, @Nullable Duration nextRetryDelay, StateContext stateContext) {
    // Ignore, as we closed already
  }

  @Override
  public void end(StateContext stateContext) {
    // Ignore, as we closed already
  }

  @Override
  public InvocationState getInvocationState() {
    return InvocationState.CLOSED;
  }
}
