// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.call
import dev.restate.sdk.kotlin.retryPolicy
import dev.restate.sdk.kotlin.runBlock
import dev.restate.sdk.testservices.contracts.Failing
import dev.restate.sdk.testservices.contracts.FailingHandlers
import dev.restate.sdk.types.TerminalException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class FailingImpl : Failing {
  companion object {
    private val LOG: Logger = LogManager.getLogger(FailingImpl::class.java)
  }

  private val eventualSuccessCalls = AtomicInteger(0)
  private val eventualSuccessSideEffectCalls = AtomicInteger(0)
  private val eventualFailureSideEffectCalls = AtomicInteger(0)

  override suspend fun terminallyFailingCall(context: ObjectContext, errorMessage: String) {
    LOG.info("Invoked fail")

    throw TerminalException(errorMessage)
  }

  override suspend fun callTerminallyFailingCall(
      context: ObjectContext,
      errorMessage: String
  ): String {
    LOG.info("Invoked failAndHandle")

    FailingHandlers.terminallyFailingCall(context.random().nextUUID().toString(), errorMessage)
        .call(context)
        .await()

    throw IllegalStateException("This should be unreachable")
  }

  override suspend fun failingCallWithEventualSuccess(context: ObjectContext): Int {
    val currentAttempt = eventualSuccessCalls.incrementAndGet()

    if (currentAttempt >= 4) {
      eventualSuccessCalls.set(0)
      return currentAttempt
    } else {
      throw IllegalArgumentException("Failed at attempt: $currentAttempt")
    }
  }

  override suspend fun terminallyFailingSideEffect(context: ObjectContext, errorMessage: String) {
    context.runBlock<Unit> { throw TerminalException(errorMessage) }

    throw IllegalStateException("Should not be reached.")
  }

  override suspend fun sideEffectSucceedsAfterGivenAttempts(
      context: ObjectContext,
      minimumAttempts: Int
  ): Int =
      context.runBlock(
          name = "failing_side_effect",
          retryPolicy =
              retryPolicy {
                initialDelay = 10.milliseconds
                exponentiationFactor = 1.0f
              }) {
            val currentAttempt = eventualSuccessSideEffectCalls.incrementAndGet()
            if (currentAttempt >= 4) {
              eventualSuccessSideEffectCalls.set(0)
              return@runBlock currentAttempt
            } else {
              throw IllegalArgumentException("Failed at attempt: $currentAttempt")
            }
          }

  override suspend fun sideEffectFailsAfterGivenAttempts(
      context: ObjectContext,
      retryPolicyMaxRetryCount: Int
  ): Int {
    try {
      context.runBlock<Unit>(
          name = "failing_side_effect",
          retryPolicy =
              retryPolicy {
                initialDelay = 10.milliseconds
                exponentiationFactor = 1.0f
                maxAttempts = retryPolicyMaxRetryCount
              }) {
            val currentAttempt = eventualFailureSideEffectCalls.incrementAndGet()
            throw IllegalArgumentException("Failed at attempt: $currentAttempt")
          }
    } catch (_: TerminalException) {
      return eventualFailureSideEffectCalls.get()
    }
    // If I reach this point, the side effect succeeded...
    throw TerminalException("Expecting the side effect to fail!")
  }
}
