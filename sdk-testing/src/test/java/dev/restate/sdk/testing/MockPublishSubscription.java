package dev.restate.sdk.testing;

import java.util.Queue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

class MockPublishSubscription<T> implements Flow.Subscription {

  private final TestRestateRuntime<T> testRestateRuntimeStateMachine;
  private final Flow.Subscriber<? super T> subscriber;
  private final Queue<T> queue;
  private final AtomicBoolean cancelled;

  MockPublishSubscription(
      Flow.Subscriber<? super T> subscriber,
      Queue<T> queue,
      AtomicBoolean subscriptionCancelled,
      TestRestateRuntime<T> testRestateRuntimeStateMachine) {
    this.subscriber = subscriber;
    this.queue = queue;
    this.cancelled = subscriptionCancelled;
    this.testRestateRuntimeStateMachine = testRestateRuntimeStateMachine;
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
