// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.JavaBlockingTests.testDefinitionForVirtualObject;

import dev.restate.sdk.common.*;
import dev.restate.sdk.core.StateMachineFailuresTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.serde.jackson.JsonSerdes;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class StateMachineFailuresTest extends StateMachineFailuresTestSuite {

  private static final StateKey<Integer> STATE =
      StateKey.of(
          "STATE",
          Serde.using(
              i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
              b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8))));

  protected TestInvocationBuilder getState(AtomicInteger nonTerminalExceptionsSeen) {
    return testDefinitionForVirtualObject(
        "GetState",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          try {
            ctx.get(STATE);
          } catch (Throwable e) {
            // A user should never catch Throwable!!!
            if (AbortedExecutionException.INSTANCE.equals(e)) {
              AbortedExecutionException.sneakyThrow();
            }
            if (!(e instanceof TerminalException)) {
              nonTerminalExceptionsSeen.addAndGet(1);
            } else {
              throw e;
            }
          }

          return "Francesco";
        });
  }

  protected TestInvocationBuilder sideEffectFailure(Serde<Integer> serde) {
    return testDefinitionForVirtualObject(
        "SideEffectFailure",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          ctx.run(serde, () -> 0);
          return "Francesco";
        });
  }
}
