// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.sdk.common.AbortedExecutionException
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.core.StateMachineFailuresTestSuite
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForService
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForVirtualObject
import dev.restate.serde.Serde
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException

class StateMachineFailuresTest : StateMachineFailuresTestSuite() {
  companion object {
    private val STATE =
        StateKey.of(
            "STATE",
            Serde.using({ i: Int -> i.toString().toByteArray(StandardCharsets.UTF_8) }) {
                b: ByteArray? ->
              String(b!!, StandardCharsets.UTF_8).toInt()
            },
        )
  }

  override fun getState(nonTerminalExceptionsSeen: AtomicInteger): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetState") { ctx, _: Unit ->
        try {
          ctx.get(STATE)
        } catch (e: Throwable) {
          // A user should never catch Throwable!!!
          if (AbortedExecutionException.INSTANCE == e) {
            throw e
          }
          // A user should never catch Throwable!!!
          if (e !is CancellationException && e !is TerminalException) {
            nonTerminalExceptionsSeen.addAndGet(1)
          } else {
            throw e
          }
        }
        "Francesco"
      }

  override fun sideEffectFailure(serde: Serde<Int>): TestInvocationBuilder =
      testDefinitionForService("SideEffectFailure") { ctx, _: Unit ->
        ctx.runBlock(serde) { 0 }
        "Francesco"
      }
}
