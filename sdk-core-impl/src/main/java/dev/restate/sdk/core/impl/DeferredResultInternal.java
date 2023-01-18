package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.syscalls.DeferredResult;
import java.util.Map;

interface DeferredResultInternal<T> extends DeferredResult<T> {

  int entryIndex();

  boolean tryResolve(Map<Integer, ReadyResultInternal<?>> resultMap);
}
