// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.core.UserFailuresTestSuite
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.testDefinitionForService
import dev.restate.sdk.types.TerminalException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class UserFailuresTest : UserFailuresTestSuite() {
  override fun throwIllegalStateException(): TestInvocationBuilder =
      testDefinitionForService<Unit, Unit>("ThrowIllegalStateException") { _, _: Unit ->
        throw IllegalStateException("Whatever")
      }

  override fun sideEffectThrowIllegalStateException(
      nonTerminalExceptionsSeen: AtomicInteger
  ): TestInvocationBuilder =
      testDefinitionForService<Unit, Unit>("SideEffectThrowIllegalStateException") { ctx, _: Unit ->
        try {
          ctx.runBlock { throw IllegalStateException("Whatever") }
        } catch (e: Throwable) {
          if (e !is CancellationException && e !is TerminalException) {
            nonTerminalExceptionsSeen.addAndGet(1)
          } else {
            throw e
          }
        }
        throw IllegalStateException("Not expected to reach this point")
      }

  override fun throwTerminalException(code: Int, message: String): TestInvocationBuilder =
      testDefinitionForService<Unit, Unit>("ThrowTerminalException") { _, _: Unit ->
        throw TerminalException(code, message)
      }

  override fun sideEffectThrowTerminalException(code: Int, message: String): TestInvocationBuilder =
      testDefinitionForService<Unit, Unit>("SideEffectThrowTerminalException") { ctx, _: Unit ->
        ctx.runBlock<Unit> { throw TerminalException(code, message) }
        throw IllegalStateException("Not expected to reach this point")
      }
}
