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
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.core.EagerStateTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.sdk.types.StateKey;
import dev.restate.serde.Serde;

public class EagerStateTest extends EagerStateTestSuite {

  @Override
  protected TestInvocationBuilder getEmpty() {
    return testDefinitionForVirtualObject(
        "GetEmpty",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, unused) ->
            String.valueOf(ctx.get(StateKey.of("STATE", TestSerdes.STRING)).isEmpty()));
  }

  @Override
  protected TestInvocationBuilder get() {
    return testDefinitionForVirtualObject(
        "GetEmpty",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, unused) -> ctx.get(StateKey.of("STATE", TestSerdes.STRING)).get());
  }

  @Override
  protected TestInvocationBuilder getAppendAndGet() {
    return testDefinitionForVirtualObject(
        "GetAppendAndGet",
        TestSerdes.STRING,
        TestSerdes.STRING,
        (ctx, input) -> {
          String oldState = ctx.get(StateKey.of("STATE", TestSerdes.STRING)).get();
          ctx.set(StateKey.of("STATE", TestSerdes.STRING), oldState + input);

          return ctx.get(StateKey.of("STATE", TestSerdes.STRING)).get();
        });
  }

  @Override
  protected TestInvocationBuilder getClearAndGet() {
    return testDefinitionForVirtualObject(
        "GetClearAndGet",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, input) -> {
          String oldState = ctx.get(StateKey.of("STATE", TestSerdes.STRING)).get();

          ctx.clear(StateKey.of("STATE", TestSerdes.STRING));
          assertThat(ctx.get(StateKey.of("STATE", TestSerdes.STRING))).isEmpty();
          return oldState;
        });
  }

  @Override
  protected TestInvocationBuilder getClearAllAndGet() {
    return testDefinitionForVirtualObject(
        "GetClearAllAndGet",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, input) -> {
          String oldState = ctx.get(StateKey.of("STATE", TestSerdes.STRING)).get();

          ctx.clearAll();
          assertThat(ctx.get(StateKey.of("STATE", TestSerdes.STRING))).isEmpty();
          assertThat(ctx.get(StateKey.of("ANOTHER_STATE", TestSerdes.STRING))).isEmpty();

          return oldState;
        });
  }

  @Override
  protected TestInvocationBuilder listKeys() {
    return testDefinitionForVirtualObject(
        "ListKeys",
        Serde.VOID,
        TestSerdes.STRING,
        (ctx, input) -> String.join(",", ctx.stateKeys()));
  }

  @Override
  protected TestInvocationBuilder consecutiveGetWithEmpty() {
    return testDefinitionForVirtualObject(
        "ConsecutiveGetWithEmpty",
        Serde.VOID,
        Serde.VOID,
        (ctx, input) -> {
          assertThat(ctx.get(StateKey.of("key-0", TestSerdes.STRING))).isEmpty();
          assertThat(ctx.get(StateKey.of("key-0", TestSerdes.STRING))).isEmpty();
          return null;
        });
  }
}
