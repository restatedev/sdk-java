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

import dev.restate.sdk.core.StateTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.sdk.types.StateKey;
import dev.restate.serde.Serde;

public class StateTest extends StateTestSuite {

  @Override
  protected TestInvocationBuilder getState() {
    return testDefinitionForVirtualObject(
        "GetState",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, unused) -> {
          String state = ctx.get(StateKey.of("STATE", String.class)).orElse("Unknown");

          return "Hello " + state;
        });
  }

  @Override
  protected TestInvocationBuilder getAndSetState() {
    return testDefinitionForVirtualObject(
        "GetState",
        TestSerdes.STRING,
        TestSerdes.STRING,
        (ctx, input) -> {
          String state = ctx.get(StateKey.of("STATE", String.class)).get();

          ctx.set(StateKey.of("STATE", String.class), input);

          return "Hello " + state;
        });
  }

  @Override
  protected TestInvocationBuilder setNullState() {
    return testDefinitionForVirtualObject(
        "GetState",
        Serde.VOID,
        TestSerdes.STRING,
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
