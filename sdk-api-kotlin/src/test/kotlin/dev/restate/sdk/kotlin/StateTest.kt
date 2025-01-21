// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.types.StateKey
import dev.restate.sdk.core.ProtoUtils.*
import dev.restate.sdk.core.StateTestSuite
import dev.restate.sdk.core.TestDefinitions.*
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.testDefinitionForVirtualObject
import java.util.stream.Stream
import kotlinx.serialization.Serializable

class StateTest : StateTestSuite() {

  override fun getState(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetState") { ctx, _: Unit ->
        val state = ctx.get(StateKey.of("STATE", TestSerdes.STRING)) ?: "Unknown"
        "Hello $state"
      }

  override fun getAndSetState(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetAndSetState") { ctx, name: String ->
        val state = ctx.get(StateKey.of("STATE", TestSerdes.STRING))!!
        ctx.set(StateKey.of("STATE", TestSerdes.STRING), name)
        "Hello $state"
      }

  override fun setNullState(): TestInvocationBuilder {
    return unsupported("The kotlin type system enforces non null state values")
  }

  // --- Test using KTSerdes

  @Serializable data class Data(var a: Int, val b: String)

  private companion object {
    val DATA: StateKey<Data> = StateKey.of("STATE", KtSerdes.json())
  }

  private fun getAndSetStateUsingKtSerdes(): TestInvocationBuilder =
      testDefinitionForVirtualObject("GetAndSetStateUsingKtSerdes") { ctx, _: Unit ->
        val state = ctx.get(DATA)!!
        state.a += 1
        ctx.set(DATA, state)

        "Hello $state"
      }

  override fun definitions(): Stream<TestDefinition> {
    return Stream.concat(
        super.definitions(),
        Stream.of(
            getAndSetStateUsingKtSerdes()
                .withInput(
                    startMessage(3),
                    inputMessage(),
                    getStateMessage("STATE", KtSerdes.json(), Data(1, "Till")),
                    setStateMessage("STATE", KtSerdes.json(), Data(2, "Till")))
                .expectingOutput(outputMessage("Hello " + Data(2, "Till")), END_MESSAGE)
                .named("With GetState and SetState"),
            getAndSetStateUsingKtSerdes()
                .withInput(
                    startMessage(2),
                    inputMessage(),
                    getStateMessage("STATE", KtSerdes.json(), Data(1, "Till")))
                .expectingOutput(
                    setStateMessage("STATE", KtSerdes.json(), Data(2, "Till")),
                    outputMessage("Hello " + Data(2, "Till")),
                    END_MESSAGE)
                .named("With GetState already completed"),
        ))
  }
}
