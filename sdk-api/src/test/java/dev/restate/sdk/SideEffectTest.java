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
import static dev.restate.sdk.core.ProtoUtils.GREETER_SERVICE_TARGET;

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.core.SideEffectTestSuite;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import java.util.Objects;

public class SideEffectTest extends SideEffectTestSuite {

  protected TestInvocationBuilder sideEffect(String sideEffectOutput) {
    return testDefinitionForService(
        "SideEffect",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) -> {
          String result = ctx.run(CoreSerdes.JSON_STRING, () -> sideEffectOutput);
          return "Hello " + result;
        });
  }

  protected TestInvocationBuilder consecutiveSideEffect(String sideEffectOutput) {
    return testDefinitionForService(
        "ConsecutiveSideEffect",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) -> {
          String firstResult = ctx.run(CoreSerdes.JSON_STRING, () -> sideEffectOutput);
          String secondResult = ctx.run(CoreSerdes.JSON_STRING, firstResult::toUpperCase);

          return "Hello " + secondResult;
        });
  }

  protected TestInvocationBuilder checkContextSwitching() {
    return testDefinitionForService(
        "CheckContextSwitching",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) -> {
          String currentThread = Thread.currentThread().getName();

          String sideEffectThread =
              ctx.run(CoreSerdes.JSON_STRING, () -> Thread.currentThread().getName());

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

  protected TestInvocationBuilder sideEffectGuard() {
    return testDefinitionForService(
        "SideEffectGuard",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (ctx, unused) -> {
          ctx.run(() -> ctx.send(GREETER_SERVICE_TARGET, new byte[] {}));
          throw new IllegalStateException("This point should not be reached");
        });
  }
}
