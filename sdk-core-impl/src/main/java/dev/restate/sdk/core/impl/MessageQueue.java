package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.SuspendedException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class MessageQueue {

  private final Queue<MessageLite> unprocessedMessages;

  private BiConsumer<MessageLite, Throwable> callback;
  private boolean closed;

  MessageQueue() {
    this.unprocessedMessages = new ArrayDeque<>();
    this.closed = false;
  }

  void offer(MessageLite msg) {
    this.unprocessedMessages.offer(msg);

    if (this.callback != null) {
      // There must always be one item here!
      MessageLite popped = this.unprocessedMessages.poll();
      popCallback().accept(popped, null);
    }
  }

  void read(Consumer<MessageLite> msgCallback, Consumer<Throwable> errorCallback) {
    if (this.callback != null) {
      throw new IllegalStateException("Two concurrent reads were requested.");
    }

    MessageLite popped = this.unprocessedMessages.poll();
    if (popped != null) {
      msgCallback.accept(popped);
    } else if (this.closed) {
      errorCallback.accept(SuspendedException.INSTANCE);
    } else {
      this.callback =
          (res, err) -> {
            if (res != null) {
              msgCallback.accept(res);
            } else {
              errorCallback.accept(err);
            }
          };
    }
  }

  void closeInput(Throwable e) {
    this.closed = true;
    if (this.callback != null) {
      popCallback().accept(null, e);
    }
  }

  private BiConsumer<MessageLite, Throwable> popCallback() {
    BiConsumer<MessageLite, Throwable> callback = this.callback;
    this.callback = null;
    return callback;
  }
}
