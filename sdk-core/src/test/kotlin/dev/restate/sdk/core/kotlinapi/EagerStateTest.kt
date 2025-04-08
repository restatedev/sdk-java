// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.sdk.common.StateKey
import dev.restate.sdk.core.EagerStateTestSuite
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForVirtualObject
import org.assertj.core.api.AssertionsForClassTypes.assertThat

class EagerStateTest : EagerStateTestSuite() {
  override fun getEmpty(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetEmpty") { ctx, _: Unit ->
        val stateIsEmpty = ctx.get(StateKey.of("STATE", TestSerdes.STRING)) == null
        stateIsEmpty.toString()
      }

  override fun get(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetEmpty") { ctx, _: Unit ->
        ctx.get(StateKey.of("STATE", TestSerdes.STRING))!!
      }

  override fun getAppendAndGet(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetAppendAndGet") { ctx, name: String ->
        val oldState = ctx.get(StateKey.of("STATE", TestSerdes.STRING))!!
        ctx.set(StateKey.of("STATE", TestSerdes.STRING), oldState + name)
        ctx.get(StateKey.of("STATE", TestSerdes.STRING))!!
      }

  override fun getClearAndGet(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetClearAndGet") { ctx, _: Unit ->
        val oldState = ctx.get(StateKey.of("STATE", TestSerdes.STRING))!!
        ctx.clear(StateKey.of("STATE", TestSerdes.STRING))
        assertThat(ctx.get(StateKey.of("STATE", TestSerdes.STRING))).isNull()
        oldState
      }

  override fun getClearAllAndGet(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetClearAllAndGet") { ctx, _: Unit ->
        val oldState = ctx.get(StateKey.of("STATE", TestSerdes.STRING))!!

        ctx.clearAll()

        assertThat(ctx.get(StateKey.of("STATE", TestSerdes.STRING))).isNull()
        assertThat(ctx.get(StateKey.of("ANOTHER_STATE", TestSerdes.STRING))).isNull()
        oldState
      }

  override fun listKeys(): TestInvocationBuilder =
      testDefinitionForVirtualObject("ListKeys") { ctx, _: Unit ->
        ctx.stateKeys().joinToString(separator = ",")
      }

  override fun consecutiveGetWithEmpty(): TestInvocationBuilder =
      testDefinitionForVirtualObject("ConsecutiveGetWithEmpty") { ctx, _: Unit ->
        assertThat(ctx.get(StateKey.of("key-0", TestSerdes.STRING))).isNull()
        assertThat(ctx.get(StateKey.of("key-0", TestSerdes.STRING))).isNull()
      }
}
