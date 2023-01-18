package dev.restate.sdk.core.syscalls;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

public interface SyscallCallback<T> {

  void onSuccess(@Nullable T value);

  void onCancel(@Nullable Throwable t);

  static <T> SyscallCallback<T> of(Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
    return new SyscallCallback<>() {
      @Override
      public void onSuccess(@Nullable T value) {
        onSuccess.accept(value);
      }

      @Override
      public void onCancel(@Nullable Throwable t) {
        onFailure.accept(t);
      }
    };
  }

  static SyscallCallback<Void> ofVoid(Runnable onSuccess, Consumer<Throwable> onFailure) {
    return new SyscallCallback<>() {
      @Override
      public void onSuccess(@Nullable Void value) {
        onSuccess.run();
      }

      @Override
      public void onCancel(@Nullable Throwable t) {
        onFailure.accept(t);
      }
    };
  }

  static <T, R> SyscallCallback<T> completing(SyscallCallback<R> callback, Function<T, R> mapper) {
    return new SyscallCallback<>() {
      @Override
      public void onSuccess(@Nullable T value) {
        callback.onSuccess(mapper.apply(value));
      }

      @Override
      public void onCancel(@Nullable Throwable t) {
        callback.onCancel(t);
      }
    };
  }

  static <T> SyscallCallback<T> completingEmpty(SyscallCallback<Void> callback) {
    return new SyscallCallback<>() {
      @Override
      public void onSuccess(@Nullable T value) {
        callback.onSuccess(null);
      }

      @Override
      public void onCancel(@Nullable Throwable t) {
        callback.onCancel(t);
      }
    };
  }

  static <T> SyscallCallback<T> completingFuture(CompletableFuture<T> fut) {
    return of(fut::complete, t -> /* TODO log */ fut.cancel(true));
  }
}
