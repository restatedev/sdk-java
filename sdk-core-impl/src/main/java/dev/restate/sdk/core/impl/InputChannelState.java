package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.SuspendedException;
import javax.annotation.Nullable;

class InputChannelState {

  interface SuspendableCallback {

    void onSuspend();

    void onError(Throwable e);
  }

  private @Nullable Throwable closeCause = null;

  /**
   * @return false if it was already closed.
   */
  boolean close(Throwable cause) {
    // Guard against multiple requests of transitions to suspended
    if (this.closeCause != null) {
      return false;
    }
    closeCause = cause;
    return true;
  }

  /** Consumes the callback if the state is closed or suspended, otherwise returns it. */
  <CB extends SuspendableCallback> @Nullable CB handleOrReturn(CB callback) {
    if (isSuspended()) {
      callback.onSuspend();
      return null;
    } else if (isClosed()) {
      callback.onError(closeCause);
      return null;
    }
    return callback;
  }

  private boolean isSuspended() {
    return this.closeCause == SuspendedException.INSTANCE;
  }

  boolean isClosed() {
    return this.closeCause != null;
  }
}
