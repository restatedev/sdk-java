// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.common.TimeoutException
import dev.restate.sdk.core.AsyncResultTestSuite
import dev.restate.sdk.core.TestDefinitions.*
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.callGreeterGreetService
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForVirtualObject
import dev.restate.sdk.core.legacy.ProtoUtils.*
import dev.restate.sdk.kotlin.*
import java.util.stream.Stream
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.coroutineScope

class AsyncResultTest : AsyncResultTestSuite() {
  override fun reverseAwaitOrder(): TestInvocationBuilder =
      testDefinitionForVirtualObject("ReverseAwaitOrder") { ctx, _: Unit ->
        val a1: DurableFuture<String> = callGreeterGreetService(ctx, "Francesco")
        val a2: DurableFuture<String> = callGreeterGreetService(ctx, "Till")

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

        return@testDefinitionForVirtualObject DurableFuture.any(a1, a2)
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

        val a12 = DurableFuture.any(a1, a2).map { if (it == 0) a1.await() else a2.await() }
        val a23 = DurableFuture.any(a2, a3).map { if (it == 0) a2.await() else a3.await() }
        val a34 = DurableFuture.any(a3, a4).map { if (it == 0) a3.await() else a4.await() }
        DurableFuture.all(a12, a23, a34).await()

        return@testDefinitionForVirtualObject a12.await() + a23.await() + a34.await()
      }

  override fun awaitAnyIndex(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitAnyIndex") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val a2 = ctx.awakeable(TestSerdes.STRING)
        val a3 = ctx.awakeable(TestSerdes.STRING)
        val a4 = ctx.awakeable(TestSerdes.STRING)

        return@testDefinitionForVirtualObject DurableFuture.any(a1, DurableFuture.all(a2, a3), a4)
            .await()
            .toString()
      }

  override fun awaitOnAlreadyResolvedAwaitables(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitOnAlreadyResolvedAwaitables") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val a2 = ctx.awakeable(TestSerdes.STRING)
        val a12 = DurableFuture.all(a1, a2)
        val a12and1 = DurableFuture.all(a12, a1)
        val a121and12 = DurableFuture.all(a12and1, a12)
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

  override fun anyWithMapperAwaitingFirstChild(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AnyWithMapperAwaitingFirstChild") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val a2 = ctx.awakeable(TestSerdes.STRING)
        val mappedA2 =
            a2.map { v ->
              ctx.set(StateKey.of("MAPPED", TestSerdes.STRING), v)
              "$v-${a1.await()}"
            }

        return@testDefinitionForVirtualObject DurableFuture.any(a1, mappedA2).await().toString()
      }

  override fun mapFailureRecoversInAll(): TestInvocationBuilder =
      testDefinitionForVirtualObject("MapFailureRecoversInAll") { ctx, _: Unit ->
        val a1 = callGreeterGreetService(ctx, "Francesco").mapFailure { "fallback" }
        val a2 = callGreeterGreetService(ctx, "Till")

        DurableFuture.all(a1, a2).await()

        return@testDefinitionForVirtualObject "${a1.await()}-${a2.await()}"
      }

  override fun awaitWithTimeoutOnMappedFuture(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AwaitWithTimeoutOnMappedFuture") { ctx, _: Unit ->
        return@testDefinitionForVirtualObject callGreeterGreetService(ctx, "Francesco")
            .map { "$it!" }
            .await(1.days)
      }

  override fun mapperThrowingTerminalException(): TestInvocationBuilder =
      testDefinitionForVirtualObject("MapperThrowingTerminalException") { ctx, _: Unit ->
        val mapped =
            callGreeterGreetService(ctx, "Francesco").map<String> {
              throw TerminalException("mapped $it")
            }
        return@testDefinitionForVirtualObject try {
          mapped.await()
        } catch (e: TerminalException) {
          e.message!!
        }
      }

  override fun anyThenAllOnCompletedAwakeables(): TestInvocationBuilder =
      testDefinitionForVirtualObject("AnyThenAllOnCompletedAwakeables") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val a2 = ctx.awakeable(TestSerdes.STRING)
        val a3 = ctx.awakeable(TestSerdes.STRING)

        val index = DurableFuture.any(a1, DurableFuture.all(a2, a3)).await()
        DurableFuture.all(a2, a3).await()

        return@testDefinitionForVirtualObject "$index${a2.await()}${a3.await()}"
      }

  override fun mapperNotRunWhenNotAwaited(): TestInvocationBuilder =
      testDefinitionForVirtualObject("MapperNotRunWhenNotAwaited") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        @Suppress("UNUSED_VARIABLE")
        val unusedMapped =
            a1.map { v ->
              ctx.set(StateKey.of("MAPPED", TestSerdes.STRING), v)
              v
            }

        return@testDefinitionForVirtualObject a1.await()
      }

  /** Mapping in a scope whose Job completes before the mapped future is awaited. */
  private fun mapInCompletedScope(): TestInvocationBuilder =
      testDefinitionForVirtualObject("MapInCompletedScope") { ctx, _: Unit ->
        val a1 = ctx.awakeable(TestSerdes.STRING)
        val mapped = coroutineScope { a1.map { "$it!" } }
        return@testDefinitionForVirtualObject mapped.await()
      }

  override fun definitions(): Stream<TestDefinition> =
      Stream.concat(
          Stream.concat(super.definitions(), super.anyTestDefinitions { awaitSelect() }),
          Stream.of(
              mapInCompletedScope()
                  .withInput(startMessage(2), inputCmd(), signalNotification(17, "1"))
                  .expectingOutput(outputCmd("1!"), END_MESSAGE)
          ),
      )
}
