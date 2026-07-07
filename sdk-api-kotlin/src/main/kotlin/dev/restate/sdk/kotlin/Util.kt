// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.common.Slice
import dev.restate.sdk.common.AbortedExecutionException
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.serde.Serde
import java.util.concurrent.CompletionStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await as kotlinxAwait

/**
 * Awaits [this], translating the SDK-internal [AbortedExecutionException] control-flow signal into
 * a coroutine [CancellationException] (coroutine idiomatic way of initiating an abort).
 */
internal suspend fun <T> CompletionStage<T>.await(): T =
    try {
      this.kotlinxAwait()
    } catch (e: AbortedExecutionException) {
      throw CancellationException("Restate invocation suspended or closed").apply { initCause(e) }
    }

internal fun <T : Any?> Serde<T>.serializeWrappingException(
    handlerContext: HandlerContext,
    value: T?,
): Slice {
  return try {
    this.serialize(value)
  } catch (e: Exception) {
    handlerContext.fail(e)
    throw CancellationException("Failed serialization", e)
  }
}
