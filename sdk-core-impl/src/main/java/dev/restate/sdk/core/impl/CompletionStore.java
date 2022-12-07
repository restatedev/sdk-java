package dev.restate.sdk.core.impl;

import dev.restate.generated.service.protocol.Protocol;
import java.util.HashMap;
import javax.annotation.Nullable;

class CompletionStore {

  private final HashMap<Integer, CompletionHolder> unprocessedCompletions;

  public CompletionStore() {
    this.unprocessedCompletions = new HashMap<>();
  }

  void wantCompletion(int id) {
    this.unprocessedCompletions.put(id, CompletionHolder.EMPTY);
  }

  void handlePastCompletion(Protocol.CompletionMessage completionMessage) {
    this.unprocessedCompletions.computeIfPresent(
        completionMessage.getEntryIndex(), (k, v) -> v.hold(completionMessage));
  }

  void handleFutureCompletion(Protocol.CompletionMessage completionMessage) {
    this.unprocessedCompletions.put(
        completionMessage.getEntryIndex(), new CompletionHolder(completionMessage));
  }

  @Nullable
  Protocol.CompletionMessage getCompletion(int index) {
    if (this.unprocessedCompletions.containsKey(index)) {
      CompletionHolder holder = this.unprocessedCompletions.get(index);
      if (holder.isHolding()) {
        this.unprocessedCompletions.remove(index);
        return holder.completionMessage;
      }
    }
    return null;
  }

  private static final class CompletionHolder {

    private static final CompletionHolder EMPTY = new CompletionHolder(null);

    private final Protocol.CompletionMessage completionMessage;

    private CompletionHolder(Protocol.CompletionMessage completionMessage) {
      this.completionMessage = completionMessage;
    }

    private boolean isHolding() {
      return completionMessage != null;
    }

    private CompletionHolder hold(Protocol.CompletionMessage completionMessage) {
      return new CompletionHolder(completionMessage);
    }
  }
}
