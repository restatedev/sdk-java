package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.syscalls.DeferredResultCallback;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implements determinism of publishers */
class ReadyResultPublisher {

  private static final Logger LOG = LogManager.getLogger(ReadyResultPublisher.class);

  private enum State {
    REPLAYING,
    PROCESSING
  }

  private final Consumer<Integer> orderHandler;

  private final HashMap<Integer, ReadyResult<?>> results;
  private final Queue<Integer> publishingOrder;
  private final HashMap<Integer, DeferredResultCallback<?>> subscriptions;

  private State state;
  private boolean inputClosed;
  private Throwable suspensionCause;

  ReadyResultPublisher(Consumer<Integer> orderHandler) {
    this.orderHandler = orderHandler;

    this.results = new HashMap<>();
    this.publishingOrder = new ArrayDeque<>();
    this.subscriptions = new HashMap<>();

    this.state = State.REPLAYING;
    this.inputClosed = false;
  }

  void offerResult(int index, ReadyResult<?> result) {
    LOG.trace("Offered result for index {}", index);
    this.results.put(index, result);
    tryProgress();
  }

  void offerOrder(int index) {
    assert this.state == State.REPLAYING;

    LOG.trace("Offered order for index {}", index);
    this.publishingOrder.offer(index);
    tryProgress();
  }

  void subscribe(int index, DeferredResultCallback<?> subscription) {
    LOG.trace("Subscribed to index {}", index);
    this.subscriptions.put(index, subscription);
    tryProgress();
  }

  void onEndReplay() {
    if (this.state == State.PROCESSING) {
      return;
    }
    this.state = State.PROCESSING;
    LOG.trace("Transitioned to PROCESSING");

    tryProgress();
  }

  void onInputChannelClosed(@Nullable Throwable cause) {
    // Guard against multiple requests of transitions to suspended
    if (this.inputClosed) {
      return;
    }
    this.inputClosed = true;
    this.suspensionCause = cause;
    LOG.trace("Input closed");

    tryProgress();
  }

  boolean isOrderQueueEmpty() {
    return this.publishingOrder.isEmpty();
  }

  private void tryProgress() {
    if (this.state == State.REPLAYING) {
      tryProgressWhenReplaying();
    } else if (this.state == State.PROCESSING) {
      tryProgressWhenProcessing();
      if (this.inputClosed) {
        failPendingSubscriptions();
      }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void tryProgressWhenReplaying() {
    // In replay mode we can emit ready results only in respect with what we have in the
    // publishingOrder queue
    for (Integer nextEntry = publishingOrder.peek();
        nextEntry != null && subscriptions.containsKey(nextEntry) && results.containsKey(nextEntry);
        nextEntry = publishingOrder.peek()) {
      publishingOrder.poll();
      LOG.trace("Publishing in replay mode index {}", nextEntry);
      results.remove(nextEntry).publish((DeferredResultCallback) subscriptions.remove(nextEntry));
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void tryProgressWhenProcessing() {
    // Let's make sure we run the registered handlers for values for which we have a result
    List<Map.Entry<Integer, Map.Entry<ReadyResult, DeferredResultCallback>>> toRun =
        new ArrayList<>();
    subscriptions
        .entrySet()
        .removeIf(
            entry -> {
              ReadyResult<?> result = results.remove(entry.getKey());
              if (result != null) {
                toRun.add(
                    new SimpleImmutableEntry<>(
                        entry.getKey(), new SimpleImmutableEntry(result, entry.getValue())));
                return true;
              }
              return false;
            });
    for (Map.Entry<Integer, Map.Entry<ReadyResult, DeferredResultCallback>>
        readyResultConsumerEntry : toRun) {
      LOG.trace("Publishing in processing mode index {}", readyResultConsumerEntry.getKey());
      orderHandler.accept(readyResultConsumerEntry.getKey());
      readyResultConsumerEntry
          .getValue()
          .getKey()
          .publish(readyResultConsumerEntry.getValue().getValue());
    }
  }

  @SuppressWarnings({"rawtypes"})
  private void failPendingSubscriptions() {
    List<DeferredResultCallback> toRun = new ArrayList<>(subscriptions.values());
    LOG.trace("Failing pending subscriptions {}", subscriptions.keySet());
    subscriptions.clear();

    toRun.forEach(sub -> sub.onCancel(this.suspensionCause));
  }
}
