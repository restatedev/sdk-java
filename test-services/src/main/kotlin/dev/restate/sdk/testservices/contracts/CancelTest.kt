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

@Serializable
enum class BlockingOperation {
  CALL,
  SLEEP,
  AWAKEABLE,
}

interface CancelTest {

  @VirtualObject
  @Name("CancelTestRunner")
  interface Runner {
    @Exclusive suspend fun startTest(context: ObjectContext, operation: BlockingOperation)

    @Exclusive suspend fun verifyTest(context: ObjectContext): Boolean
  }

  @VirtualObject
  @Name("CancelTestBlockingService")
  interface BlockingService {
    @Exclusive suspend fun block(context: ObjectContext, operation: BlockingOperation)

    @Exclusive suspend fun isUnlocked(context: ObjectContext)
  }
}
