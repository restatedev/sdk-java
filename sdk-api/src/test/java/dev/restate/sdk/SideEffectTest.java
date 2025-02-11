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

import dev.restate.sdk.core.SideEffectTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.serde.Serde;
import java.util.Objects;

public class SideEffectTest extends SideEffectTestSuite {

  @Override
  protected TestInvocationBuilder sideEffect(String sideEffectOutput) {
    return testDefinitionForService(
        "SideEffect",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          String result = ctx.run(JsonSerdes.STRING, () -> sideEffectOutput);
          return "Hello " + result;
        });
  }

  @Override
  protected TestInvocationBuilder namedSideEffect(String name, String sideEffectOutput) {
    return testDefinitionForService(
        "NamedSideEffect",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          String result = ctx.run(name, JsonSerdes.STRING, () -> sideEffectOutput);
          return "Hello " + result;
        });
  }

  @Override
  protected TestInvocationBuilder consecutiveSideEffect(String sideEffectOutput) {
    return testDefinitionForService(
        "ConsecutiveSideEffect",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          String firstResult = ctx.run(JsonSerdes.STRING, () -> sideEffectOutput);
          String secondResult = ctx.run(JsonSerdes.STRING, firstResult::toUpperCase);

          return "Hello " + secondResult;
        });
  }

  @Override
  protected TestInvocationBuilder checkContextSwitching() {
    return testDefinitionForService(
        "CheckContextSwitching",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          String currentThread = Thread.currentThread().getName();

          String sideEffectThread =
              ctx.run(JsonSerdes.STRING, () -> Thread.currentThread().getName());

          if (!Objects.equals(currentThread, sideEffectThread)) {
            throw new IllegalStateException(
                "Current thread and side effect thread do not match: "
                    + currentThread
                    + " != "
                    + sideEffectThread);
          }

          return "Hello";
        });
  }

  @Override
  protected TestInvocationBuilder failingSideEffect(String name, String reason) {
    return testDefinitionForService(
        "FailingSideEffect",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          ctx.run(
              name,
              () -> {
                throw new IllegalStateException(reason);
              });
          return null;
        });
  }

  @Override
  protected TestInvocationBuilder failingSideEffectWithRetryPolicy(
      String reason, RetryPolicy retryPolicy) {
    return testDefinitionForService(
        "FailingSideEffectWithRetryPolicy",
        Serde.VOID,
        JsonSerdes.STRING,
        (ctx, unused) -> {
          ctx.run(
              retryPolicy,
              () -> {
                throw new IllegalStateException(reason);
              });
          return null;
        });
  }
}
