package dev.restate.sdk.testing;

import java.util.Queue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

class MockPublishSubscription<T> implements Flow.Subscription {
  private final Flow.Subscriber<? super T> subscriber;
  private final Queue<T> queue;
  private final AtomicBoolean cancelled;

  MockPublishSubscription(
      Flow.Subscriber<? super T> subscriber,
      Queue<T> queue,
      AtomicBoolean subscriptionCancelled) {
    this.subscriber = subscriber;
    this.queue = queue;
    this.cancelled = subscriptionCancelled;
  }

  @Override
  public void request(long l) {
    if (this.cancelled.get()) {
      return;
    }
    while (l != 0 && !this.queue.isEmpty()) {
      subscriber.onNext(queue.remove());
    }
  }

  @Override
  public void cancel() {
    this.cancelled.set(true);
  }
}
