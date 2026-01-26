// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.sdk.common.RetryPolicy
import dev.restate.sdk.core.SideEffectTestSuite
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForService
import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.endpoint.definition.HandlerType
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.endpoint.definition.ServiceType
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.kotlin.awaitAll
import dev.restate.sdk.kotlin.runAsync
import dev.restate.sdk.kotlin.runBlock
import dev.restate.serde.kotlinx.*
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
                      "run",
                      HandlerType.SHARED,
                      jsonSerde<Unit>(),
                      jsonSerde<String>(),
                      HandlerRunner.of(
                          KotlinSerializationSerdeFactory(),
                          HandlerRunner.Options(
                              Dispatchers.Unconfined +
                                  CoroutineName("CheckContextSwitchingTestCoroutine")
                          ),
                      ) { ctx: Context, _: Unit ->
                        val sideEffectCoroutine =
                            ctx.runBlock { coroutineContext[CoroutineName]!!.name }
                        check(sideEffectCoroutine == "CheckContextSwitchingTestCoroutine") {
                          "Side effect thread is not running within the same coroutine context of the handler method: $sideEffectCoroutine"
                        }
                        "Hello"
                      },
                  )
              ),
          ),
          "run",
      )

  override fun failingSideEffect(name: String, reason: String) =
      testDefinitionForService<Unit, String>("FailingSideEffect") { ctx, _: Unit ->
        ctx.runBlock(name) { throw IllegalStateException(reason) }
      }

  override fun awaitAllSideEffectWithFirstFailing(
      firstSideEffect: String,
      secondSideEffect: String,
      successValue: String,
      failureReason: String,
  ) =
      testDefinitionForService<Unit, Unit>("AwaitAllSideEffectWithFirstFailing") { ctx, _: Unit ->
        val fut1 =
            ctx.runAsync<String>(firstSideEffect) { throw IllegalStateException(failureReason) }
        val fut2 = ctx.runAsync(secondSideEffect) { successValue }
        listOf(fut1, fut2).awaitAll()
      }

  override fun awaitAllSideEffectWithSecondFailing(
      firstSideEffect: String,
      secondSideEffect: String,
      successValue: String,
      failureReason: String,
  ) =
      testDefinitionForService<Unit, Unit>("AwaitAllSideEffectWithSecondFailing") { ctx, _: Unit ->
        val fut1 = ctx.runAsync(firstSideEffect) { successValue }
        val fut2 =
            ctx.runAsync<String>(secondSideEffect) { throw IllegalStateException(failureReason) }
        listOf(fut1, fut2).awaitAll()
      }

  override fun failingSideEffectWithRetryPolicy(reason: String, retryPolicy: RetryPolicy?) =
      testDefinitionForService<Unit, String>("FailingSideEffectWithRetryPolicy") { ctx, _: Unit ->
        ctx.runBlock(
            retryPolicy =
                retryPolicy?.let {
                  RetryPolicy(
                      initialDelay = it.initialDelay.toKotlinDuration(),
                      exponentiationFactor = it.exponentiationFactor,
                      maxDelay = it.maxDelay?.toKotlinDuration(),
                      maxDuration = it.maxDuration?.toKotlinDuration(),
                      maxAttempts = it.maxAttempts,
                  )
                }
        ) {
          throw IllegalStateException(reason)
        }
      }
}
