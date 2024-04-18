// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import com.google.protobuf.ByteString
import dev.restate.sdk.annotation.*
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.common.Target
import dev.restate.sdk.core.ProtoUtils.*
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.TestDefinitions.testInvocation
import java.util.stream.Stream

class CodegenTest : TestDefinitions.TestSuite {
  @Service
  class ServiceGreeter {
    @Handler
    suspend fun greet(context: Context, request: String): String {
      return request
    }
  }

  @VirtualObject
  class ObjectGreeter {
    @Exclusive
    suspend fun greet(context: ObjectContext, request: String): String {
      return request
    }

    @Handler
    @Shared
    suspend fun sharedGreet(context: SharedObjectContext, request: String): String {
      return request
    }
  }

  @VirtualObject
  interface GreeterInterface {
    @Exclusive suspend fun greet(context: ObjectContext, request: String): String
  }

  private class ObjectGreeterImplementedFromInterface : GreeterInterface {
    override suspend fun greet(context: ObjectContext, request: String): String {
      return request
    }
  }

  @Service(name = "Empty")
  class Empty {
    @Handler
    suspend fun emptyInput(context: Context): String {
      val client = EmptyClient.fromContext(context)
      return client.emptyInput().await()
    }

    @Handler
    suspend fun emptyOutput(context: Context, request: String) {
      val client = EmptyClient.fromContext(context)
      client.emptyOutput(request).await()
    }

    @Handler
    suspend fun emptyInputOutput(context: Context) {
      val client = EmptyClient.fromContext(context)
      client.emptyInputOutput().await()
    }
  }

  @Service(name = "PrimitiveTypes")
  class PrimitiveTypes {
    @Handler
    suspend fun primitiveOutput(context: Context): Int {
      val client = PrimitiveTypesClient.fromContext(context)
      return client.primitiveOutput().await()
    }

    @Handler
    suspend fun primitiveInput(context: Context, input: Int) {
      val client = PrimitiveTypesClient.fromContext(context)
      client.primitiveInput(input).await()
    }
  }

  @VirtualObject
  class CornerCases {
    @Exclusive
    suspend fun send(context: ObjectContext, request: String): String {
      // Just needs to compile
      return CodegenTestCornerCasesClient.fromContext(context, request)._send("my_send").await()
    }
  }

  override fun definitions(): Stream<TestDefinition> {
    return Stream.of(
        testInvocation({ ServiceGreeter() }, "greet")
            .withInput(startMessage(1), inputMessage("Francesco"))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE),
        testInvocation({ ObjectGreeter() }, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputMessage("Francesco"))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE),
        testInvocation({ ObjectGreeter() }, "sharedGreet")
            .withInput(startMessage(1, "slinkydeveloper"), inputMessage("Francesco"))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE),
        testInvocation({ ObjectGreeterImplementedFromInterface() }, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputMessage("Francesco"))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Francesco"), END_MESSAGE),
        testInvocation({ Empty() }, "emptyInput")
            .withInput(startMessage(1), inputMessage(), completionMessage(1, "Till"))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(Target.service("Empty", "emptyInput")),
                outputMessage("Till"),
                END_MESSAGE)
            .named("empty output"),
        testInvocation({ Empty() }, "emptyOutput")
            .withInput(
                startMessage(1),
                inputMessage("Francesco"),
                completionMessage(1).setValue(ByteString.EMPTY))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(Target.service("Empty", "emptyOutput"), "Francesco"),
                outputMessage(),
                END_MESSAGE)
            .named("empty output"),
        testInvocation({ Empty() }, "emptyInputOutput")
            .withInput(
                startMessage(1),
                inputMessage("Francesco"),
                completionMessage(1).setValue(ByteString.EMPTY))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(Target.service("Empty", "emptyInputOutput")),
                outputMessage(),
                END_MESSAGE)
            .named("empty input and empty output"),
        testInvocation({ PrimitiveTypes() }, "primitiveOutput")
            .withInput(
                startMessage(1), inputMessage(), completionMessage(1, CoreSerdes.JSON_INT, 10))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    Target.service("PrimitiveTypes", "primitiveOutput"), CoreSerdes.VOID, null),
                outputMessage(CoreSerdes.JSON_INT, 10),
                END_MESSAGE)
            .named("primitive output"),
        testInvocation({ PrimitiveTypes() }, "primitiveInput")
            .withInput(
                startMessage(1), inputMessage(10), completionMessage(1).setValue(ByteString.EMPTY))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    Target.service("PrimitiveTypes", "primitiveInput"), CoreSerdes.JSON_INT, 10),
                outputMessage(),
                END_MESSAGE)
            .named("primitive input"))
  }
}
