package dev.restate.sdk.core.impl;

/** State machine tracking side effects acks */
class SideEffectAckStateMachine
    extends BaseSuspendableCallbackStateMachine<SideEffectAckStateMachine.SafePointCallback> {

  interface SafePointCallback extends SuspendableCallback {
    void onSafePoint();
  }

  private int lastAcknowledgedEntry = -1;

  /** -1 means no side effect waiting to be acked. */
  private int lastExecutedSideEffect = -1;

  // Wait for a safe point to execute the next side effect
  void waitSafePoint(SafePointCallback callback) {
    if (canExecuteSideEffect()) {
      callback.onSafePoint();
    } else {
      this.setCallback(callback);
    }
  }

  void tryHandleSideEffectAck(int entryIndex) {
    this.lastAcknowledgedEntry = Math.max(entryIndex, this.lastAcknowledgedEntry);
    if (canExecuteSideEffect()) {
      this.consumeCallback(SafePointCallback::onSafePoint);
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
