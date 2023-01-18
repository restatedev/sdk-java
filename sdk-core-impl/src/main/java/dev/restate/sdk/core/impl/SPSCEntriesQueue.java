package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.syscalls.SyscallCallback;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class SPSCEntriesQueue {

  private final Queue<Map.Entry<Integer, MessageLite>> unprocessedMessages;

  private SyscallCallback<Map.Entry<Integer, MessageLite>> callback;
  private boolean closed;

  SPSCEntriesQueue() {
    this.unprocessedMessages = new ArrayDeque<>();

    this.closed = false;
  }

  void offer(int index, MessageLite msg) {
    assert Util.isEntry(msg);

    this.unprocessedMessages.offer(Map.entry(index, msg));

    if (this.callback != null) {
      // There must always be one item here!
      Map.Entry<Integer, MessageLite> popped = this.unprocessedMessages.poll();
      popCallback().onSuccess(popped);
    }
  }

  void read(BiConsumer<Integer, MessageLite> msgCallback, Consumer<Throwable> errorCallback) {
    if (this.callback != null) {
      throw new IllegalStateException("Two concurrent reads were requested.");
    }
    if (this.closed) {
      throw new IllegalStateException("Cannot read when closed");
    }

    Map.Entry<Integer, MessageLite> popped = this.unprocessedMessages.poll();
    if (popped != null) {
      msgCallback.accept(popped.getKey(), popped.getValue());
    } else if (this.closed) {
      errorCallback.accept(SuspendedException.INSTANCE);
    } else {
      this.callback =
          SyscallCallback.of(e -> msgCallback.accept(e.getKey(), e.getValue()), errorCallback);
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

  private SyscallCallback<Map.Entry<Integer, MessageLite>> popCallback() {
    SyscallCallback<Map.Entry<Integer, MessageLite>> callback = this.callback;
    this.callback = null;
    return callback;
  }
}
