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
import dev.restate.sdk.testservices.contracts.AwakeableHolder
import dev.restate.sdk.testservices.contracts.KillTest
import dev.restate.serde.Serde

class KillTestImpl {
  class RunnerImpl : KillTest.Runner {
    // The call tree method invokes the KillSingletonService::recursiveCall which blocks on calling
    // itself again.
    // This will ensure that we have a call tree that is two calls deep and has a pending invocation
    // in the inbox:
    // startCallTree --> recursiveCall --> recursiveCall:inboxed
    override suspend fun startCallTree() {
      virtualObject<KillTest.Singleton>(key()).recursiveCall()
    }
  }

  class SingletonImpl : KillTest.Singleton {
    override suspend fun recursiveCall() {
      val awakeable = awakeable(Serde.RAW)
      toVirtualObject<AwakeableHolder>(key()).request { hold(awakeable.id) }.send()
      awakeable.await()

      virtualObject<KillTest.Singleton>(key()).recursiveCall()
    }

    override suspend fun isUnlocked() {
      // no-op
    }
  }
}
