// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.impl;

/** State machine tracking side effects acks */
class SideEffectAckStateMachine
    extends BaseSuspendableCallbackStateMachine<SideEffectAckStateMachine.SideEffectAckCallback> {

  interface SideEffectAckCallback extends SuspendableCallback {
    void onLastSideEffectAck();
  }

  private int lastAcknowledgedEntry = -1;

  /** -1 means no side effect waiting to be acked. */
  private int lastExecutedSideEffect = -1;

  void waitLastSideEffectAck(SideEffectAckCallback callback) {
    if (canExecuteSideEffect()) {
      callback.onLastSideEffectAck();
    } else {
      this.setCallback(callback);
    }
  }

  void tryHandleSideEffectAck(int entryIndex) {
    this.lastAcknowledgedEntry = Math.max(entryIndex, this.lastAcknowledgedEntry);
    if (canExecuteSideEffect()) {
      this.consumeCallback(SideEffectAckCallback::onLastSideEffectAck);
    }
  }

  void registerExecutedSideEffect(int entryIndex) {
    this.lastExecutedSideEffect = entryIndex;
  }

  private boolean canExecuteSideEffect() {
    return this.lastExecutedSideEffect <= this.lastAcknowledgedEntry;
  }

  public int getLastExecutedSideEffect() {
    return lastExecutedSideEffect;
  }

  @Override
  void abort(Throwable cause) {
    super.abort(cause);
    // We can't do anything else if the input stream is closed, so we just fail the callback, if any
    this.tryFailCallback();
  }
}
