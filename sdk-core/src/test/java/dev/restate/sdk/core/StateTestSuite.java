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
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;

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
            .withInput(
                startMessage(3),
                inputCmd("Till"),
                getLazyStateCmd(1, "STATE"),
                getLazyStateCompletion(1, "Francesco"))
            .expectingOutput(outputCmd("Hello Francesco"), END_MESSAGE)
            .named("With GetStateEntry already completed"),
        this.getState()
            .withInput(
                startMessage(3),
                inputCmd("Till"),
                getLazyStateCmd(1, "STATE"),
                getLazyStateCompletionEmpty(1))
            .expectingOutput(outputCmd("Hello Unknown"), END_MESSAGE)
            .named("With GetStateEntry already completed empty"),
        this.getState()
            .withInput(startMessage(1), inputCmd("Till"))
            .expectingOutput(getLazyStateCmd(1, "STATE"), suspensionMessage(1))
            .named("Without GetStateEntry"),
        this.getState()
            .withInput(startMessage(2), inputCmd("Till"), getLazyStateCmd(1, "STATE"))
            .expectingOutput(suspensionMessage(1))
            .named("With GetStateEntry not completed"),
        this.getState()
            .withInput(
                startMessage(2),
                inputCmd("Till"),
                getLazyStateCmd(1, "STATE"),
                getLazyStateCompletion(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(outputCmd("Hello Francesco"), END_MESSAGE)
            .named("With GetStateEntry and completed with later CompletionFrame"),
        this.getState()
            .withInput(startMessage(1), inputCmd("Till"), getLazyStateCompletion(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(getLazyStateCmd(1, "STATE"), outputCmd("Hello Francesco"), END_MESSAGE)
            .named("Without GetStateEntry and completed with later CompletionFrame"),
        this.getAndSetState()
            .withInput(
                startMessage(4),
                inputCmd("Till"),
                getLazyStateCmd(1, "STATE"),
                getLazyStateCompletion(1, "Francesco"),
                setStateCmd("STATE", "Till"))
            .expectingOutput(outputCmd("Hello Francesco"), END_MESSAGE)
            .named("With GetState and SetState"),
        this.getAndSetState()
            .withInput(
                startMessage(3),
                inputCmd("Till"),
                getLazyStateCmd(1, "STATE"),
                getLazyStateCompletion(1, "Francesco"))
            .expectingOutput(
                setStateCmd("STATE", "Till"), outputCmd("Hello Francesco"), END_MESSAGE)
            .named("With GetState already completed"),
        this.getAndSetState()
            .withInput(startMessage(1), inputCmd("Till"), getLazyStateCompletion(1, "Francesco"))
            .onlyUnbuffered()
            .expectingOutput(
                getLazyStateCmd(1, "STATE"),
                setStateCmd("STATE", "Till"),
                outputCmd("Hello Francesco"),
                END_MESSAGE)
            .named("With GetState completed later"),
        this.setNullState()
            .withInput(startMessage(1), inputCmd("Till"))
            .assertingOutput(containsOnlyExactErrorMessage(new NullPointerException())));
  }
}
