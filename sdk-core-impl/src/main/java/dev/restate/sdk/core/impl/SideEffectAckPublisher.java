package dev.restate.sdk.core.impl;

import javax.annotation.Nullable;

class SideEffectAckPublisher {

  interface OnEnterSideEffectCallback extends InputChannelState.SuspendableCallback {
    void onEnter();
  }

  private int lastAcknowledgedEntry = -1;
  /** -1 means no side effect waiting to be acked. */
  private int lastExecutedSideEffect = -1;

  private @Nullable OnEnterSideEffectCallback onEnterSideEffectCallback;
  private final InputChannelState state = new InputChannelState();

  void executeEnterSideEffect(OnEnterSideEffectCallback callback) {
    if (canExecuteSideEffect()) {
      callback.onEnter();
    } else {
      this.onEnterSideEffectCallback = state.handleOrReturn(callback);
    }
  }

  void tryHandleSideEffectAck(int entryIndex) {
    this.lastAcknowledgedEntry = Math.max(entryIndex, this.lastAcknowledgedEntry);
    if (canExecuteSideEffect()) {
      tryInvokeCallback();
    }
  }

  void registerExecutedSideEffect(int entryIndex) {
    this.lastExecutedSideEffect = entryIndex;
  }

  void abort(Throwable e) {
    if (this.state.close(e)) {
      tryFailCallback();
    }
  }

  private void tryInvokeCallback() {
    if (this.onEnterSideEffectCallback != null) {
      OnEnterSideEffectCallback cb = this.onEnterSideEffectCallback;
      this.onEnterSideEffectCallback = null;
      cb.onEnter();
    }
  }

  private void tryFailCallback() {
    if (this.onEnterSideEffectCallback != null) {
      OnEnterSideEffectCallback cb = this.onEnterSideEffectCallback;
      this.onEnterSideEffectCallback = null;
      state.handleOrReturn(cb);
    }
  }

  private boolean canExecuteSideEffect() {
    return this.lastExecutedSideEffect <= this.lastAcknowledgedEntry;
  }

  public int getLastExecutedSideEffect() {
    return lastExecutedSideEffect;
  }
}
