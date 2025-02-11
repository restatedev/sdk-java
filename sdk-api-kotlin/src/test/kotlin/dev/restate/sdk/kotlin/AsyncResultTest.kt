// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.AsyncResultTestSuite
import dev.restate.sdk.core.TestDefinitions.*
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.callGreeterGreetService
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.testDefinitionForVirtualObject
import dev.restate.sdk.types.StateKey
import dev.restate.sdk.types.TimeoutException
import java.util.stream.Stream
import kotlin.time.Duration.Companion.days

class AsyncResultTest : AsyncResultTestSuite() {
  override fun reverseAwaitOrder(): TestInvocationBuilder =
      testDefinitionForVirtualObject("ReverseAwaitOrder") { ctx, _: Unit ->
        val a1: Awaitable<String> = callGreeterGreetService(ctx, "Francesco")
        val a2: Awaitable<String> = callGreeterGreetService(ctx, "Till")

        val a2Res: String = a2.await()
        ctx.set(StateKey.of("A2", TestSerdes.STRING), a2Res)

        val a1Res: String = a1.await()
        return@testDefinitionForVirtualObject "$a1Res-$a2Res"
      }

  override fun awaitTwiceTheSameAwaitable(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitTwiceTheSameAwaitable") { ctx, _: Unit ->
        val a = callGreeterGreetService(ctx, "Francesco")
        return@testDefinitionForVirtualObject "${a.await()}-${a.await()}"
      }

  override fun awaitAll(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitAll") { ctx, _: Unit ->
        val a1 = callGreeterGreetService(ctx, "Francesco")
        val a2 = callGreeterGreetService(ctx, "Till")

        return@testDefinitionForVirtualObject listOf(a1, a2)
            .awaitAll()
            .joinToString(separator = "-")
      }

  override fun awaitAny(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitAny") { ctx, _: Unit ->
        val a1 = callGreeterGreetService(ctx, "Francesco")
        val a2 = callGreeterGreetService(ctx, "Till")

        return@testDefinitionForVirtualObject Awaitable.any(a1, a2)
            .map { it -> if (it == 0) a1.await() else a2.await() }
            .await()
      }

  private fun awaitSelect(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitSelect") { ctx, _: Unit ->
        val a1 = callGreeterGreetService(ctx, "Francesco")
        val a2 = callGreeterGreetService(ctx, "Till")
        return@testDefinitionForVirtualObject select {
              a1.onAwait { it }
              a2.onAwait { it }
            }
            .await()
      }

  override fun combineAnyWithAll(): TestInvocationBuilder =
      testDefinitionForVirtualObject("CombineAnyWithAll") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val a2 = ctx.awakeable(TestSerdes.STRING)
        val a3 = ctx.awakeable(TestSerdes.STRING)
        val a4 = ctx.awakeable(TestSerdes.STRING)

        val a12 = Awaitable.any(a1, a2).map { if (it == 0) a1.await() else a2.await() }
        val a23 = Awaitable.any(a2, a3).map { if (it == 0) a2.await() else a3.await() }
        val a34 = Awaitable.any(a3, a4).map { if (it == 0) a3.await() else a4.await() }
        Awaitable.all(a12, a23, a34).await()

        return@testDefinitionForVirtualObject a12.await() + a23.await() + a34.await()
      }

  override fun awaitAnyIndex(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitAnyIndex") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val a2 = ctx.awakeable(TestSerdes.STRING)
        val a3 = ctx.awakeable(TestSerdes.STRING)
        val a4 = ctx.awakeable(TestSerdes.STRING)

        return@testDefinitionForVirtualObject Awaitable.any(a1, Awaitable.all(a2, a3), a4)
            .await()
            .toString()
      }

  override fun awaitOnAlreadyResolvedAwaitables(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitOnAlreadyResolvedAwaitables") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val a2 = ctx.awakeable(TestSerdes.STRING)
        val a12 = Awaitable.all(a1, a2)
        val a12and1 = Awaitable.all(a12, a1)
        val a121and12 = Awaitable.all(a12and1, a12)
        a12and1.await()
        a121and12.await()

        return@testDefinitionForVirtualObject a1.await() + a2.await()
      }

  override fun awaitWithTimeout(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitWithTimeout") { ctx, _: Unit ->
        val a1 = callGreeterGreetService(ctx, "Francesco")
        return@testDefinitionForVirtualObject try {
          a1.await(1.days)
        } catch (_: TimeoutException) {
          "timeout"
        }
      }

  override fun definitions(): Stream<TestDefinition> =
      Stream.concat(super.definitions(), super.anyTestDefinitions { awaitSelect() })
}
