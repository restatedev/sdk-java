// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.common.Slice
import dev.restate.common.Target
import dev.restate.sdk.annotation.*
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.TestDefinitions.testInvocation
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.core.statemachine.ProtoUtils.*
import dev.restate.sdk.kotlin.*
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import dev.restate.serde.TypeRef
import dev.restate.serde.TypeTag
import dev.restate.serde.kotlinx.*
import java.util.stream.Stream
import kotlinx.serialization.Serializable

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
  class NestedDataClass {
    @Serializable data class Input(val a: String)

    @Serializable data class Output(val a: String)

    @Exclusive
    suspend fun greet(context: ObjectContext, request: Input): Output {
      return Output(request.a)
    }

    @Exclusive
    suspend fun complexType(
        context: ObjectContext,
        request: Map<String, List<out Input>>
    ): Map<String, List<out Output>> {
      return mapOf()
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

  @Service
  @Name("Empty")
  class Empty {
    @Handler
    suspend fun emptyInput(context: Context): String {
      val client = CodegenTestEmptyClient.fromContext(context)
      return client.emptyInput().await()
    }

    @Handler
    suspend fun emptyOutput(context: Context, request: String) {
      val client = CodegenTestEmptyClient.fromContext(context)
      client.emptyOutput(request).await()
    }

    @Handler
    suspend fun emptyInputOutput(context: Context) {
      val client = CodegenTestEmptyClient.fromContext(context)
      client.emptyInputOutput().await()
    }
  }

  @Service
  @Name("PrimitiveTypes")
  class PrimitiveTypes {
    @Handler
    suspend fun primitiveOutput(context: Context): Int {
      val client = CodegenTestPrimitiveTypesClient.fromContext(context)
      return client.primitiveOutput().await()
    }

    @Handler
    suspend fun primitiveInput(context: Context, input: Int) {
      val client = CodegenTestPrimitiveTypesClient.fromContext(context)
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
          .returnNull(request) {}
          .await()
    }

    @Exclusive
    suspend fun badReturnTypeInferred(context: ObjectContext): Unit {
      CodegenTestCornerCasesClient.fromContext(context, context.key())
          .send()
          .badReturnTypeInferred()
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
          CodegenTestWorkflowCornerCasesClient.connect("invalid", request)._submit("my_send")
      CodegenTestWorkflowCornerCasesClient.connect("invalid", request).submit("my_send")
      return CodegenTestWorkflowCornerCasesClient.connect("invalid", request)
          .workflowHandle()
          .output
          .response()
          .value
    }
  }

  @Service
  @Name("RawInputOutput")
  class RawInputOutput {
    @Handler
    @Raw
    suspend fun rawOutput(context: Context): ByteArray {
      val client: CodegenTestRawInputOutputClient.ContextClient =
          CodegenTestRawInputOutputClient.fromContext(context)
      return client.rawOutput().await()
    }

    @Handler
    @Raw(contentType = "application/vnd.my.custom")
    suspend fun rawOutputWithCustomCT(context: Context): ByteArray {
      val client: CodegenTestRawInputOutputClient.ContextClient =
          CodegenTestRawInputOutputClient.fromContext(context)
      return client.rawOutputWithCustomCT().await()
    }

    @Handler
    suspend fun rawInput(context: Context, @Raw input: ByteArray) {
      val client: CodegenTestRawInputOutputClient.ContextClient =
          CodegenTestRawInputOutputClient.fromContext(context)
      client.rawInput(input).await()
    }

    @Handler
    suspend fun rawInputWithCustomCt(
        context: Context,
        @Raw(contentType = "application/vnd.my.custom") input: ByteArray
    ) {
      val client: CodegenTestRawInputOutputClient.ContextClient =
          CodegenTestRawInputOutputClient.fromContext(context)
      client.rawInputWithCustomCt(input).await()
    }

    @Handler
    suspend fun rawInputWithCustomAccept(
        context: Context,
        @Accept("application/*") @Raw(contentType = "application/vnd.my.custom") input: ByteArray
    ) {
      val client: CodegenTestRawInputOutputClient.ContextClient =
          CodegenTestRawInputOutputClient.fromContext(context)
      client.rawInputWithCustomCt(input).await()
    }
  }

  @Workflow
  @Name("MyWorkflow")
  class MyWorkflow {
    @Workflow
    suspend fun run(context: WorkflowContext, myInput: String) {
      val client = CodegenTestMyWorkflowClient.fromContext(context, context.key())
      client.send().sharedHandler(myInput)
    }

    @Handler
    suspend fun sharedHandler(context: SharedWorkflowContext, myInput: String): String {
      val client = CodegenTestMyWorkflowClient.fromContext(context, context.key())
      return client.sharedHandler(myInput).await()
    }
  }

  class MyCustomSerdeFactory : SerdeFactory {
    override fun <T : Any?> create(typeTag: TypeTag<T?>): Serde<T?> {
      check(typeTag is KotlinSerializationSerdeFactory.KtTypeTag)
      check(typeTag.type == Byte::class)
      return Serde.using<Byte>({ b -> byteArrayOf(b) }, { it[0] }) as Serde<T?>
    }

    override fun <T : Any?> create(typeRef: TypeRef<T?>): Serde<T?> {
      check(typeRef.type == Byte::class)
      return Serde.using<Byte>({ b -> byteArrayOf(b) }, { it[0] }) as Serde<T?>
    }

    override fun <T : Any?> create(clazz: Class<T?>?): Serde<T?> {
      check(clazz == Byte::class.java)
      return Serde.using<Byte>({ b -> byteArrayOf(b) }, { it[0] }) as Serde<T?>
    }
  }

  @CustomSerdeFactory(MyCustomSerdeFactory::class)
  @Service
  @Name("CustomSerdeService")
  class CustomSerdeService {
    @Handler
    suspend fun echo(context: Context, input: Byte): Byte {
      return input
    }
  }

  override fun definitions(): Stream<TestDefinition> {
    return Stream.of(
        testInvocation({ ServiceGreeter() }, "greet")
            .withInput(startMessage(1), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation({ ObjectGreeter() }, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation({ ObjectGreeter() }, "sharedGreet")
            .withInput(startMessage(1, "slinkydeveloper"), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation({ NestedDataClass() }, "greet")
            .withInput(
                startMessage(1, "slinkydeveloper"),
                inputCmd(jsonSerde<NestedDataClass.Input>(), NestedDataClass.Input("123")))
            .onlyBidiStream()
            .expectingOutput(
                outputCmd(jsonSerde<NestedDataClass.Output>(), NestedDataClass.Output("123")),
                END_MESSAGE),
        testInvocation({ ObjectGreeterImplementedFromInterface() }, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation({ Empty() }, "emptyInput")
            .withInput(startMessage(1), inputCmd(), callCompletion(2, "Till"))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyInput")),
                outputCmd("Till"),
                END_MESSAGE)
            .named("empty output"),
        testInvocation({ Empty() }, "emptyOutput")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyOutput"), "Francesco"),
                outputCmd(),
                END_MESSAGE)
            .named("empty output"),
        testInvocation({ Empty() }, "emptyInputOutput")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyInputOutput")),
                outputCmd(),
                END_MESSAGE)
            .named("empty input and empty output"),
        testInvocation({ PrimitiveTypes() }, "primitiveOutput")
            .withInput(startMessage(1), inputCmd(), callCompletion(2, TestSerdes.INT, 10))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1, 2, Target.service("PrimitiveTypes", "primitiveOutput"), Serde.VOID, null),
                outputCmd(TestSerdes.INT, 10),
                END_MESSAGE)
            .named("primitive output"),
        testInvocation({ PrimitiveTypes() }, "primitiveInput")
            .withInput(startMessage(1), inputCmd(10), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1, 2, Target.service("PrimitiveTypes", "primitiveInput"), TestSerdes.INT, 10),
                outputCmd(),
                END_MESSAGE)
            .named("primitive input"),
        testInvocation({ RawInputOutput() }, "rawInput")
            .withInput(
                startMessage(1),
                inputCmd("{{".toByteArray()),
                callCompletion(2, KotlinSerializationSerdeFactory.UNIT, Unit))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("RawInputOutput", "rawInput"), "{{".toByteArray()),
                outputCmd(),
                END_MESSAGE),
        testInvocation({ RawInputOutput() }, "rawInputWithCustomCt")
            .withInput(
                startMessage(1),
                inputCmd("{{".toByteArray()),
                callCompletion(2, KotlinSerializationSerdeFactory.UNIT, Unit))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawInputWithCustomCt"),
                    "{{".toByteArray()),
                outputCmd(),
                END_MESSAGE),
        testInvocation({ RawInputOutput() }, "rawOutput")
            .withInput(
                startMessage(1), inputCmd(), callCompletion(2, Serde.RAW, "{{".toByteArray()))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawOutput"),
                    KotlinSerializationSerdeFactory.UNIT,
                    Unit),
                outputCmd("{{".toByteArray()),
                END_MESSAGE),
        testInvocation({ RawInputOutput() }, "rawOutputWithCustomCT")
            .withInput(
                startMessage(1), inputCmd(), callCompletion(2, Serde.RAW, "{{".toByteArray()))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawOutputWithCustomCT"),
                    KotlinSerializationSerdeFactory.UNIT,
                    Unit),
                outputCmd("{{".toByteArray()),
                END_MESSAGE),
        testInvocation({ CornerCases() }, "returnNull")
            .withInput(
                startMessage(1, "mykey"),
                inputCmd(jsonSerde<String?>(), null),
                callCompletion(2, jsonSerde<String?>(), null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.virtualObject("CodegenTestCornerCases", "mykey", "returnNull"),
                    jsonSerde<String?>(),
                    null),
                outputCmd(jsonSerde<String?>(), null),
                END_MESSAGE),
        testInvocation({ CornerCases() }, "badReturnTypeInferred")
            .withInput(startMessage(1, "mykey"), inputCmd())
            .onlyBidiStream()
            .expectingOutput(
                oneWayCallCmd(
                    1,
                    Target.virtualObject(
                        "CodegenTestCornerCases", "mykey", "badReturnTypeInferred"),
                    null,
                    null,
                    Slice.EMPTY),
                outputCmd(),
                END_MESSAGE),
        testInvocation({ CustomSerdeService() }, "echo")
            .withInput(startMessage(1), inputCmd(byteArrayOf(1)))
            .onlyBidiStream()
            .expectingOutput(outputCmd(byteArrayOf(1)), END_MESSAGE),
    )
  }
}
