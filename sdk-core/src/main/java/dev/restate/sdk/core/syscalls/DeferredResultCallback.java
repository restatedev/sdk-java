package dev.restate.sdk.core.syscalls;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;

public interface DeferredResultCallback<T> extends NonEmptyDeferredResultCallback<T> {

  void onEmptyResult();

  static <T> DeferredResultCallback<T> of(
      Runnable emptyCallback,
      Consumer<T> resultCallback,
      Consumer<Throwable> failureCallback,
      Consumer<Throwable> suspensionCallback) {
    return new DeferredResultCallback<>() {
      @Override
      public void onEmptyResult() {
        emptyCallback.run();
      }

      @Override
      public void onResult(@Nullable T t) {
        resultCallback.accept(t);
      }

      @Override
      public void onFailure(Throwable t) {
        failureCallback.accept(t);
      }

      @Override
      public void onCancel(@Nullable Throwable t) {
        suspensionCallback.accept(t);
      }
    };
  }

  static <T> DeferredResultCallback<T> ofNonEmpty(
      Consumer<T> resultCallback,
      Consumer<Throwable> failureCallback,
      Consumer<Throwable> cancelCallback) {
    return new DeferredResultCallback<>() {
      @Override
      public void onEmptyResult() {
        failureCallback.accept(new IllegalStateException("Received unexpected empty result"));
      }

      @Override
      public void onResult(@Nullable T t) {
        resultCallback.accept(t);
      }

      @Override
      public void onFailure(Throwable t) {
        failureCallback.accept(t);
      }

      @Override
      public void onCancel(@Nullable Throwable t) {
        cancelCallback.accept(t);
      }
    };
  }

  static <T> DeferredResultCallback<T> completingFuture(CompletableFuture<T> fut) {
    return ofNonEmpty(
        fut::complete,
        fut::completeExceptionally,
        t ->
            // TODO log
            fut.cancel(true));
  }

  static <T> DeferredResultCallback<T> completingOptionalFuture(
      CompletableFuture<Optional<T>> fut) {
    return of(
        () -> fut.complete(Optional.empty()),
        t -> fut.complete(Optional.ofNullable(t)),
        fut::completeExceptionally,
        t ->
            // TODO log
            fut.cancel(true));
  }
}
