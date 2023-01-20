package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.SuspendedException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class HandshakeQueue {

  private final Queue<MessageLite> unprocessedMessages;

  private BiConsumer<MessageLite, Throwable> callback;
  private boolean closed;

  HandshakeQueue() {
    this.unprocessedMessages = new ArrayDeque<>();

    this.closed = false;
  }

  void offer(MessageLite msg) {
    checkClosed();

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
    checkClosed();

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

  List<MessageLite> drainAndClose() {
    if (this.callback != null) {
      throw new IllegalStateException("Cannot drain when there is a registered callback.");
    }
    checkClosed();
    this.closed = true;

    List<MessageLite> out = new ArrayList<>(this.unprocessedMessages);
    this.unprocessedMessages.clear();
    return out;
  }

  void abort(Throwable e) {
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

  private void checkClosed() {
    if (this.closed) {
      throw new IllegalStateException("Cannot read when closed");
    }
  }
}
