// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.OnlyInputAndOutputTestSuite
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.testDefinitionForService

class OnlyInputAndOutputTest : OnlyInputAndOutputTestSuite() {

  override fun noSyscallsGreeter(): TestInvocationBuilder =
      testDefinitionForService("NoSyscallsGreeter") { _, name: String -> "Hello $name" }
}
