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

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.core.EagerStateTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;

public class EagerStateTest extends EagerStateTestSuite {

  protected TestInvocationBuilder getEmpty() {
    return testDefinitionForVirtualObject(
        "GetEmpty",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) ->
            String.valueOf(ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).isEmpty()));
  }

  protected TestInvocationBuilder get() {
    return testDefinitionForVirtualObject(
        "GetEmpty",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) -> ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get());
  }

  protected TestInvocationBuilder getAppendAndGet() {
    return testDefinitionForVirtualObject(
        "GetAppendAndGet",
        CoreSerdes.JSON_STRING,
        CoreSerdes.JSON_STRING,
        (ctx, input) -> {
          String oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get();
          ctx.set(StateKey.of("STATE", CoreSerdes.JSON_STRING), oldState + input);

          return ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get();
        });
  }

  protected TestInvocationBuilder getClearAndGet() {
    return testDefinitionForVirtualObject(
        "GetClearAndGet",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, input) -> {
          String oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get();

          ctx.clear(StateKey.of("STATE", CoreSerdes.JSON_STRING));
          assertThat(ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))).isEmpty();
          return oldState;
        });
  }

  protected TestInvocationBuilder getClearAllAndGet() {
    return testDefinitionForVirtualObject(
        "GetClearAllAndGet",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, input) -> {
          String oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)).get();

          ctx.clearAll();
          assertThat(ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))).isEmpty();
          assertThat(ctx.get(StateKey.of("ANOTHER_STATE", CoreSerdes.JSON_STRING))).isEmpty();

          return oldState;
        });
  }

  protected TestInvocationBuilder listKeys() {
    return testDefinitionForVirtualObject(
        "ListKeys",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, input) -> String.join(",", ctx.stateKeys()));
  }
}
