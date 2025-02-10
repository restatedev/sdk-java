// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.JavaBlockingTests.testDefinitionForService;

import dev.restate.sdk.core.RandomTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.serde.Serde;
import java.util.Random;

public class RandomTest extends RandomTestSuite {

  @Override
  protected TestInvocationBuilder randomShouldBeDeterministic() {
    return testDefinitionForService(
        "RandomShouldBeDeterministic",
        Serde.VOID,
        JsonSerdes.INT,
        (ctx, unused) -> ctx.random().nextInt());
  }

  @Override
  protected int getExpectedInt(long seed) {
    return new Random(seed).nextInt();
  }
}
