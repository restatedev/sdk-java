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
import dev.restate.sdk.common.Serde
import dev.restate.sdk.common.Target
import dev.restate.sdk.core.ProtoUtils.*
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.TestDefinitions.testInvocation
import dev.restate.sdk.core.TestSerdes
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

    @Exclusive
    suspend fun returnNull(context: ObjectContext, request: String?): String? {
      return CodegenTestCornerCasesClient.fromContext(context, context.key())
          .returnNull(request)
          .await()
    }
  }

  @Workflow
  class WorkflowCornerCases {
    @Workflow
    fun process(context: WorkflowContext, request: String): String {
      return ""
    }

    @Shared
    suspend fun submit(context: SharedWorkflowContext, request: String): String {
      // Just needs to compile
      val ignored: String =
          CodegenTestWorkflowCornerCasesClient.fromIngress("invalid", request)._submit("my_send")
      return CodegenTestWorkflowCornerCasesClient.fromIngress("invalid", request)
          .submit("my_send")
          .output
    }
  }

  @Service(name = "RawInputOutput")
  class RawInputOutput {
    @Handler
    @Raw
    suspend fun rawOutput(context: Context): ByteArray {
      val client: RawInputOutputClient.ContextClient = RawInputOutputClient.fromContext(context)
      return client.rawOutput().await()
    }

    @Handler
    @Raw(contentType = "application/vnd.my.custom")
    suspend fun rawOutputWithCustomCT(context: Context): ByteArray {
      val client: RawInputOutputClient.ContextClient = RawInputOutputClient.fromContext(context)
      return client.rawOutputWithCustomCT().await()
    }

    @Handler
    suspend fun rawInput(context: Context, @Raw input: ByteArray) {
      val client: RawInputOutputClient.ContextClient = RawInputOutputClient.fromContext(context)
      client.rawInput(input).await()
    }

    @Handler
    suspend fun rawInputWithCustomCt(
        context: Context,
        @Raw(contentType = "application/vnd.my.custom") input: ByteArray
    ) {
      val client: RawInputOutputClient.ContextClient = RawInputOutputClient.fromContext(context)
      client.rawInputWithCustomCt(input).await()
    }

    @Handler
    suspend fun rawInputWithCustomAccept(
        context: Context,
        @Accept("application/*") @Raw(contentType = "application/vnd.my.custom") input: ByteArray
    ) {
      val client: RawInputOutputClient.ContextClient = RawInputOutputClient.fromContext(context)
      client.rawInputWithCustomCt(input).await()
    }
  }

  @Workflow(name = "MyWorkflow")
  class MyWorkflow {
    @Workflow
    suspend fun run(context: WorkflowContext, myInput: String) {
      val client = MyWorkflowClient.fromContext(context, context.key())
      client.send().sharedHandler(myInput)
    }

    @Handler
    suspend fun sharedHandler(context: SharedWorkflowContext, myInput: String): String {
      val client = MyWorkflowClient.fromContext(context, context.key())
      return client.sharedHandler(myInput).await()
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
            .withInput(startMessage(1), inputMessage(), completionMessage(1, TestSerdes.INT, 10))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    Target.service("PrimitiveTypes", "primitiveOutput"), Serde.VOID, null),
                outputMessage(TestSerdes.INT, 10),
                END_MESSAGE)
            .named("primitive output"),
        testInvocation({ PrimitiveTypes() }, "primitiveInput")
            .withInput(
                startMessage(1), inputMessage(10), completionMessage(1).setValue(ByteString.EMPTY))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    Target.service("PrimitiveTypes", "primitiveInput"), TestSerdes.INT, 10),
                outputMessage(),
                END_MESSAGE)
            .named("primitive input"),
        testInvocation({ RawInputOutput() }, "rawInput")
            .withInput(
                startMessage(1),
                inputMessage("{{".toByteArray()),
                completionMessage(1, KtSerdes.UNIT, null))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(Target.service("RawInputOutput", "rawInput"), "{{".toByteArray()),
                outputMessage(),
                END_MESSAGE),
        testInvocation({ RawInputOutput() }, "rawInputWithCustomCt")
            .withInput(
                startMessage(1),
                inputMessage("{{".toByteArray()),
                completionMessage(1, KtSerdes.UNIT, null))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    Target.service("RawInputOutput", "rawInputWithCustomCt"), "{{".toByteArray()),
                outputMessage(),
                END_MESSAGE),
        testInvocation({ RawInputOutput() }, "rawOutput")
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1, Serde.RAW, "{{".toByteArray()))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(Target.service("RawInputOutput", "rawOutput"), KtSerdes.UNIT, null),
                outputMessage("{{".toByteArray()),
                END_MESSAGE),
        testInvocation({ RawInputOutput() }, "rawOutputWithCustomCT")
            .withInput(
                startMessage(1),
                inputMessage(),
                completionMessage(1, Serde.RAW, "{{".toByteArray()))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    Target.service("RawInputOutput", "rawOutputWithCustomCT"), KtSerdes.UNIT, null),
                outputMessage("{{".toByteArray()),
                END_MESSAGE),
        testInvocation({ CornerCases() }, "returnNull")
            .withInput(
                startMessage(1, "mykey"),
                inputMessage(KtSerdes.json<String?>().serialize(null)),
                completionMessage(1, KtSerdes.json<String?>(), null))
            .onlyUnbuffered()
            .expectingOutput(
                invokeMessage(
                    Target.virtualObject("CodegenTestCornerCases", "mykey", "returnNull"),
                    KtSerdes.json<String?>(),
                    null),
                outputMessage(KtSerdes.json<String?>(), null),
                END_MESSAGE),
    )
  }
}
