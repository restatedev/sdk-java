// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.DeferredTestSuite
import dev.restate.sdk.core.TestDefinitions.*
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.callGreeterGreetService
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.testDefinitionForVirtualObject
import dev.restate.sdk.types.StateKey
import java.util.stream.Stream

class DeferredTest : DeferredTestSuite() {
  override fun reverseAwaitOrder(): TestInvocationBuilder =
      testDefinitionForVirtualObject("ReverseAwaitOrder") { ctx, _: Unit ->
        val a1: Awaitable<String> = callGreeterGreetService(ctx, "Francesco")
        val a2: Awaitable<String> = callGreeterGreetService(ctx, "Till")

        val a2Res: String = a2.await()
        ctx.set(StateKey.of("A2", TestSerdes.STRING), a2Res)

        val a1Res: String = a1.await()
        "$a1Res-$a2Res"
      }

  override fun awaitTwiceTheSameAwaitable(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitTwiceTheSameAwaitable") { ctx, _: Unit ->
        val a = callGreeterGreetService(ctx, "Francesco")
        "${a.await()}-${a.await()}"
      }

  override fun awaitAll(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitAll") { ctx, _: Unit ->
        val a1 = callGreeterGreetService(ctx, "Francesco")
        val a2 = callGreeterGreetService(ctx, "Till")

        listOf(a1, a2).awaitAll().joinToString(separator = "-")
      }

  override fun awaitAny(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitAny") { ctx, _: Unit ->
        val a1 = callGreeterGreetService(ctx, "Francesco")
        val a2 = callGreeterGreetService(ctx, "Till")
        Awaitable.any(a1, a2).await() as String
      }

  private fun awaitSelect(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitSelect") { ctx, _: Unit ->
        val a1 = callGreeterGreetService(ctx, "Francesco")
        val a2 = callGreeterGreetService(ctx, "Till")
        select {
          a1.onAwait { it }
          a2.onAwait { it }
        }
      }

  override fun combineAnyWithAll(): TestInvocationBuilder =
      testDefinitionForVirtualObject("CombineAnyWithAll") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val a2 = ctx.awakeable(TestSerdes.STRING)
        val a3 = ctx.awakeable(TestSerdes.STRING)
        val a4 = ctx.awakeable(TestSerdes.STRING)

        val a12 = Awaitable.any(a1, a2)
        val a23 = Awaitable.any(a2, a3)
        val a34 = Awaitable.any(a3, a4)
        Awaitable.all(a12, a23, a34).await()

        a12.await().toString() + a23.await() as String + a34.await()
      }

  override fun awaitAnyIndex(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitAnyIndex") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val a2 = ctx.awakeable(TestSerdes.STRING)
        val a3 = ctx.awakeable(TestSerdes.STRING)
        val a4 = ctx.awakeable(TestSerdes.STRING)

        Awaitable.any(a1, Awaitable.all(a2, a3), a4).awaitIndex().toString()
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

        a1.await() + a2.await()
      }

  override fun awaitWithTimeout(): TestInvocationBuilder {
    return unsupported("This is a feature not available in sdk-api-kotlin")
  }

  override fun definitions(): Stream<TestDefinition> =
      Stream.concat(super.definitions(), super.anyTestDefinitions { awaitSelect() })
}
