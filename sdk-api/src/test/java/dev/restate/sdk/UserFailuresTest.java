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

import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.TestDefinitions.TestInvocationBuilder;
import dev.restate.sdk.core.UserFailuresTestSuite;
import java.util.concurrent.atomic.AtomicInteger;

public class UserFailuresTest extends UserFailuresTestSuite {

  protected TestInvocationBuilder throwIllegalStateException() {
    return testDefinitionForService(
        "ThrowIllegalStateException",
        CoreSerdes.VOID,
        CoreSerdes.VOID,
        (ctx, unused) -> {
          throw new IllegalStateException("Whatever");
        });
  }

  protected TestInvocationBuilder sideEffectThrowIllegalStateException(
      AtomicInteger nonTerminalExceptionsSeen) {
    return testDefinitionForService(
        "SideEffectThrowIllegalStateException",
        CoreSerdes.VOID,
        CoreSerdes.VOID,
        (ctx, unused) -> {
          try {
            ctx.sideEffect(
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

  protected TestInvocationBuilder throwTerminalException(
      TerminalException.Code code, String message) {
    return testDefinitionForService(
        "ThrowTerminalException",
        CoreSerdes.VOID,
        CoreSerdes.VOID,
        (ctx, unused) -> {
          throw new TerminalException(code, message);
        });
  }

  protected TestInvocationBuilder sideEffectThrowTerminalException(
      TerminalException.Code code, String message) {
    return testDefinitionForService(
        "SideEffectThrowTerminalException",
        CoreSerdes.VOID,
        CoreSerdes.VOID,
        (ctx, unused) -> {
          ctx.sideEffect(
              () -> {
                throw new TerminalException(code, message);
              });
          throw new IllegalStateException("This should not be reached");
        });
  }
}
