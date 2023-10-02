package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import java.util.ArrayDeque;
import java.util.Queue;

class IncomingEntriesStateMachine
    extends BaseSuspendableCallbackStateMachine<IncomingEntriesStateMachine.OnEntryCallback> {

  interface OnEntryCallback extends SuspendableCallback {
    void onEntry(MessageLite msg);
  }

  private final Queue<MessageLite> unprocessedMessages;

  IncomingEntriesStateMachine() {
    this.unprocessedMessages = new ArrayDeque<>();
  }

  void offer(MessageLite msg) {
    Util.assertIsEntry(msg);
    this.consumeCallbackOrElse(cb -> cb.onEntry(msg), () -> this.unprocessedMessages.offer(msg));
  }

  void read(OnEntryCallback msgCallback) {
    this.assertCallbackNotSet("Two concurrent reads were requested.");

    MessageLite popped = this.unprocessedMessages.poll();
    if (popped != null) {
      msgCallback.onEntry(popped);
    } else {
      this.setCallback(msgCallback);
    }
  }

  boolean isEmpty() {
    return this.unprocessedMessages.isEmpty();
  }

  @Override
  void abort(Throwable cause) {
    super.abort(cause);
    // We can't do anything else if the input stream is closed, so we just fail the callback, if any
    this.tryFailCallback();
  }
}
