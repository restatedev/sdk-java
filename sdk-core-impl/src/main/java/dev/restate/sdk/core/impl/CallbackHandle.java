package dev.restate.sdk.core.impl;

import java.util.function.Consumer;
import javax.annotation.Nullable;

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
