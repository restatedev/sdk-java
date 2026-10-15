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
import dev.restate.sdk.core.AssertUtils.containsOnly
import dev.restate.sdk.core.AssertUtils.errorMessage
import dev.restate.sdk.core.MockBidiStream
import dev.restate.sdk.core.MockRequestResponse
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.TestDefinitions.TestExecutor
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder
import dev.restate.sdk.core.TestDefinitions.TestSuite
import dev.restate.sdk.core.TestRunner
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.core.javaapi.PayloadCodecTest.ENC
import dev.restate.sdk.core.javaapi.PayloadCodecTest.FailingEncodeCodec
import dev.restate.sdk.core.javaapi.PayloadCodecTest.isNativeExecutor
import dev.restate.sdk.core.javaapi.PayloadCodecTest.withCodec
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.callGreeterGreetService
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForService
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForVirtualObject
import dev.restate.sdk.core.legacy.ProtoUtils.*
import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.endpoint.definition.HandlerType
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.endpoint.definition.ServiceType
import dev.restate.sdk.kotlin.*
import dev.restate.serde.kotlinx.KotlinSerializationSerdeFactory
import java.util.stream.Stream
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.condition.EnabledIf

/** Payload codecs are supported only by the native state machine. */
@EnabledIf("dev.restate.sdk.core.javaapi.PayloadCodecTest#isNativeStateMachineAvailable")
class PayloadCodecTest : TestRunner() {

  override fun executors(): Stream<TestExecutor> =
      Stream.concat(MockRequestResponse.getExecutors(), MockBidiStream.getExecutors()).filter {
        isNativeExecutor(it)
      }

  override fun definitions(): Stream<TestSuite> = Stream.of(Suite())

  class Suite : TestSuite {
    private val state = StateKey.of("STATE", TestSerdes.STRING)

    /** Like [testDefinitionForVirtualObject], but running the handler on [Dispatchers.Default]. */
    private fun virtualObjectOnDefaultDispatcher(
        runner: suspend (ObjectContext, String) -> String
    ): TestInvocationBuilder =
        TestDefinitions.testInvocation(
            ServiceDefinition.of(
                "CodecService",
                ServiceType.VIRTUAL_OBJECT,
                listOf(
                    HandlerDefinition.of(
                        "run",
                        HandlerType.EXCLUSIVE,
                        TestSerdes.STRING,
                        TestSerdes.STRING,
                        HandlerRunner.of(
                            KotlinSerializationSerdeFactory(),
                            HandlerRunner.Options(Dispatchers.Default),
                            runner,
                        ),
                    )
                ),
            ),
            "run",
        )

    override fun definitions(): Stream<TestDefinition> =
        Stream.of(
            withCodec(
                testDefinitionForService("CodecService") { _, input: String -> "Hello $input" }
                    .withInput(startMessage(1), inputCmd(ENC, "Till"))
                    .expectingOutput(outputCmd(ENC, "Hello Till"), END_MESSAGE)
                    .named("Input and output")
            ),
            withCodec(
                testDefinitionForService("CodecService") { _, input: String -> "Hello $input" }
                    .withInput(startMessage(1), inputCmd("Till"))
                    .assertingOutput(
                        containsOnly(
                            errorMessage { assertThat(it.message).contains("Value is not encoded") }
                        )
                    )
                    .named("Input not encoded fails")
            ),
            withCodec(
                testDefinitionForVirtualObject("CodecService") { ctx, input: String ->
                      val current = ctx.get(state)!!
                      ctx.set(state, input)
                      "Hello $current"
                    }
                    .withInput(
                        startMessage(3, "my-key"),
                        inputCmd(ENC, "Till"),
                        getLazyStateCmd(1, "STATE"),
                        getLazyStateCompletion(1, ENC, "Francesco"),
                    )
                    .expectingOutput(
                        setStateCmd("STATE", ENC, "Till"),
                        outputCmd(ENC, "Hello Francesco"),
                        END_MESSAGE,
                    )
                    .named("Get and set state")
            ),
            withCodec(
                testDefinitionForService("CodecService") { ctx, _: Unit ->
                      callGreeterGreetService(ctx, "Francesco").await()
                    }
                    .withInput(
                        startMessage(1),
                        inputCmd(ENC, ""),
                        callCompletion(2, ENC, "Hello Francesco"),
                    )
                    .onlyBidiStream()
                    .expectingOutput(
                        callCmd(1, 2, GREETER_SERVICE_TARGET, ENC, "Francesco"),
                        outputCmd(ENC, "Hello Francesco"),
                        END_MESSAGE,
                    )
                    .named("Call")
            ),
            withCodec(
                testDefinitionForService("CodecService") { ctx, _: Unit ->
                      "Hello " + ctx.runBlock { "Francesco" }
                    }
                    .withInput(startMessage(1), inputCmd(ENC, ""))
                    .expectingOutput(
                        runCmd(1),
                        proposeRunCompletion(1, ENC, "Francesco"),
                        suspensionMessage(1),
                    )
                    .named("Run proposes encoded value")
            ),
            withCodec(
                testDefinitionForService("CodecService") { ctx, _: Unit ->
                      "Hello " + ctx.runBlock { "Francesco" }
                    }
                    .withInput(
                        startMessage(3),
                        inputCmd(ENC, ""),
                        runCmd(1),
                        runCompletion(1, ENC, "Francesco"),
                    )
                    .onlyBidiStream()
                    .expectingOutput(outputCmd(ENC, "Hello Francesco"), END_MESSAGE)
                    .named("Run replay decodes value")
            ),
            // Encoding failures are not run failures: no run completion is proposed, and the
            // invocation fails (retryable)
            withCodec(
                FailingEncodeCodec(),
                testDefinitionForService("CodecService") { ctx, _: Unit ->
                      "Hello " + ctx.runBlock { "Francesco" }
                    }
                    .withInput(startMessage(1), inputCmd(ENC, ""))
                    .assertingOutput { messages ->
                      assertThat(messages).hasSize(2)
                      assertThat(messages[0]).isEqualTo(runCmd(1))
                      errorMessage { assertThat(it.message).contains(FailingEncodeCodec.MESSAGE) }
                          .accept(messages[1])
                    }
                    .named("Run result encoding failure fails the invocation"),
            ),
            withCodec(
                testDefinitionForService("CodecService") { ctx, _: String ->
                      TestSerdes.STRING.deserialize(ctx.request().body)
                    }
                    .withInput(startMessage(1), inputCmd(ENC, "Till"))
                    .expectingOutput(outputCmd(ENC, "Till"), END_MESSAGE)
                    .named("Request body is decoded")
            ),
            withCodec(
                virtualObjectOnDefaultDispatcher { ctx, input ->
                      val current = ctx.get(state)!!
                      ctx.set(state, input)
                      "Hello $current"
                    }
                    .withInput(
                        startMessage(3, "my-key"),
                        inputCmd(ENC, "Till"),
                        getLazyStateCmd(1, "STATE"),
                        getLazyStateCompletion(1, ENC, "Francesco"),
                    )
                    .expectingOutput(
                        setStateCmd("STATE", ENC, "Till"),
                        outputCmd(ENC, "Hello Francesco"),
                        END_MESSAGE,
                    )
                    .named("Get and set state on Dispatchers.Default")
            ),
        )
  }
}
