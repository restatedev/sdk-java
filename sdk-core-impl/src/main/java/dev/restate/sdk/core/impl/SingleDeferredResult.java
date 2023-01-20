package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.syscalls.ReadyResult;
import java.util.Map;
import javax.annotation.Nullable;

class SingleDeferredResult<T> implements DeferredResultInternal<T> {

  private final int entryIndex;
  private @Nullable ReadyResultInternal<T> inner;

  SingleDeferredResult(int entryIndex) {
    this.entryIndex = entryIndex;
  }

  @Override
  public boolean isCompleted() {
    return inner != null;
  }

  @Override
  public int entryIndex() {
    return entryIndex;
  }

  @Override
  @Nullable
  public ReadyResult<T> toReadyResult() {
    return inner;
  }

  @SuppressWarnings("unchecked")
  public boolean tryResolve(Map<Integer, ReadyResultInternal<?>> resultMap) {
    ReadyResultInternal<?> resolved = resultMap.remove(entryIndex);
    if (resolved != null) {
      inner = (ReadyResultInternal<T>) resolved;
      return true;
    }
    return false;
  }
}
