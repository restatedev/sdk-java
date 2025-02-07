// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.statemachine.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.TestDefinition;

import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import java.util.stream.Stream;

public abstract class OnlyInputAndOutputTestSuite implements TestSuite {

  protected abstract TestInvocationBuilder noSyscallsGreeter();

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        this.noSyscallsGreeter()
            .withInput(startMessage(1), inputMessage("Francesco"))
            .expectingOutput(outputMessage("Hello Francesco"), END_MESSAGE));
  }
}
