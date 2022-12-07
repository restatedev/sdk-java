package dev.restate.sdk.core.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

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
      List<T> l;
      synchronized (this.messages) {
        l = new ArrayList<>(this.messages);
      }
      this.future.complete(l);
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

  public static class MockSubscription implements Flow.Subscription {

    private boolean cancelled = false;

    @Override
    public void request(long l) {}

    @Override
    public void cancel() {
      this.cancelled = true;
    }

    public boolean isCancelled() {
      return cancelled;
    }
  }

  @SafeVarargs
  public static <T> void pipe(Flow.Subscriber<T> subscriber, T... values) {
    for (T val : values) {
      subscriber.onNext(val);
    }
  }

  @SafeVarargs
  public static <T> void pipeAndComplete(Flow.Subscriber<T> subscriber, T... values) {
    pipe(subscriber, values);
    subscriber.onComplete();
  }
}
