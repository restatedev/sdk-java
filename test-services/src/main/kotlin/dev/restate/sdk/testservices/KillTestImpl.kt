// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.common.Serde
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdktesting.contracts.AwakeableHolderClient
import dev.restate.sdktesting.contracts.KillTest
import dev.restate.sdktesting.contracts.KillTestSingletonClient

class KillTestImpl {
  class RunnerImpl : KillTest.Runner {
    // The call tree method invokes the KillSingletonService::recursiveCall which blocks on calling
    // itself again.
    // This will ensure that we have a call tree that is two calls deep and has a pending invocation
    // in the inbox:
    // startCallTree --> recursiveCall --> recursiveCall:inboxed
    override suspend fun startCallTree(context: Context) {
      KillTestSingletonClient.fromContext(context, "").recursiveCall().await()
    }
  }

  class SingletonImpl : KillTest.Singleton {
    override suspend fun recursiveCall(context: ObjectContext) {
      val awakeable = context.awakeable(Serde.RAW)
      AwakeableHolderClient.fromContext(context, "kill").send().hold(awakeable.id)

      awakeable.await()

      KillTestSingletonClient.fromContext(context, "").recursiveCall().await()
    }

    override suspend fun isUnlocked(context: ObjectContext) {
      // no-op
    }
  }
}
