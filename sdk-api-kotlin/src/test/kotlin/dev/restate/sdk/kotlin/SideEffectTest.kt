// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.definition.HandlerType
import dev.restate.sdk.definition.ServiceType
import dev.restate.sdk.definition.HandlerDefinition
import dev.restate.sdk.definition.HandlerSpecification
import dev.restate.sdk.definition.ServiceDefinition
import dev.restate.sdk.core.ProtoUtils.GREETER_SERVICE_TARGET
import dev.restate.sdk.core.SideEffectTestSuite
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.testDefinitionForService
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.time.toKotlinDuration
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
          ServiceDefinition.of(
              "CheckContextSwitching",
              ServiceType.SERVICE,
              listOf(
                  HandlerDefinition.of(
                      HandlerSpecification.of(
                          "run", HandlerType.SHARED, KtSerdes.UNIT, KtSerdes.json()),
                      HandlerRunner.of { ctx: Context, _: Unit ->
                        val sideEffectCoroutine =
                            ctx.runBlock { coroutineContext[CoroutineName]!!.name }
                        check(sideEffectCoroutine == "CheckContextSwitchingTestCoroutine") {
                          "Side effect thread is not running within the same coroutine context of the handler method: $sideEffectCoroutine"
                        }
                        "Hello"
                      }))),
          HandlerRunner.Options(
              Dispatchers.Unconfined + CoroutineName("CheckContextSwitchingTestCoroutine")),
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

  override fun failingSideEffectWithRetryPolicy(
      reason: String,
      retryPolicy: dev.restate.sdk.types.RetryPolicy?
  ) =
      testDefinitionForService<Unit, String>("FailingSideEffectWithRetryPolicy") { ctx, _: Unit ->
        ctx.runBlock(
            retryPolicy =
                retryPolicy?.let {
                  RetryPolicy(
                      initialDelay = it.initialDelay.toKotlinDuration(),
                      exponentiationFactor = it.exponentiationFactor,
                      maxDelay = it.maxDelay?.toKotlinDuration(),
                      maxDuration = it.maxDuration?.toKotlinDuration(),
                      maxAttempts = it.maxAttempts)
                }) {
              throw IllegalStateException(reason)
            }
      }
}
