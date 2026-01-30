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
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.AwakeableHolder
import dev.restate.sdk.testservices.contracts.CancelTest
import kotlin.time.Duration.Companion.days

class CancelTestImpl {
  class RunnerImpl : CancelTest.Runner {
    companion object {
      private val CANCELED_STATE: StateKey<Boolean> = stateKey("canceled")
    }

    override suspend fun startTest(operation: CancelTest.BlockingOperation) {
      try {
        virtualObject<CancelTest.BlockingService>(key()).block(operation)
      } catch (e: TerminalException) {
        if (e.code == TerminalException.CANCELLED_CODE) {
          state().set(CANCELED_STATE, true)
        } else {
          throw e
        }
      }
    }

    override suspend fun verifyTest(): Boolean {
      return state().get(CANCELED_STATE) ?: false
    }
  }

  class BlockingService : CancelTest.BlockingService {
    override suspend fun block(operation: CancelTest.BlockingOperation) {
      val self = virtualObject<CancelTest.BlockingService>(key())
      val awakeableHolder = virtualObject<AwakeableHolder>(key())

      val awakeable = awakeable<String>()
      awakeableHolder.hold(awakeable.id)
      awakeable.await()

      when (operation) {
        CancelTest.BlockingOperation.CALL -> self.block(operation)
        CancelTest.BlockingOperation.SLEEP -> sleep(1024.days)
        CancelTest.BlockingOperation.AWAKEABLE -> {
          val uncompletable: Awakeable<String> = awakeable<String>()
          uncompletable.await()
        }
      }
    }

    override suspend fun isUnlocked() {
      // no-op
    }
  }
}
