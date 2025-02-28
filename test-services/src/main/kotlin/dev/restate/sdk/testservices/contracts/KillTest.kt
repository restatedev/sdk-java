// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices.contracts

import dev.restate.sdk.annotation.*
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.ObjectContext

interface KillTest {
  @Service(name = "KillTestRunner")
  interface Runner {
    @Handler suspend fun startCallTree(context: Context)
  }

  @VirtualObject(name = "KillTestSingleton")
  interface Singleton {
    @Handler suspend fun recursiveCall(context: ObjectContext)

    @Handler suspend fun isUnlocked(context: ObjectContext)
  }
}
