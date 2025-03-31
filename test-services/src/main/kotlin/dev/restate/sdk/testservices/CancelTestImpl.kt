// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.AwakeableHolderHandlers
import dev.restate.sdk.testservices.contracts.BlockingOperation
import dev.restate.sdk.testservices.contracts.CancelTest
import dev.restate.sdk.testservices.contracts.CancelTestBlockingServiceHandlers
import dev.restate.sdk.types.StateKey
import dev.restate.sdk.types.TerminalException
import kotlin.time.Duration.Companion.days

class CancelTestImpl {
  class RunnerImpl : CancelTest.Runner {
    companion object {
      private val CANCELED_STATE: StateKey<Boolean> = stateKey("canceled")
    }

    override suspend fun startTest(context: ObjectContext, operation: BlockingOperation) {
      try {
        CancelTestBlockingServiceHandlers.block(context.key(), operation).call(context).await()
      } catch (e: TerminalException) {
        if (e.code == TerminalException.CANCELLED_CODE) {
          context.set(CANCELED_STATE, true)
        } else {
          throw e
        }
      }
    }

    override suspend fun verifyTest(context: ObjectContext): Boolean {
      return context.get(CANCELED_STATE) ?: false
    }
  }

  class BlockingService : CancelTest.BlockingService {
    override suspend fun block(context: ObjectContext, operation: BlockingOperation) {
      val awakeable = context.awakeable<String>()
      AwakeableHolderHandlers.hold(context.key(), awakeable.id).call(context).await()
      awakeable.await()

      when (operation) {
        BlockingOperation.CALL -> CancelTestBlockingServiceHandlers.block(context.key(), operation).call(context).await()
        BlockingOperation.SLEEP -> context.sleep(1024.days)
        BlockingOperation.AWAKEABLE -> {
          val uncompletable: Awakeable<String> = context.awakeable<String>()
          uncompletable.await()
        }
      }
    }

    override suspend fun isUnlocked(context: ObjectContext) {
      // no-op
    }
  }
}
