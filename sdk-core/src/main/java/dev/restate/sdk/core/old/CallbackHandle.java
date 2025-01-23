// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.old;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Handle for callbacks. */
final class CallbackHandle<T> {

  private @Nullable T cb = null;

  public void set(T t) {
    this.cb = t;
  }

  public boolean isEmpty() {
    return this.cb == null;
  }

  public void consume(Consumer<T> consumer) {
    if (this.cb != null) {
      consumer.accept(pop());
    }
  }

  public void consumeOrElse(Consumer<T> consumer, Runnable elseRunnable) {
    if (this.cb != null) {
      consumer.accept(pop());
    } else {
      elseRunnable.run();
    }
  }

  private @Nullable T pop() {
    T temp = this.cb;
    this.cb = null;
    return temp;
  }
}
