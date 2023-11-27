// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import java.util.function.Function;

/** This class is a helper to hold {@link ReadyResult}, and eventually map them. */
public class ReadyResultHolder<T> {

  private final DeferredResult<Object> deferredResult;
  private final Function<Object, T> resultMapper;

  private ReadyResult<Object> readyResult;

  @SuppressWarnings("unchecked")
  public ReadyResultHolder(DeferredResult<T> deferredResult) {
    this.deferredResult = (DeferredResult<Object>) deferredResult;
    this.resultMapper = null;
  }

  @SuppressWarnings("unchecked")
  public <U> ReadyResultHolder(DeferredResult<U> deferredResult, Function<U, T> resultMapper) {
    this.deferredResult = (DeferredResult<Object>) deferredResult;
    this.resultMapper = (Function<Object, T>) resultMapper;
  }

  public boolean isCompleted() {
    return deferredResult.isCompleted();
  }

  public DeferredResult<Object> getDeferredResult() {
    return deferredResult;
  }

  /** Invoke this after the resolution of deferred result */
  @SuppressWarnings("unchecked")
  private void postResolve() {
    assert deferredResult.isCompleted();

    if (this.resultMapper != null) {
      // This cast is safe b/c of the constructor
      this.readyResult =
          (ReadyResult<Object>) deferredResult.toReadyResult().map(this.resultMapper);
    } else {
      this.readyResult = deferredResult.toReadyResult();
    }
  }

  @SuppressWarnings("unchecked")
  public ReadyResult<T> getReadyResult() {
    if (readyResult == null) {
      this.postResolve();
    }

    // This cast is safe b/c of the constructor
    return (ReadyResult<T>) readyResult;
  }
}
