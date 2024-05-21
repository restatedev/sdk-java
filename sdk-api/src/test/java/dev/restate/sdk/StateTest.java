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

import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.core.StateTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.serde.jackson.JsonSerdes;

public class StateTest extends StateTestSuite {

  protected TestInvocationBuilder getState() {
    return testDefinitionForVirtualObject(
        "GetState",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          String state = ctx.get(StateKey.of("STATE", JsonSerdes.STRING)).orElse("Unknown");

          return "Hello " + state;
        });
  }

  protected TestInvocationBuilder getAndSetState() {
    return testDefinitionForVirtualObject(
        "GetState",
        JsonSerdes.STRING,
        JsonSerdes.STRING,
        (ctx, input) -> {
          String state = ctx.get(StateKey.of("STATE", JsonSerdes.STRING)).get();

          ctx.set(StateKey.of("STATE", JsonSerdes.STRING), input);

          return "Hello " + state;
        });
  }

  protected TestInvocationBuilder setNullState() {
    return testDefinitionForVirtualObject(
        "GetState",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          ctx.set(
              StateKey.of(
                  "STATE",
                  Serde.<String>using(
                      l -> {
                        throw new IllegalStateException("Unexpected call to serde fn");
                      },
                      l -> {
                        throw new IllegalStateException("Unexpected call to serde fn");
                      })),
              null);

          throw new IllegalStateException("set did not fail");
        });
  }
}
