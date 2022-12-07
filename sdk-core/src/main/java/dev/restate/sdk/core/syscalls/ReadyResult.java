package dev.restate.sdk.core.syscalls;

import javax.annotation.Nullable;

public interface ReadyResult<T> extends DeferredResult<T> {

  boolean isOk();

  @Nullable
  T getResult();

  @Nullable
  Throwable getFailure();
}
