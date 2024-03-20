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
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Empty;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import java.util.stream.Stream;

public abstract class StateTestSuite implements TestDefinitions.TestSuite {

  protected abstract TestInvocationBuilder getState();

  protected abstract TestInvocationBuilder getAndSetState();

  protected abstract TestInvocationBuilder setNullState();

  @Override
  public Stream<TestDefinitions.TestDefinition> definitions() {
    return Stream.of(
        this.getState()
            .withInput(startMessage(2), inputMessage("Till"), getStateMessage("STATE", "Francesco"))
            .expectingOutput(outputMessage("Hello Francesco"), END_MESSAGE)
            .named("With GetStateEntry already completed"),
        this.getState()
            .withInput(
                startMessage(2),
                inputMessage("Till"),
                getStateMessage("STATE").setEmpty(Empty.getDefaultInstance()))
            .expectingOutput(outputMessage("Hello Unknown"), END_MESSAGE)
            .named("With GetStateEntry already completed empty"),
        this.getState()
            .withInput(startMessage(1), inputMessage("Till"))
            .expectingOutput(getStateMessage("STATE"), suspensionMessage(1))
            .named("Without GetStateEntry"),
        this.getState()
            .withInput(startMessage(2), inputMessage("Till"), getStateMessage("STATE"))
            .expectingOutput(suspensionMessage(1))
            .named("With GetStateEntry not completed"),
        this.getState()
            .withInput(
                startMessage(2),
                inputMessage("Till"),
                getStateMessage("STATE"),
                completionMessage(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(outputMessage("Hello Francesco"), END_MESSAGE)
            .named("With GetStateEntry and completed with later CompletionFrame"),
        this.getState()
            .withInput(startMessage(1), inputMessage("Till"), completionMessage(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(
                getStateMessage("STATE"), outputMessage("Hello Francesco"), END_MESSAGE)
            .named("Without GetStateEntry and completed with later CompletionFrame"),
        this.getState()
            .withInput(
                startMessage(2),
                inputMessage("Till"),
                getStateMessage("STATE", new TerminalException(409)))
            .expectingOutput(outputMessage(new TerminalException(409)), END_MESSAGE)
            .named("Failed GetStateEntry"),
        this.getState()
            .withInput(
                startMessage(1),
                inputMessage("Till"),
                completionMessage(1, new TerminalException(409)))
            .assertingOutput(
                messageLites -> {
                  assertThat(messageLites)
                      .element(0)
                      .isInstanceOf(Protocol.GetStateEntryMessage.class);
                  assertThat(messageLites)
                      .element(1)
                      .isEqualTo(outputMessage(new TerminalException(409)));
                  assertThat(messageLites).element(2).isEqualTo(END_MESSAGE);
                })
            .named("Failing GetStateEntry"),
        this.getAndSetState()
            .withInput(
                startMessage(3),
                inputMessage("Till"),
                getStateMessage("STATE", "Francesco"),
                setStateMessage("STATE", "Till"))
            .expectingOutput(outputMessage("Hello Francesco"), END_MESSAGE)
            .named("With GetState and SetState"),
        this.getAndSetState()
            .withInput(startMessage(2), inputMessage("Till"), getStateMessage("STATE", "Francesco"))
            .expectingOutput(
                setStateMessage("STATE", "Till"), outputMessage("Hello Francesco"), END_MESSAGE)
            .named("With GetState already completed"),
        this.getAndSetState()
            .withInput(startMessage(1), inputMessage("Till"), completionMessage(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(
                getStateMessage("STATE"),
                setStateMessage("STATE", "Till"),
                outputMessage("Hello Francesco"),
                END_MESSAGE)
            .named("With GetState completed later"),
        this.setNullState()
            .withInput(startMessage(1), inputMessage("Till"))
            .assertingOutput(containsOnlyExactErrorMessage(new NullPointerException())));
  }
}
