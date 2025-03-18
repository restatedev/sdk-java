// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi;

import static dev.restate.sdk.core.javaapi.JavaAPITests.testDefinitionForService;

import dev.restate.sdk.DurableFuture;
import dev.restate.sdk.core.SleepTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.serde.Serde;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SleepTest extends SleepTestSuite {

  @Override
  protected TestInvocationBuilder sleepGreeter() {
    return testDefinitionForService(
        "SleepGreeter",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, unused) -> {
          ctx.sleep(Duration.ofSeconds(1));
          return "Hello";
        });
  }

  @Override
  protected TestInvocationBuilder manySleeps() {
    return testDefinitionForService(
        "ManySleeps",
        Serde.VOID,
        Serde.VOID,
        (ctx, unused) -> {
          List<DurableFuture<?>> collectedDurableFutures = new ArrayList<>();

          for (int i = 0; i < 10; i++) {
            collectedDurableFutures.add(ctx.timer(Duration.ofSeconds(1)));
          }

          DurableFuture.all(
                  collectedDurableFutures.get(0),
                  collectedDurableFutures.get(1),
                  collectedDurableFutures
                      .subList(2, collectedDurableFutures.size())
                      .toArray(DurableFuture[]::new))
              .await();

          return null;
        });
  }
}
