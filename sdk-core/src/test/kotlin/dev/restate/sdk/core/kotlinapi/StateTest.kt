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
import dev.restate.sdk.core.StateTestSuite
import dev.restate.sdk.core.TestDefinitions.*
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForVirtualObject
import dev.restate.sdk.core.statemachine.ProtoUtils.*
import dev.restate.sdk.kotlin.*
import dev.restate.serde.kotlinx.*
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
    val DATA = stateKey<Data>("STATE")
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
                    inputCmd(),
                    getEagerStateCmd("STATE", jsonSerde<Data>(), Data(1, "Till")),
                    setStateCmd("STATE", jsonSerde<Data>(), Data(2, "Till")))
                .expectingOutput(outputCmd("Hello " + Data(2, "Till")), END_MESSAGE)
                .named("With GetState and SetState"),
            getAndSetStateUsingKtSerdes()
                .withInput(
                    startMessage(2),
                    inputCmd(),
                    getEagerStateCmd("STATE", jsonSerde<Data>(), Data(1, "Till")))
                .expectingOutput(
                    setStateCmd("STATE", jsonSerde<Data>(), Data(2, "Till")),
                    outputCmd("Hello " + Data(2, "Till")),
                    END_MESSAGE)
                .named("With GetState already completed"),
        ))
  }
}
