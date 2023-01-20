package dev.restate.sdk.core.impl;

import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.SuspendedException;
import dev.restate.sdk.core.impl.ReadyResults.ReadyResultInternal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implements determinism of publishers */
class ReadyResultPublisher {

  private static final Logger LOG = LogManager.getLogger(ReadyResultPublisher.class);

  interface OnNewReadyResultCallback {

    boolean onNewReadyResult(Map<Integer, ReadyResultInternal<?>> resultMap);

    void onCancel(Throwable e);

    static OnNewReadyResultCallback of(
        Predicate<Map<Integer, ReadyResultInternal<?>>> onNewReadyResult,
        Consumer<Throwable> onCancel) {
      return new OnNewReadyResultCallback() {
        @Override
        public boolean onNewReadyResult(Map<Integer, ReadyResultInternal<?>> resultMap) {
          return onNewReadyResult.test(resultMap);
        }

        @Override
        public void onCancel(Throwable e) {
          onCancel.accept(e);
        }
      };
    }
  }

  private final Map<Integer, Protocol.CompletionMessage> completions;
  private final Map<Integer, Function<Protocol.CompletionMessage, ReadyResultInternal<?>>>
      completionParsers;
  private final Map<Integer, ReadyResultInternal<?>> results;

  private @Nullable OnNewReadyResultCallback onNewReadyResultCallback;

  private @Nullable Throwable inputChannelClosed;

  ReadyResultPublisher() {
    this.completions = new HashMap<>();
    this.completionParsers = new HashMap<>();
    this.results = new HashMap<>();
  }

  void offerCompletion(Protocol.CompletionMessage completionMessage) {
    if (this.inputChannelClosed != null) {
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
    // Guard against multiple requests of transitions to suspended
    if (this.inputChannelClosed != null) {
      return;
    }
    if (cause == SuspendedException.INSTANCE) {
      LOG.trace("Ready result publisher closed");
    } else {
      LOG.trace("Ready result publisher closed with failure", cause);
    }

    inputChannelClosed = cause;
    tryProgress();
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
        // Check if the input channel is closed
        if (this.inputChannelClosed != null) {
          cb.onCancel(this.inputChannelClosed);
        } else {
          // Register again the callback, for the next event
          this.onNewReadyResultCallback = cb;
        }
      }
    }
  }
}
