package dev.restate.sdk.testing;

import java.util.Queue;
import java.util.concurrent.Flow;

class PublishSubscription<MessageLite> implements Flow.Subscription {
  private final Flow.Subscriber<? super MessageLite> subscriber;
  private final Queue<MessageLite> queue;

  PublishSubscription(
      Flow.Subscriber<? super MessageLite> subscriber,
      Queue<MessageLite> queue) {
    this.subscriber = subscriber;
    this.queue = queue;
  }

  @Override
  public void request(long l) {
    while (l != 0 && !this.queue.isEmpty()) {
      subscriber.onNext(queue.remove());
    }
  }

  @Override
  public void cancel() {

  }
}
