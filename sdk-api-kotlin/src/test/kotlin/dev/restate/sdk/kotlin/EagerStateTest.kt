// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.core.EagerStateTestSuite
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.testDefinitionForVirtualObject
import org.assertj.core.api.AssertionsForClassTypes.assertThat

class EagerStateTest : EagerStateTestSuite() {
  override fun getEmpty(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetEmpty") { ctx, _: Unit ->
        val stateIsEmpty = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)) == null
        stateIsEmpty.toString()
      }

  override fun get(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetEmpty") { ctx, _: Unit ->
        ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!
      }

  override fun getAppendAndGet(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetAppendAndGet") { ctx, name: String ->
        val oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!
        ctx.set(StateKey.of("STATE", CoreSerdes.JSON_STRING), oldState + name)
        ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!
      }

  override fun getClearAndGet(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetClearAndGet") { ctx, _: Unit ->
        val oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!
        ctx.clear(StateKey.of("STATE", CoreSerdes.JSON_STRING))
        assertThat(ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))).isNull()
        oldState
      }

  override fun getClearAllAndGet(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetClearAllAndGet") { ctx, _: Unit ->
        val oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!

        ctx.clearAll()

        assertThat(ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))).isNull()
        assertThat(ctx.get(StateKey.of("ANOTHER_STATE", CoreSerdes.JSON_STRING))).isNull()
        oldState
      }

  override fun listKeys(): TestInvocationBuilder =
      testDefinitionForVirtualObject("ListKeys") { ctx, _: Unit ->
        ctx.stateKeys().joinToString(separator = ",")
      }
}
