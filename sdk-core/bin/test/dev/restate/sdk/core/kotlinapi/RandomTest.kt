// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.sdk.core.RandomTestSuite
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForService
import kotlin.random.Random

class RandomTest : RandomTestSuite() {
  override fun randomShouldBeDeterministic(): TestInvocationBuilder =
      testDefinitionForService("RandomShouldBeDeterministic") { ctx, _: Unit ->
        ctx.random().nextInt()
      }

  override fun getExpectedInt(seed: Long): Int {
    return Random(seed).nextInt()
  }
}
