package dev.restate.sdk.testing;

import com.google.protobuf.MessageLite;
import java.util.Queue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

class PublishSubscription<MessageLite> implements Flow.Subscription {
  private final Flow.Subscriber<? super MessageLite> subscriber;
  private final Queue<MessageLite> queue;
  private final AtomicBoolean cancelled;

  PublishSubscription(
      Flow.Subscriber<? super MessageLite> subscriber,
      Queue<MessageLite> queue,
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
