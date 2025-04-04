// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.AssertUtils.*;
import static dev.restate.sdk.core.statemachine.ProtoUtils.*;

import dev.restate.sdk.core.TestDefinitions.TestDefinition;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.statemachine.ProtoUtils;
import java.util.stream.Stream;

public abstract class RandomTestSuite implements TestSuite {

  protected abstract TestInvocationBuilder randomShouldBeDeterministic();

  protected abstract int getExpectedInt(long seed);

  @Override
  public Stream<TestDefinition> definitions() {
    String debugId = "my-id";

    return Stream.of(
        this.randomShouldBeDeterministic()
            .withInput(startMessage(1).setDebugId(debugId), ProtoUtils.inputCmd())
            .expectingOutput(
                outputCmd(getExpectedInt(ProtoUtils.invocationIdToRandomSeed(debugId))),
                END_MESSAGE));
  }
}
