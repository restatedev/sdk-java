package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.syscalls.SyscallCallback;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;
import javax.annotation.Nullable;

class EntriesQueue {

  private final Queue<MessageLite> unprocessedMessages;

  @Nullable private SyscallCallback<MessageLite> callback;
  private boolean closed;

  EntriesQueue() {
    this.unprocessedMessages = new ArrayDeque<>();

    this.closed = false;
  }

  void offer(MessageLite msg) {
    Util.assertIsEntry(msg);

    if (this.callback != null) {
      popCallback().onSuccess(msg);
    } else {
      this.unprocessedMessages.offer(msg);
    }
  }

  void read(Consumer<MessageLite> msgCallback, Consumer<Throwable> errorCallback) {
    if (this.callback != null) {
      throw new IllegalStateException("Two concurrent reads were requested.");
    }
    if (this.closed) {
      throw new IllegalStateException("Cannot read when closed");
    }

    MessageLite popped = this.unprocessedMessages.poll();
    if (popped != null) {
      try {
        msgCallback.accept(popped);
        // TODO this is probably wrong :(
      } catch (Exception e) {
        errorCallback.accept(e);
      }
    } else {
      this.callback = SyscallCallback.of(msgCallback, errorCallback);
    }
  }

  void abort(Throwable e) {
    this.closed = true;
    if (this.callback != null) {
      popCallback().onCancel(e);
    }
  }

  boolean isEmpty() {
    return this.unprocessedMessages.isEmpty();
  }

  @Nullable
  private SyscallCallback<MessageLite> popCallback() {
    SyscallCallback<MessageLite> callback = this.callback;
    this.callback = null;
    return callback;
  }
}
