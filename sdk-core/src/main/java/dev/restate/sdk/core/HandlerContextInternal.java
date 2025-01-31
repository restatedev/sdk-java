// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.core.AsyncResults.AsyncResultInternal;
import dev.restate.sdk.core.statemachine.InvocationState;
import dev.restate.sdk.definition.AsyncResult;
import dev.restate.sdk.definition.HandlerContext;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.Slice;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

interface HandlerContextInternal extends HandlerContext {

  @Override
  default AsyncResult<Integer> createAnyDeferred(List<AsyncResult<?>> children) {
    return AsyncResults.any(
        children.stream().map(dr -> (AsyncResultInternal<?>) dr).collect(Collectors.toList()));
  }

  @Override
  default AsyncResult<Void> createAllDeferred(List<AsyncResult<?>> children) {
    return AsyncResults.all(
        children.stream().map(dr -> (AsyncResultInternal<?>) dr).collect(Collectors.toList()));
  }

  void proposeRunSuccess(int runHandle, Slice toWrite);

  void proposeRunFailure(int runHandle, Throwable toWrite, Duration attemptDuration, @Nullable RetryPolicy retryPolicy);

  void pollAsyncResult(AsyncResultInternal<?> asyncResult);

  // -- Lifecycle methods

  void close();

  // -- State machine introspection (used by logging propagator)

  /**
   * @return fully qualified method name in the form {fullyQualifiedServiceName}/{methodName}
   */
  String getFullyQualifiedMethodName();

  InvocationState getInvocationState();
}
