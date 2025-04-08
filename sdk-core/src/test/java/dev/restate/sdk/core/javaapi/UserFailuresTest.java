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

import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.UserFailuresTestSuite;
import dev.restate.serde.Serde;
import java.util.concurrent.atomic.AtomicInteger;

public class UserFailuresTest extends UserFailuresTestSuite {

  @Override
  protected TestInvocationBuilder throwIllegalStateException() {
    return testDefinitionForService(
        "ThrowIllegalStateException",
        Serde.VOID,
        Serde.VOID,
        (ctx, unused) -> {
          throw new IllegalStateException("Whatever");
        });
  }

  @Override
  protected TestInvocationBuilder sideEffectThrowIllegalStateException(
      AtomicInteger nonTerminalExceptionsSeen) {
    return testDefinitionForService(
        "SideEffectThrowIllegalStateException",
        Serde.VOID,
        Serde.VOID,
        (ctx, unused) -> {
          try {
            ctx.run(
                () -> {
                  throw new IllegalStateException("Whatever");
                });
          } catch (Throwable e) {
            // A user should never catch Throwable!!!
            if (AbortedExecutionException.INSTANCE.equals(e)) {
              AbortedExecutionException.sneakyThrow();
            }
            if (!(e instanceof TerminalException)) {
              nonTerminalExceptionsSeen.addAndGet(1);
            }
            throw e;
          }

          throw new IllegalStateException("Unexpected end");
        });
  }

  @Override
  protected TestInvocationBuilder throwTerminalException(int code, String message) {
    return testDefinitionForService(
        "ThrowTerminalException",
        Serde.VOID,
        Serde.VOID,
        (ctx, unused) -> {
          throw new TerminalException(code, message);
        });
  }

  @Override
  protected TestInvocationBuilder sideEffectThrowTerminalException(int code, String message) {
    return testDefinitionForService(
        "SideEffectThrowTerminalException",
        Serde.VOID,
        Serde.VOID,
        (ctx, unused) -> {
          ctx.run(
              () -> {
                throw new TerminalException(code, message);
              });
          throw new IllegalStateException("This should not be reached");
        });
  }
}
