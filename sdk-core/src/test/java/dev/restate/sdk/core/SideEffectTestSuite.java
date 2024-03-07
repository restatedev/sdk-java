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
import static dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.restate.generated.sdk.java.Java;
import dev.restate.sdk.common.CoreSerdes;
import java.util.stream.Stream;

public abstract class SideEffectTestSuite implements TestDefinitions.TestSuite {

  protected abstract TestInvocationBuilder sideEffect(String sideEffectOutput);

  protected abstract TestInvocationBuilder consecutiveSideEffect(String sideEffectOutput);

  protected abstract TestInvocationBuilder checkContextSwitching();

  protected abstract TestInvocationBuilder sideEffectGuard();

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        this.sideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                suspensionMessage(1))
            .named("Without optimization suspends"),
        this.sideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"), ackMessage(1))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                outputMessage("Hello Francesco"),
                END_MESSAGE)
            .named("Without optimization and with acks returns"),
        this.consecutiveSideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"))
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                suspensionMessage(1))
            .named("With optimization and without ack on first side effect will suspend"),
        this.consecutiveSideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"), ackMessage(1))
            .onlyUnbuffered()
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("FRANCESCO")),
                suspensionMessage(2))
            .named("With optimization and ack on first side effect will suspend"),
        this.consecutiveSideEffect("Francesco")
            .withInput(startMessage(1), inputMessage("Till"), ackMessage(1), ackMessage(2))
            .onlyUnbuffered()
            .expectingOutput(
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("Francesco")),
                Java.SideEffectEntryMessage.newBuilder()
                    .setValue(CoreSerdes.JSON_STRING.serializeToByteString("FRANCESCO")),
                outputMessage("Hello FRANCESCO"),
                END_MESSAGE)
            .named("With optimization and ack on first and second side effect will resume"),

        // --- Other tests
        this.checkContextSwitching()
            .withInput(startMessage(1), inputMessage(), ackMessage(1))
            .onlyUnbuffered()
            .assertingOutput(
                actualOutputMessages -> {
                  assertThat(actualOutputMessages).hasSize(3);
                  assertThat(actualOutputMessages)
                      .element(0)
                      .asInstanceOf(type(Java.SideEffectEntryMessage.class))
                      .returns(true, Java.SideEffectEntryMessage::hasValue);
                  assertThat(actualOutputMessages).element(1).isEqualTo(outputMessage("Hello"));
                  assertThat(actualOutputMessages).element(2).isEqualTo(END_MESSAGE);
                }),
        this.sideEffectGuard()
            .withInput(startMessage(1), inputMessage("Till"))
            .assertingOutput(
                containsOnlyExactErrorMessage(ProtocolException.invalidSideEffectCall())));
  }
}
