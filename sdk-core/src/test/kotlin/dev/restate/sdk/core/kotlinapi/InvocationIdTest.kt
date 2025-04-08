// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.sdk.core.InvocationIdTestSuite
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForService

class InvocationIdTest : InvocationIdTestSuite() {

  override fun returnInvocationId(): TestInvocationBuilder =
      testDefinitionForService("ReturnInvocationId") { ctx, _: Unit ->
        ctx.request().getInvocationId().toString()
      }
}
