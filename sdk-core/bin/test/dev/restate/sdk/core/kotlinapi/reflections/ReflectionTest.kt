// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi.reflections

import dev.restate.common.Slice
import dev.restate.common.Target
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.TestDefinitions.testInvocation
import dev.restate.sdk.core.TestSerdes
import dev.restate.sdk.core.statemachine.ProtoUtils.*
import dev.restate.serde.Serde
import dev.restate.serde.kotlinx.*
import java.util.stream.Stream

class ReflectionTest : TestDefinitions.TestSuite {

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
                inputCmd(jsonSerde<NestedDataClass.Input>(), NestedDataClass.Input("123")),
            )
            .onlyBidiStream()
            .expectingOutput(
                outputCmd(jsonSerde<NestedDataClass.Output>(), NestedDataClass.Output("123")),
                END_MESSAGE,
            ),
        testInvocation({ ObjectGreeterImplementedFromInterface() }, "greet")
            .withInput(
                startMessage(1, "slinkydeveloper"),
                inputCmd("Francesco"),
                callCompletion(2, "Francesco"),
            )
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.virtualObject("GreeterInterface", "slinkydeveloper", "greet"),
                    "Francesco",
                ),
                outputCmd("Francesco"),
                END_MESSAGE,
            ),
        testInvocation({ Empty() }, "emptyInput")
            .withInput(startMessage(1), inputCmd(), callCompletion(2, "Till"))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyInput")),
                outputCmd("Till"),
                END_MESSAGE,
            )
            .named("empty output"),
        testInvocation({ Empty() }, "emptyOutput")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyOutput"), "Francesco"),
                outputCmd(),
                END_MESSAGE,
            )
            .named("empty output"),
        testInvocation({ Empty() }, "emptyInputOutput")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyInputOutput")),
                outputCmd(),
                END_MESSAGE,
            )
            .named("empty input and empty output"),
        testInvocation({ PrimitiveTypes() }, "primitiveOutput")
            .withInput(startMessage(1), inputCmd(), callCompletion(2, TestSerdes.INT, 10))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("PrimitiveTypes", "primitiveOutput"),
                    Serde.VOID,
                    null,
                ),
                outputCmd(TestSerdes.INT, 10),
                END_MESSAGE,
            )
            .named("primitive output"),
        testInvocation({ PrimitiveTypes() }, "primitiveInput")
            .withInput(startMessage(1), inputCmd(10), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("PrimitiveTypes", "primitiveInput"),
                    TestSerdes.INT,
                    10,
                ),
                outputCmd(),
                END_MESSAGE,
            )
            .named("primitive input"),
        testInvocation({ RawInputOutput() }, "rawInput")
            .withInput(
                startMessage(1),
                inputCmd("{{".toByteArray()),
                callCompletion(2, KotlinSerializationSerdeFactory.UNIT, Unit),
            )
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("RawInputOutput", "rawInput"), "{{".toByteArray()),
                outputCmd(),
                END_MESSAGE,
            ),
        testInvocation({ RawInputOutput() }, "rawInputWithCustomCt")
            .withInput(
                startMessage(1),
                inputCmd("{{".toByteArray()),
                callCompletion(2, KotlinSerializationSerdeFactory.UNIT, Unit),
            )
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawInputWithCustomCt"),
                    "{{".toByteArray(),
                ),
                outputCmd(),
                END_MESSAGE,
            ),
        testInvocation({ RawInputOutput() }, "rawOutput")
            .withInput(
                startMessage(1),
                inputCmd(),
                callCompletion(2, Serde.RAW, "{{".toByteArray()),
            )
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawOutput"),
                    KotlinSerializationSerdeFactory.UNIT,
                    Unit,
                ),
                outputCmd("{{".toByteArray()),
                END_MESSAGE,
            ),
        testInvocation({ RawInputOutput() }, "rawOutputWithCustomCT")
            .withInput(
                startMessage(1),
                inputCmd(),
                callCompletion(2, Serde.RAW, "{{".toByteArray()),
            )
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawOutputWithCustomCT"),
                    KotlinSerializationSerdeFactory.UNIT,
                    Unit,
                ),
                outputCmd("{{".toByteArray()),
                END_MESSAGE,
            ),
        testInvocation({ CornerCases() }, "returnNull")
            .withInput(
                startMessage(1, "mykey"),
                inputCmd(jsonSerde<String?>(), null),
                callCompletion(2, jsonSerde<String?>(), null),
            )
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.virtualObject("CornerCases", "mykey", "returnNull"),
                    jsonSerde<String?>(),
                    null,
                ),
                outputCmd(jsonSerde<String?>(), null),
                END_MESSAGE,
            ),
        testInvocation({ CornerCases() }, "badReturnTypeInferred")
            .withInput(startMessage(1, "mykey"), inputCmd())
            .onlyBidiStream()
            .expectingOutput(
                oneWayCallCmd(
                    1,
                    Target.virtualObject(
                        "CornerCases",
                        "mykey",
                        "badReturnTypeInferred",
                    ),
                    null,
                    null,
                    Slice.EMPTY,
                ),
                outputCmd(),
                END_MESSAGE,
            ),
        testInvocation({ CornerCases() }, "callSuspendWithinProxy")
            .withInput(startMessage(1, "mykey"), inputCmd())
            .onlyBidiStream()
            .expectingOutput(
                oneWayCallCmd(
                    1,
                    Target.virtualObject(
                        "CornerCases",
                        "mykey",
                        "callSuspendWithinProxy",
                    ),
                    null,
                    null,
                    Slice.EMPTY,
                ),
                outputCmd(),
                END_MESSAGE,
            ),
        testInvocation({ CustomSerdeService() }, "echo")
            .withInput(startMessage(1), inputCmd(byteArrayOf(1)))
            .onlyBidiStream()
            .expectingOutput(outputCmd(byteArrayOf(1)), END_MESSAGE),
    )
  }
}
