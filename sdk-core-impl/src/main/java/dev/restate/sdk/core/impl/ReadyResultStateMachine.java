package dev.restate.sdk.core.impl;

import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.ReadyResults.ReadyResultInternal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** State machine tracking ready results */
class ReadyResultStateMachine
    extends BaseSuspendableCallbackStateMachine<ReadyResultStateMachine.OnNewReadyResultCallback> {

  private static final Logger LOG = LogManager.getLogger(ReadyResultStateMachine.class);

  interface OnNewReadyResultCallback extends SuspendableCallback {
    boolean onNewReadyResult(Map<Integer, ReadyResultInternal<?>> resultMap);
  }

  private final Map<Integer, Protocol.CompletionMessage> completions;
  private final Map<Integer, Function<Protocol.CompletionMessage, ReadyResultInternal<?>>>
      completionParsers;
  private final Map<Integer, ReadyResultInternal<?>> results;

  ReadyResultStateMachine() {
    this.completions = new HashMap<>();
    this.completionParsers = new HashMap<>();
    this.results = new HashMap<>();
  }

  void offerCompletion(Protocol.CompletionMessage completionMessage) {
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
    this.assertCallbackNotSet("Two concurrent reads were requested.");

    this.tryProgress(callback);
  }

  void abort(Throwable cause) {
    super.abort(cause);
    this.consumeCallback(this::tryProgress);
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
    this.consumeCallback(this::tryProgress);
  }

  private void tryProgress(OnNewReadyResultCallback cb) {
    boolean resolved = cb.onNewReadyResult(this.results);
    if (!resolved) {
      this.setCallback(cb);
    }
  }
}
