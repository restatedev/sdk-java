// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.impl;

import java.util.function.Consumer;

// Implements the base logic for state machines containing suspensable callbacks.
abstract class BaseSuspendableCallbackStateMachine<CB extends SuspendableCallback> {

  private final CallbackHandle<CB> callbackHandle;
  private final InputPublisherState inputPublisherState;

  BaseSuspendableCallbackStateMachine() {
    this.callbackHandle = new CallbackHandle<>();
    this.inputPublisherState = new InputPublisherState();
  }

  void abort(Throwable cause) {
    this.inputPublisherState.notifyClosed(cause);
  }

  public void tryFailCallback() {
    callbackHandle.consume(
        cb -> {
          if (inputPublisherState.isSuspended()) {
            cb.onSuspend();
          } else if (inputPublisherState.isClosed()) {
            cb.onError(inputPublisherState.getCloseCause());
          }
        });
  }

  public void consumeCallback(Consumer<CB> consumer) {
    this.callbackHandle.consume(consumer);
  }

  public void consumeCallbackOrElse(Consumer<CB> consumer, Runnable elseRunnable) {
    this.callbackHandle.consumeOrElse(consumer, elseRunnable);
  }

  public void assertCallbackNotSet(String reason) {
    if (!this.callbackHandle.isEmpty()) {
      throw new IllegalStateException(reason);
    }
  }

  void setCallback(CB callback) {
    if (inputPublisherState.isSuspended()) {
      callback.onSuspend();
    } else if (inputPublisherState.isClosed()) {
      callback.onError(inputPublisherState.getCloseCause());
    } else {
      callbackHandle.set(callback);
    }
  }
}
