package dev.restate.sdk.core.syscalls;

import javax.annotation.Nullable;

public interface NonEmptyDeferredResultCallback<T> {

  void onResult(@Nullable T t);

  // This is user failure
  void onFailure(Throwable t);

  void onCancel(@Nullable Throwable t);
}
