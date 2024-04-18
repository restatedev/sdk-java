// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.ProtoUtils.GREETER_SERVICE_TARGET
import dev.restate.sdk.core.SideEffectTestSuite
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.testDefinitionForService
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers

class SideEffectTest : SideEffectTestSuite() {

  override fun sideEffect(sideEffectOutput: String): TestInvocationBuilder =
      testDefinitionForService("SideEffect") { ctx, _: Unit ->
        val result = ctx.runBlock { sideEffectOutput }
        "Hello $result"
      }

  override fun namedSideEffect(name: String, sideEffectOutput: String): TestInvocationBuilder =
      testDefinitionForService("SideEffect") { ctx, _: Unit ->
        val result = ctx.runBlock(name) { sideEffectOutput }
        "Hello $result"
      }

  override fun consecutiveSideEffect(sideEffectOutput: String): TestInvocationBuilder =
      testDefinitionForService("ConsecutiveSideEffect") { ctx, _: Unit ->
        val firstResult = ctx.runBlock { sideEffectOutput }
        val secondResult = ctx.runBlock { firstResult.uppercase(Locale.getDefault()) }
        "Hello $secondResult"
      }

  override fun checkContextSwitching(): TestInvocationBuilder =
      TestDefinitions.testInvocation(
          Service.service(
              "CheckContextSwitching",
              Service.Options(
                  Dispatchers.Unconfined + CoroutineName("CheckContextSwitchingTestCoroutine"))) {
                handler("run") { ctx, _: Unit ->
                  val sideEffectCoroutine = ctx.runBlock { coroutineContext[CoroutineName]!!.name }
                  check(sideEffectCoroutine == "CheckContextSwitchingTestCoroutine") {
                    "Side effect thread is not running within the same coroutine context of the handler method: $sideEffectCoroutine"
                  }
                  "Hello"
                }
              },
          "run")

  override fun sideEffectGuard(): TestInvocationBuilder =
      testDefinitionForService<Unit, String>("SideEffectGuard") { ctx, _: Unit ->
        ctx.runBlock { ctx.send(GREETER_SERVICE_TARGET, KtSerdes.json(), "something") }
        throw IllegalStateException("This point should not be reached")
      }

  override fun failingSideEffect(name: String, reason: String): TestInvocationBuilder =
      testDefinitionForService<Unit, String>("FailingSideEffect") { ctx, _: Unit ->
        ctx.runBlock(name) { throw IllegalStateException(reason) }
      }
}
