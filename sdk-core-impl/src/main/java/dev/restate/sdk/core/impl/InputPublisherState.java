package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.AbortedExecutionException;
import javax.annotation.Nullable;

class InputPublisherState {

  private @Nullable Throwable closeCause = null;

  void notifyClosed(Throwable cause) {
    closeCause = cause;
  }

  boolean isSuspended() {
    return this.closeCause == AbortedExecutionException.INSTANCE;
  }

  boolean isClosed() {
    return this.closeCause != null;
  }

  public Throwable getCloseCause() {
    return closeCause;
  }
}
