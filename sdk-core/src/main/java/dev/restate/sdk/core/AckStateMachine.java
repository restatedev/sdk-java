// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

/** State machine tracking acks */
class AckStateMachine extends BaseSuspendableCallbackStateMachine<AckStateMachine.AckCallback> {

  interface AckCallback extends SuspendableCallback {
    void onAck();
  }

  private int lastAcknowledgedEntry = -1;

  /** -1 means no side effect waiting to be acked. */
  private int lastEntryToAck = -1;

  void waitLastAck(AckCallback callback) {
    if (lastEntryIsAcked()) {
      callback.onAck();
    } else {
      this.setCallback(callback);
    }
  }

  void tryHandleAck(int entryIndex) {
    this.lastAcknowledgedEntry = Math.max(entryIndex, this.lastAcknowledgedEntry);
    if (lastEntryIsAcked()) {
      this.consumeCallback(AckCallback::onAck);
    }
  }

  void registerEntryToAck(int entryIndex) {
    this.lastEntryToAck = Math.max(entryIndex, this.lastEntryToAck);
  }

  private boolean lastEntryIsAcked() {
    return this.lastEntryToAck <= this.lastAcknowledgedEntry;
  }

  public int getLastEntryToAck() {
    return lastEntryToAck;
  }

  @Override
  void abort(Throwable cause) {
    super.abort(cause);
    // We can't do anything else if the input stream is closed, so we just fail the callback, if any
    this.tryFailCallback();
  }
}
