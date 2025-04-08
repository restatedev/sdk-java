// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.common.StateKey
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.CounterClient
import dev.restate.sdk.testservices.contracts.NonDeterministic
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
      CounterClient.fromContext(context, "abc").get().await()
    }
    // This is required to cause a suspension after the non-deterministic operation
    context.sleep(100.milliseconds)
    incrementCounter(context)
  }

  override suspend fun callDifferentMethod(context: ObjectContext) {
    if (doLeftAction(context)) {
      CounterClient.fromContext(context, "abc").get().await()
    } else {
      CounterClient.fromContext(context, "abc").reset().await()
    }
    // This is required to cause a suspension after the non-deterministic operation
    context.sleep(100.milliseconds)
    incrementCounter(context)
  }

  override suspend fun backgroundInvokeWithDifferentTargets(context: ObjectContext) {
    if (doLeftAction(context)) {
      CounterClient.fromContext(context, "abc").send().get()
    } else {
      CounterClient.fromContext(context, "abc").send().reset()
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
    CounterClient.fromContext(context, context.key()).send().add(1)
  }

  private fun doLeftAction(context: ObjectContext): Boolean {
    // Test runner sets an appropriate key here
    val countKey = context.key()
    return invocationCounts.merge(countKey, 1) { a: Int, b: Int -> a + b }!! % 2 == 1
  }
}
