// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint;

import java.util.concurrent.CompletableFuture;

/**
 * Interface to define interaction with deferred results.
 *
 * <p>Implementations of this class are provided by {@link HandlerContext} and should not be
 * overriden/wrapped.
 */
public interface AsyncResult<T> {

  CompletableFuture<T> poll();

}
