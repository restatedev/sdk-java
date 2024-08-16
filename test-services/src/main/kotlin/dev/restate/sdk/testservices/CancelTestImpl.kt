// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.kotlin.Awakeable
import dev.restate.sdk.kotlin.KtStateKey
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.awakeable
import dev.restate.sdk.testservices.contracts.AwakeableHolderClient
import dev.restate.sdk.testservices.contracts.BlockingOperation
import dev.restate.sdk.testservices.contracts.CancelTest
import dev.restate.sdk.testservices.contracts.CancelTestBlockingServiceClient
import kotlin.time.Duration.Companion.days

class CancelTestImpl {
  class RunnerImpl : CancelTest.Runner {
    companion object {
      private val CANCELED_STATE: StateKey<Boolean> = KtStateKey.json("canceled")
    }

    override suspend fun startTest(context: ObjectContext, operation: BlockingOperation) {
      val client = CancelTestBlockingServiceClient.fromContext(context, "")

      try {
        client.block(operation).await()
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
      val self = CancelTestBlockingServiceClient.fromContext(context, "")
      val client = AwakeableHolderClient.fromContext(context, "cancel")

      val awakeable = context.awakeable<String>()
      client.hold(awakeable.id).await()
      awakeable.await()

      when (operation) {
        BlockingOperation.CALL -> self.block(operation).await()
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
