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

import dev.restate.sdk.common.Serde;
import dev.restate.sdk.core.SleepTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SleepTest extends SleepTestSuite {

  protected TestInvocationBuilder sleepGreeter() {
    return testDefinitionForService(
        "SleepGreeter",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          ctx.sleep(Duration.ofSeconds(1));
          return "Hello";
        });
  }

  protected TestInvocationBuilder manySleeps() {
    return testDefinitionForService(
        "ManySleeps",
        Serde.VOID,
        Serde.VOID,
        (ctx, unused) -> {
          List<Awaitable<?>> collectedAwaitables = new ArrayList<>();

          for (int i = 0; i < 10; i++) {
            collectedAwaitables.add(ctx.timer(Duration.ofSeconds(1)));
          }

          Awaitable.all(
                  collectedAwaitables.get(0),
                  collectedAwaitables.get(1),
                  collectedAwaitables
                      .subList(2, collectedAwaitables.size())
                      .toArray(Awaitable[]::new))
              .await();

          return null;
        });
  }
}
