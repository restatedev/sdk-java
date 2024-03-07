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

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.core.RandomTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import java.util.Random;

public class RandomTest extends RandomTestSuite {

  protected TestInvocationBuilder randomShouldBeDeterministic() {
    return testDefinitionForService(
        "RandomShouldBeDeterministic",
        CoreSerdes.VOID,
        CoreSerdes.JSON_INT,
        (ctx, unused) -> ctx.random().nextInt());
  }

  protected TestInvocationBuilder randomInsideSideEffect() {
    return testDefinitionForService(
        "RandomInsideSideEffect",
        CoreSerdes.VOID,
        CoreSerdes.JSON_INT,
        (ctx, unused) -> {
          ctx.sideEffect(() -> ctx.random().nextInt());
          throw new IllegalStateException("This should not unreachable");
        });
  }

  protected int getExpectedInt(long seed) {
    return new Random(seed).nextInt();
  }
}
