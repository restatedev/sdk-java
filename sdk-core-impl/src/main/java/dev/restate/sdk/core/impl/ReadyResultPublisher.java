package dev.restate.sdk.core.impl;

import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.ReadyResults.ReadyResultInternal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implements determinism of publishers */
class ReadyResultPublisher {

  private static final Logger LOG = LogManager.getLogger(ReadyResultPublisher.class);

  interface OnNewReadyResultCallback extends InputChannelState.SuspendableCallback {
    boolean onNewReadyResult(Map<Integer, ReadyResultInternal<?>> resultMap);
  }

  private final Map<Integer, Protocol.CompletionMessage> completions;
  private final Map<Integer, Function<Protocol.CompletionMessage, ReadyResultInternal<?>>>
      completionParsers;
  private final Map<Integer, ReadyResultInternal<?>> results;

  private @Nullable OnNewReadyResultCallback onNewReadyResultCallback;

  private final InputChannelState state;

  ReadyResultPublisher() {
    this.completions = new HashMap<>();
    this.completionParsers = new HashMap<>();
    this.results = new HashMap<>();
    this.state = new InputChannelState();
  }

  void offerCompletion(Protocol.CompletionMessage completionMessage) {
    if (this.state.isClosed()) {
      LOG.warn("Offering a completion when the publisher is closed");
      return;
    }
    LOG.trace("Offered new completion {}", completionMessage);

    this.completions.put(completionMessage.getEntryIndex(), completionMessage);
    this.tryParse(completionMessage.getEntryIndex());
  }

  void offerCompletionParser(
      int entryIndex, Function<Protocol.CompletionMessage, ReadyResultInternal<?>> parser) {
    LOG.trace("Offered new completion parser for index {}", entryIndex);

    this.completionParsers.put(entryIndex, parser);
    this.tryParse(entryIndex);
  }

  void onNewReadyResult(OnNewReadyResultCallback callback) {
    if (this.onNewReadyResultCallback != null) {
      throw new IllegalStateException("Two concurrent reads were requested.");
    }
    this.onNewReadyResultCallback = callback;

    this.tryProgress();
  }

  void abort(Throwable cause) {
    if (this.state.close(cause)) {
      tryProgress();
    }
  }

  private void tryParse(int entryIndex) {
    Protocol.CompletionMessage completionMessage = this.completions.get(entryIndex);
    if (completionMessage == null) {
      return;
    }

    Function<Protocol.CompletionMessage, ReadyResultInternal<?>> parser =
        this.completionParsers.remove(entryIndex);
    if (parser == null) {
      return;
    }

    this.completions.remove(entryIndex, completionMessage);

    // Parse to ready result
    ReadyResultInternal<?> readyResult = parser.apply(completionMessage);

    // Push to the ready result queue
    this.results.put(completionMessage.getEntryIndex(), readyResult);

    // We have a new result, let's try to progress
    this.tryProgress();
  }

  private void tryProgress() {
    if (this.onNewReadyResultCallback != null) {
      // Pop callback
      OnNewReadyResultCallback cb = this.onNewReadyResultCallback;
      this.onNewReadyResultCallback = null;

      // Try to consume results
      boolean resolved = cb.onNewReadyResult(this.results);
      if (!resolved) {
        this.onNewReadyResultCallback = this.state.handleOrReturn(cb);
      }
    }
  }
}
