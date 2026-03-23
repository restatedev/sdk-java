// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.internal

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/**
 * Coroutine context element that marks the current coroutine as executing inside a `ctx.run()`
 * block. Context methods check for this element and throw [IllegalStateException] if present.
 */
internal class InsideRunElement private constructor() : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<InsideRunElement> {
    val INSTANCE = InsideRunElement()

    suspend fun checkNotInsideRun() {
      if (currentCoroutineContext()[Key] != null) {
        throw IllegalStateException(
            "Cannot invoke context method inside ctx.run(). " +
                "The run closure is meant for non-deterministic operations (e.g., HTTP calls, database reads). " +
                "You MUST use context methods outside of ctx.run(), check the documentation: https://docs.restate.dev/develop/java/durable-steps#run"
        )
      }
    }
  }
}
