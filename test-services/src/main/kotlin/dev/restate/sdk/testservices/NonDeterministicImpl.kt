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
import dev.restate.sdk.testservices.contracts.Counter
import dev.restate.sdk.testservices.contracts.NonDeterministic
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class NonDeterministicImpl : NonDeterministic {
  private val invocationCounts: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
  private val STATE_A: StateKey<String> = stateKey("a")
  private val STATE_B: StateKey<String> = stateKey("b")

  override suspend fun eitherSleepOrCall() {
    if (doLeftAction()) {
      sleep(100.milliseconds)
    } else {
      virtualObject<Counter>("abc").get()
    }
    // This is required to cause a suspension after the non-deterministic operation
    sleep(100.milliseconds)
    incrementCounter()
  }

  override suspend fun callDifferentMethod() {
    if (doLeftAction()) {
      virtualObject<Counter>("abc").get()
    } else {
      virtualObject<Counter>("abc").reset()
    }
    // This is required to cause a suspension after the non-deterministic operation
    sleep(100.milliseconds)
    incrementCounter()
  }

  override suspend fun backgroundInvokeWithDifferentTargets() {
    if (doLeftAction()) {
      toVirtualObject<Counter>("abc").request { it.get() }.send()
    } else {
      toVirtualObject<Counter>("abc").request { it.reset() }.send()
    }
    // This is required to cause a suspension after the non-deterministic operation
    sleep(100.milliseconds)
    incrementCounter()
  }

  override suspend fun setDifferentKey() {
    if (doLeftAction()) {
      state().set(STATE_A, "my-state")
    } else {
      state().set(STATE_B, "my-state")
    }
    // This is required to cause a suspension after the non-deterministic operation
    sleep(100.milliseconds)
    incrementCounter()
  }

  private suspend fun incrementCounter() {
    toVirtualObject<Counter>("abc").request { it.add(1) }.send()
  }

  private suspend fun doLeftAction(): Boolean {
    // Test runner sets an appropriate key here
    val countKey = key()
    return invocationCounts.merge(countKey, 1) { a: Int, b: Int -> a + b }!! % 2 == 1
  }
}
