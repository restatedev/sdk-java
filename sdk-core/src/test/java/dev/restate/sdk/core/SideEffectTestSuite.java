// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.AssertUtils.containsOnlyExactErrorMessage;
import static dev.restate.sdk.core.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.restate.generated.sdk.java.Java;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import dev.restate.sdk.core.testservices.GreetingRequest;
import io.grpc.BindableService;
import java.util.stream.Stream;

public abstract class SideEffectTestSuite implements TestDefinitions.TestSuite {

  protected abstract BindableService sideEffect(String sideEffectOutput);

  protected abstract BindableService consecutiveSideEffect(String sideEffectOutput);

  protected abstract BindableService checkContextSwitching();

  protected abstract BindableService sideEffectGuard();

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        testInvocation(() -> this.sideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(greetingRequest("Till")))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                suspensionMessage(1))
            .named("Without optimization suspends"),
        testInvocation(() -> this.sideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(greetingRequest("Till")), ackMessage(1))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                outputMessage(greetingResponse("Hello Francesco")),
                END_MESSAGE)
            .named("Without optimization and with acks returns"),
        testInvocation(() -> this.consecutiveSideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(greetingRequest("Till")))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                suspensionMessage(1))
            .named("With optimization and without ack on first side effect will suspend"),
        testInvocation(() -> this.consecutiveSideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(greetingRequest("Till")), ackMessage(1))
            .onlyUnbuffered()
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("FRANCESCO")),
                suspensionMessage(2))
            .named("With optimization and ack on first side effect will suspend"),
        testInvocation(() -> this.consecutiveSideEffect("Francesco"), GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1),
                inputMessage(greetingRequest("Till")),
                ackMessage(1),
                ackMessage(2))
            .onlyUnbuffered()
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("FRANCESCO")),
                outputMessage(greetingResponse("Hello FRANCESCO")),
                END_MESSAGE)
            .named("With optimization and ack on first and second side effect will resume"),

        // --- Other tests
        testInvocation(this::checkContextSwitching, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1), inputMessage(GreetingRequest.getDefaultInstance()), ackMessage(1))
            .onlyUnbuffered()
            .assertingOutput(
                actualOutputMessages -> {
                  assertThat(actualOutputMessages).hasSize(3);
                  assertThat(actualOutputMessages)
                      .element(0)
                      .asInstanceOf(type(Java.SideEffectEntryMessage.class))
                      .returns(true, Java.SideEffectEntryMessage::hasValue);
                  assertThat(actualOutputMessages)
                      .element(1)
                      .isEqualTo(outputMessage(greetingResponse("Hello")));
                  assertThat(actualOutputMessages).element(2).isEqualTo(END_MESSAGE);
                }),
        testInvocation(this::sideEffectGuard, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(greetingRequest("Till")))
            .assertingOutput(
                containsOnlyExactErrorMessage(ProtocolException.invalidSideEffectCall())));
  }
}
