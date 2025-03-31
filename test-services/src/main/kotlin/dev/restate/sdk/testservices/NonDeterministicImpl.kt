// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.CounterHandlers
import dev.restate.sdk.testservices.contracts.NonDeterministic
import dev.restate.sdk.types.StateKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class NonDeterministicImpl : NonDeterministic {
  private val invocationCounts: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
  private val STATE_A: StateKey<String> = stateKey("a")
  private val STATE_B: StateKey<String> = stateKey("b")

  override suspend fun eitherSleepOrCall(context: ObjectContext) {
    if (doLeftAction(context)) {
      context.sleep(100.milliseconds)
    } else {
      CounterHandlers.get("abc").call(context).await()
    }
    // This is required to cause a suspension after the non-deterministic operation
    context.sleep(100.milliseconds)
    incrementCounter(context)
  }

  override suspend fun callDifferentMethod(context: ObjectContext) {
    if (doLeftAction(context)) {
      CounterHandlers.get("abc").call(context).await()
    } else {
      CounterHandlers.reset("abc").call(context).await()
    }
    // This is required to cause a suspension after the non-deterministic operation
    context.sleep(100.milliseconds)
    incrementCounter(context)
  }

  override suspend fun backgroundInvokeWithDifferentTargets(context: ObjectContext) {
    if (doLeftAction(context)) {
      CounterHandlers.get("abc").call(context).await()
    } else {
      CounterHandlers.reset("abc").call(context).await()
    }
    // This is required to cause a suspension after the non-deterministic operation
    context.sleep(100.milliseconds)
    incrementCounter(context)
  }

  override suspend fun setDifferentKey(context: ObjectContext) {
    if (doLeftAction(context)) {
      context.set(STATE_A, "my-state")
    } else {
      context.set(STATE_B, "my-state")
    }
    // This is required to cause a suspension after the non-deterministic operation
    context.sleep(100.milliseconds)
    incrementCounter(context)
  }

  private suspend fun incrementCounter(context: ObjectContext) {
    CounterHandlers.add(context.key(), 1).call(context).await()
  }

  private fun doLeftAction(context: ObjectContext): Boolean {
    // Test runner sets an appropriate key here
    val countKey = context.key()
    return invocationCounts.merge(countKey, 1) { a: Int, b: Int -> a + b }!! % 2 == 1
  }
}
