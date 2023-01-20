package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.syscalls.SyscallCallback;
import javax.annotation.Nullable;

class SideEffectAckPublisher {

  private int lastAcknowledgedEntry = -1;
  /** -1 means no side effect waiting to be acked. */
  private int lastExecutedSideEffect = -1;

  @Nullable private SyscallCallback<Void> sideEffectAckCallback;
  private boolean inputClosed = false;

  void executeEnterSideEffect(SyscallCallback<Void> syscallCallback) {
    if (canExecuteSideEffect()) {
      syscallCallback.onSuccess(null);
    } else {
      if (this.inputClosed) {
        syscallCallback.onCancel(SuspendedException.INSTANCE);
      } else {
        this.sideEffectAckCallback = syscallCallback;
      }
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
    if (this.inputClosed) {
      return;
    }
    this.inputClosed = true;
    tryFailCallback(e);
  }

  private void tryInvokeCallback() {
    if (this.sideEffectAckCallback != null) {
      SyscallCallback<Void> cb = this.sideEffectAckCallback;
      this.sideEffectAckCallback = null;
      cb.onSuccess(null);
    }
  }

  private void tryFailCallback(Throwable e) {
    if (this.sideEffectAckCallback != null) {
      SyscallCallback<Void> cb = this.sideEffectAckCallback;
      this.sideEffectAckCallback = null;
      cb.onCancel(e);
    }
  }

  private boolean canExecuteSideEffect() {
    return this.lastExecutedSideEffect <= this.lastAcknowledgedEntry;
  }
}
