// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.sdk.core.SleepTestSuite
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForService
import dev.restate.sdk.kotlin.*
import kotlin.time.Duration.Companion.seconds

class SleepTest : SleepTestSuite() {

  override fun sleepGreeter(): TestDefinitions.TestInvocationBuilder =
      testDefinitionForService("SleepGreeter") { ctx, _: Unit ->
        ctx.sleep(1.seconds)
        "Hello"
      }

  override fun manySleeps(): TestDefinitions.TestInvocationBuilder =
      testDefinitionForService<Unit, Unit>("ManySleeps") { ctx, _: Unit ->
        val durableFutures = mutableListOf<DurableFuture<Unit>>()
        for (i in 0..9) {
          durableFutures.add(ctx.timer(1.seconds))
        }
        durableFutures.awaitAll()
      }
}
