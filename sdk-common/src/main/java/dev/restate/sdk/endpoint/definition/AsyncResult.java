// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import dev.restate.common.function.ThrowingFunction;
import dev.restate.sdk.types.TerminalException;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Interface to define interaction with deferred results.
 *
 * <p>Implementations of this class are provided by {@link HandlerContext} and should not be
 * overriden/wrapped.
 */
public interface AsyncResult<T> {

  CompletableFuture<T> poll();

  HandlerContext ctx();

  <U> AsyncResult<U> map(ThrowingFunction<T, CompletableFuture<U>> successMapper, ThrowingFunction<TerminalException, CompletableFuture<U>> failureMapper);

  default <U> AsyncResult<U> map(ThrowingFunction<T, CompletableFuture<U>> successMapper) {
    return map(successMapper, null);
  }

  default    AsyncResult<T> mapFailure(ThrowingFunction<TerminalException, CompletableFuture<T>> failureMapper) {
     return map(null, failureMapper);
   }
}
