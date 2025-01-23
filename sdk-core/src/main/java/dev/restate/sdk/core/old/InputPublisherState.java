// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.old;

import dev.restate.sdk.types.AbortedExecutionException;
import org.jspecify.annotations.Nullable;

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
