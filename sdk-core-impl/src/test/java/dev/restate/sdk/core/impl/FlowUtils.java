package dev.restate.sdk.core.impl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlowUtils {

  public static class CollectorSubscriber<T> implements Flow.Subscriber<T> {

    private final List<T> msgs = new ArrayList<>();
    private Throwable error = null;
    private boolean completed = false;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T t) {
      this.msgs.add(t);
    }

    @Override
    public void onError(Throwable throwable) {
      this.error = throwable;
    }

    @Override
    public void onComplete() {
      this.completed = true;
    }

    public List<T> getMessages() {
      return msgs;
    }

    public Throwable getError() {
      return error;
    }

    public boolean isCompleted() {
      return completed;
    }
  }

  public static class FutureSubscriber<T> implements Flow.Subscriber<T> {

    private final List<T> messages = new ArrayList<>();
    private final CompletableFuture<List<T>> future = new CompletableFuture<>();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T t) {
      synchronized (this.messages) {
        this.messages.add(t);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      this.future.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
      this.future.complete(getMessages());
    }

    public CompletableFuture<List<T>> getFuture() {
      return future;
    }

    public List<T> getMessages() {
      List<T> l;
      synchronized (this.messages) {
        l = new ArrayList<>(this.messages);
      }
      return l;
    }
  }

  public static class BufferedMockPublisher<T> implements Flow.Publisher<T> {

    private final Collection<T> elements;
    private final AtomicBoolean subscriptionCancelled;

    public BufferedMockPublisher(Collection<T> elements) {
      this.elements = elements;
      this.subscriptionCancelled = new AtomicBoolean(false);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
      subscriber.onSubscribe(
          new MockSubscription<>(subscriber, new ArrayDeque<>(elements), subscriptionCancelled));
    }

    public boolean isSubscriptionCancelled() {
      return subscriptionCancelled.get();
    }

    private static class MockSubscription<T> implements Flow.Subscription {

      private final Flow.Subscriber<? super T> subscriber;
      private final Queue<T> queue;
      private final AtomicBoolean cancelled;

      private MockSubscription(
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

        if (this.queue.isEmpty()) {
          subscriber.onComplete();
        }
      }

      @Override
      public void cancel() {
        this.cancelled.set(true);
      }
    }
  }

  public static class UnbufferedMockPublisher<T> implements Flow.Publisher<T> {

    private MockSubscription<T> subscription;

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
      this.subscription = new MockSubscription<>(subscriber);
      subscriber.onSubscribe(this.subscription);
    }

    public boolean isSubscriptionCancelled() {
      return Objects.requireNonNull(this.subscription).cancelled;
    }

    public void push(T element) {
      Objects.requireNonNull(this.subscription).onPush(element);
    }

    public void close() {
      Objects.requireNonNull(this.subscription).onClose();
    }

    private static class MockSubscription<T> implements Flow.Subscription {

      private final Flow.Subscriber<? super T> subscriber;
      private final Queue<T> queue;
      private boolean publisherClosed = false;
      private long request = 0;
      private boolean cancelled = false;

      private MockSubscription(Flow.Subscriber<? super T> subscriber) {
        this.subscriber = subscriber;
        this.queue = new ArrayDeque<>();
      }

      @Override
      public void request(long l) {
        if (l == Long.MAX_VALUE) {
          this.request = l;
        } else {
          this.request += l;
          // Overflow check
          if (this.request < 0) {
            this.request = Long.MAX_VALUE;
          }
        }
        this.doProgress();
      }

      @Override
      public void cancel() {
        this.cancelled = true;
      }

      private void onPush(T element) {
        this.queue.offer(element);
        this.doProgress();
      }

      private void onClose() {
        this.publisherClosed = true;
        this.doProgress();
      }

      private void doProgress() {
        if (this.cancelled) {
          return;
        }
        while (this.request != 0 && !this.queue.isEmpty()) {
          this.request--;
          subscriber.onNext(queue.remove());
        }
        if (this.publisherClosed) {
          subscriber.onComplete();
        }
      }
    }
  }
}
