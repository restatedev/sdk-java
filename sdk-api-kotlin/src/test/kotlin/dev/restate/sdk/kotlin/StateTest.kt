// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import com.google.protobuf.kotlin.toByteStringUtf8
import dev.restate.generated.service.protocol.Protocol
import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.core.ProtoUtils.*
import dev.restate.sdk.core.StateTestSuite
import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.TestDefinitions.testInvocation
import dev.restate.sdk.core.testservices.*
import io.grpc.BindableService
import java.util.stream.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StateTest : StateTestSuite() {
  private class GetState :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtComponent {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val state: String =
          ObjectContext.current().get(StateKey.of("STATE", CoreSerdes.JSON_STRING)) ?: "Unknown"
      return greetingResponse { message = "Hello $state" }
    }
  }

  override fun getState(): BindableService {
    return GetState()
  }

  private class GetAndSetState :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtComponent {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = ObjectContext.current()

      val state = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!
      ctx.set(StateKey.of("STATE", CoreSerdes.JSON_STRING), request.getName())

      return greetingResponse { message = "Hello $state" }
    }
  }

  override fun getAndSetState(): BindableService {
    return GetAndSetState()
  }

  override fun setNullState(): BindableService {
    throw UnsupportedOperationException("The kotlin type system enforces non null state values")
  }

  // --- Test using KTSerdes

  @Serializable data class Data(var a: Int, val b: String)

  private class GetAndSetStateUsingKtSerdes : GreeterRestateKt.GreeterRestateKtImplBase() {

    companion object {
      val DATA: StateKey<Data> = StateKey.of("STATE", KtSerdes.json())
    }

    override suspend fun greet(context: ObjectContext, request: GreetingRequest): GreetingResponse {
      val state = context.get(DATA)!!
      state.a += 1
      context.set(DATA, state)

      return greetingResponse { message = "Hello $state" }
    }
  }

  override fun definitions(): Stream<TestDefinition> {
    return Stream.concat(
        super.definitions(),
        Stream.of(
            testInvocation(GetAndSetStateUsingKtSerdes(), GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(3),
                    inputMessage(greetingRequest("Till")),
                    getStateMessage("STATE", Data(1, "Till")),
                    setStateMessage("STATE", Data(2, "Till")))
                .expectingOutput(
                    outputMessage(greetingResponse("Hello " + Data(2, "Till"))), END_MESSAGE)
                .named("With GetState and SetState"),
            testInvocation(GetAndSetStateUsingKtSerdes(), GreeterGrpc.getGreetMethod())
                .withInput(
                    startMessage(2),
                    inputMessage(greetingRequest("Till")),
                    getStateMessage("STATE", Data(1, "Till")))
                .expectingOutput(
                    setStateMessage("STATE", Data(2, "Till")),
                    outputMessage(greetingResponse("Hello " + Data(2, "Till"))),
                    END_MESSAGE)
                .named("With GetState already completed"),
        ))
  }

  companion object {
    fun getStateMessage(key: String, data: Data): Protocol.GetStateEntryMessage.Builder {
      return Protocol.GetStateEntryMessage.newBuilder()
          .setKey(key.toByteStringUtf8())
          .setValue(Json.encodeToString(data).toByteStringUtf8())
    }

    fun setStateMessage(key: String, data: Data): Protocol.SetStateEntryMessage.Builder {
      return Protocol.SetStateEntryMessage.newBuilder()
          .setKey(key.toByteStringUtf8())
          .setValue(Json.encodeToString(data).toByteStringUtf8())
    }
  }
}
