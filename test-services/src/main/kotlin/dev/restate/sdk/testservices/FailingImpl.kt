// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.runBlock
import dev.restate.sdktesting.contracts.Failing
import dev.restate.sdktesting.contracts.FailingClient
import java.util.concurrent.atomic.AtomicInteger
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class FailingImpl : Failing {
  companion object {
    private val LOG: Logger = LogManager.getLogger(FailingImpl::class.java)
  }

  private val eventualSuccessCalls = AtomicInteger(0)
  private val eventualSuccessSideEffectCalls = AtomicInteger(0)

  override suspend fun terminallyFailingCall(context: ObjectContext, errorMessage: String) {
    LOG.info("Invoked fail")

    throw TerminalException(errorMessage)
  }

  override suspend fun callTerminallyFailingCall(
      context: ObjectContext,
      errorMessage: String
  ): String {
    LOG.info("Invoked failAndHandle")

    FailingClient.fromContext(context, context.random().nextUUID().toString())
        .terminallyFailingCall(errorMessage)
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

  override suspend fun failingSideEffectWithEventualSuccess(context: ObjectContext): Int {
    val successAttempt: Int =
        context.runBlock {
          val currentAttempt = eventualSuccessSideEffectCalls.incrementAndGet()
          if (currentAttempt >= 4) {
            eventualSuccessSideEffectCalls.set(0)
            return@runBlock currentAttempt
          } else {
            throw IllegalArgumentException("Failed at attempt: $currentAttempt")
          }
        }

    return successAttempt
  }

  override suspend fun terminallyFailingSideEffect(context: ObjectContext, errorMessage: String) {
    context.runBlock<Unit> { throw TerminalException(errorMessage) }

    throw IllegalStateException("Should not be reached.")
  }
}
