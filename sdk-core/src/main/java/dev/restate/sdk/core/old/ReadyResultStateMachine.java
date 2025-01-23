// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.old;

import dev.restate.generated.service.protocol.Protocol;
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
    boolean onNewResult(Map<Integer, Result<?>> resultMap);
  }

  private final Map<Integer, Protocol.CompletionMessage> completions;
  private final Map<Integer, Function<Protocol.CompletionMessage, Result<?>>> completionParsers;
  private final Map<Integer, Result<?>> results;

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
      int entryIndex, Function<Protocol.CompletionMessage, Result<?>> parser) {
    LOG.trace("Offered new completion parser for index {}", entryIndex);

    this.completionParsers.put(entryIndex, parser);
    this.tryParse(entryIndex);
  }

  void onNewReadyResult(OnNewReadyResultCallback callback) {
    this.assertCallbackNotSet("Two concurrent reads were requested.");

    this.tryProgress(callback);
  }

  @Override
  void abort(Throwable cause) {
    super.abort(cause);
    this.consumeCallback(this::tryProgress);
  }

  private void tryParse(int entryIndex) {
    Protocol.CompletionMessage completionMessage = this.completions.get(entryIndex);
    if (completionMessage == null) {
      return;
    }

    Function<Protocol.CompletionMessage, Result<?>> parser =
        this.completionParsers.remove(entryIndex);
    if (parser == null) {
      return;
    }

    this.completions.remove(entryIndex, completionMessage);

    // Parse to ready result
    Result<?> readyResult = parser.apply(completionMessage);

    // Push to the ready result queue
    this.results.put(completionMessage.getEntryIndex(), readyResult);

    // We have a new result, let's try to progress
    this.consumeCallback(this::tryProgress);
  }

  private void tryProgress(OnNewReadyResultCallback cb) {
    boolean resolved = cb.onNewResult(this.results);
    if (!resolved) {
      this.setCallback(cb);
    }
  }
}
