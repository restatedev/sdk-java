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
import kotlinx.serialization.Serializable

interface CancelTest {

  @Serializable
  enum class BlockingOperation {
    CALL,
    SLEEP,
    AWAKEABLE,
  }

  @VirtualObject
  @Name("CancelTestRunner")
  interface Runner {
    @Handler suspend fun startTest(operation: BlockingOperation)

    @Handler suspend fun verifyTest(): Boolean
  }

  @VirtualObject
  @Name("CancelTestBlockingService")
  interface BlockingService {
    @Handler suspend fun block(operation: BlockingOperation)

    @Handler suspend fun isUnlocked()
  }
}
