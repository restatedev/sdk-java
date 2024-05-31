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

import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.core.EagerStateTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;

public class EagerStateTest extends EagerStateTestSuite {

  protected TestInvocationBuilder getEmpty() {
    return testDefinitionForVirtualObject(
        "GetEmpty",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) ->
            String.valueOf(ctx.get(StateKey.of("STATE", JsonSerdes.STRING)).isEmpty()));
  }

  protected TestInvocationBuilder get() {
    return testDefinitionForVirtualObject(
        "GetEmpty",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> ctx.get(StateKey.of("STATE", JsonSerdes.STRING)).get());
  }

  protected TestInvocationBuilder getAppendAndGet() {
    return testDefinitionForVirtualObject(
        "GetAppendAndGet",
        JsonSerdes.STRING,
        JsonSerdes.STRING,
        (ctx, input) -> {
          String oldState = ctx.get(StateKey.of("STATE", JsonSerdes.STRING)).get();
          ctx.set(StateKey.of("STATE", JsonSerdes.STRING), oldState + input);

          return ctx.get(StateKey.of("STATE", JsonSerdes.STRING)).get();
        });
  }

  protected TestInvocationBuilder getClearAndGet() {
    return testDefinitionForVirtualObject(
        "GetClearAndGet",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, input) -> {
          String oldState = ctx.get(StateKey.of("STATE", JsonSerdes.STRING)).get();

          ctx.clear(StateKey.of("STATE", JsonSerdes.STRING));
          assertThat(ctx.get(StateKey.of("STATE", JsonSerdes.STRING))).isEmpty();
          return oldState;
        });
  }

  protected TestInvocationBuilder getClearAllAndGet() {
    return testDefinitionForVirtualObject(
        "GetClearAllAndGet",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, input) -> {
          String oldState = ctx.get(StateKey.of("STATE", JsonSerdes.STRING)).get();

          ctx.clearAll();
          assertThat(ctx.get(StateKey.of("STATE", JsonSerdes.STRING))).isEmpty();
          assertThat(ctx.get(StateKey.of("ANOTHER_STATE", JsonSerdes.STRING))).isEmpty();

          return oldState;
        });
  }

  protected TestInvocationBuilder listKeys() {
    return testDefinitionForVirtualObject(
        "ListKeys",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, input) -> String.join(",", ctx.stateKeys()));
  }

  @Override
  protected TestInvocationBuilder consecutiveGetWithEmpty() {
    return testDefinitionForVirtualObject(
        "ConsecutiveGetWithEmpty",
        Serde.VOID,
        Serde.VOID,
        (ctx, input) -> {
          assertThat(ctx.get(StateKey.of("key-0", JsonSerdes.STRING))).isEmpty();
          assertThat(ctx.get(StateKey.of("key-0", JsonSerdes.STRING))).isEmpty();
          return null;
        });
  }
}
