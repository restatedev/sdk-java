// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi.reflections;

import static dev.restate.sdk.core.TestDefinitions.testInvocation;
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;

import dev.restate.common.Target;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.sdk.core.javaapi.MySerdeFactory;
import dev.restate.serde.Serde;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

public class ReflectionTest implements TestSuite {

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        testInvocation(ServiceGreeter::new, "greet")
            .withInput(startMessage(1), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeter::new, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeter::new, "sharedGreet")
            .withInput(startMessage(1, "slinkydeveloper"), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation(ObjectGreeterImplementedFromInterface::new, "greet")
            .withInput(startMessage(1, "slinkydeveloper"), inputCmd("Francesco"))
            .onlyBidiStream()
            .expectingOutput(outputCmd("Francesco"), END_MESSAGE),
        testInvocation(Empty::new, "emptyInput")
            .withInput(startMessage(1), inputCmd(), callCompletion(2, "Till"))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyInput")),
                outputCmd("Till"),
                END_MESSAGE)
            .named("empty output"),
        testInvocation(Empty::new, "emptyOutput")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyOutput"), "Francesco"),
                outputCmd(),
                END_MESSAGE)
            .named("empty output"),
        testInvocation(Empty::new, "emptyInputOutput")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("Empty", "emptyInputOutput")),
                outputCmd(),
                END_MESSAGE)
            .named("empty input and empty output"),
        testInvocation(PrimitiveTypes::new, "primitiveOutput")
            .withInput(startMessage(1), inputCmd(), callCompletion(2, TestSerdes.INT, 10))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1, 2, Target.service("PrimitiveTypes", "primitiveOutput"), Serde.VOID, null),
                outputCmd(TestSerdes.INT, 10),
                END_MESSAGE)
            .named("primitive output"),
        testInvocation(PrimitiveTypes::new, "primitiveInput")
            .withInput(startMessage(1), inputCmd(10), callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1, 2, Target.service("PrimitiveTypes", "primitiveInput"), TestSerdes.INT, 10),
                outputCmd(),
                END_MESSAGE)
            .named("primitive input"),
        testInvocation(RawInputOutput::new, "rawInput")
            .withInput(
                startMessage(1),
                inputCmd("{{".getBytes(StandardCharsets.UTF_8)),
                callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawInput"),
                    "{{".getBytes(StandardCharsets.UTF_8)),
                outputCmd(),
                END_MESSAGE),
        testInvocation(RawInputOutput::new, "rawInputWithCustomCt")
            .withInput(
                startMessage(1),
                inputCmd("{{".getBytes(StandardCharsets.UTF_8)),
                callCompletion(2, Serde.VOID, null))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawInputWithCustomCt"),
                    "{{".getBytes(StandardCharsets.UTF_8)),
                outputCmd(),
                END_MESSAGE),
        testInvocation(RawInputOutput::new, "rawOutput")
            .withInput(
                startMessage(1),
                inputCmd(),
                callCompletion(2, Serde.RAW, "{{".getBytes(StandardCharsets.UTF_8)))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("RawInputOutput", "rawOutput"), Serde.VOID, null),
                outputCmd("{{".getBytes(StandardCharsets.UTF_8)),
                END_MESSAGE),
        testInvocation(RawInputOutput::new, "rawOutputWithCustomCT")
            .withInput(
                startMessage(1),
                inputCmd(),
                callCompletion(2, Serde.RAW, "{{".getBytes(StandardCharsets.UTF_8)))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(
                    1,
                    2,
                    Target.service("RawInputOutput", "rawOutputWithCustomCT"),
                    Serde.VOID,
                    null),
                outputCmd("{{".getBytes(StandardCharsets.UTF_8)),
                END_MESSAGE),
        testInvocation(CustomSerde::new, "greet")
            .withInput(startMessage(1), inputCmd(MySerdeFactory.SERDE, "input"))
            .expectingOutput(outputCmd(MySerdeFactory.SERDE, "OUTPUT"), END_MESSAGE),
        testInvocation(ServiceWithInterface::new, "callInterface")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, "Hello Francesco"))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("MyGreeter", "greet"), "Francesco"),
                outputCmd("Hello Francesco"),
                END_MESSAGE),
        testInvocation(ServiceWithInterface::new, "callInterfaceHandle")
            .withInput(startMessage(1), inputCmd("Francesco"), callCompletion(2, "Hello Francesco"))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("MyGreeterHandle", "greet"), "Francesco"),
                outputCmd("Hello Francesco"),
                END_MESSAGE),
        testInvocation(RouterService::new, "route")
            .withInput(
                startMessage(1), inputCmd("ServiceA"), callCompletion(2, "Hello from A, world"))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("ServiceA", "greet"), "world"),
                outputCmd("Hello from A, world"),
                END_MESSAGE),
        testInvocation(RouterService::new, "route")
            .withInput(
                startMessage(1), inputCmd("ServiceB"), callCompletion(2, "Hello from B, world"))
            .onlyBidiStream()
            .expectingOutput(
                callCmd(1, 2, Target.service("ServiceB", "greet"), "world"),
                outputCmd("Hello from B, world"),
                END_MESSAGE));
  }
}
