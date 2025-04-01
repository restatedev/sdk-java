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
import dev.restate.sdk.testservices.contracts.AwakeableHolderClient
import dev.restate.sdk.testservices.contracts.KillTest
import dev.restate.sdk.testservices.contracts.KillTestSingletonClient
import dev.restate.serde.Serde

class KillTestImpl {
  class RunnerImpl : KillTest.Runner {
    // The call tree method invokes the KillSingletonService::recursiveCall which blocks on calling
    // itself again.
    // This will ensure that we have a call tree that is two calls deep and has a pending invocation
    // in the inbox:
    // startCallTree --> recursiveCall --> recursiveCall:inboxed
    override suspend fun startCallTree(context: ObjectContext) {
      KillTestSingletonClient.fromContext(context, context.key()).recursiveCall().await()
    }
  }

  class SingletonImpl : KillTest.Singleton {
    override suspend fun recursiveCall(context: ObjectContext) {
      val awakeable = context.awakeable(Serde.RAW)
      AwakeableHolderClient.fromContext(context, context.key()).send().hold(awakeable.id)

      awakeable.await()

      KillTestSingletonClient.fromContext(context, context.key()).recursiveCall().await()
    }

    override suspend fun isUnlocked(context: ObjectContext) {
      // no-op
    }
  }
}
