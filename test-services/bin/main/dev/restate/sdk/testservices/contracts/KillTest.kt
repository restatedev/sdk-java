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
import dev.restate.sdk.kotlin.*

interface KillTest {
  @VirtualObject
  @Name("KillTestRunner")
  interface Runner {
    @Handler suspend fun startCallTree()
  }

  @VirtualObject
  @Name("KillTestSingleton")
  interface Singleton {
    @Handler suspend fun recursiveCall()

    @Handler suspend fun isUnlocked()
  }
}
