// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.JavaBlockingTests.*;

import dev.restate.sdk.common.DurablePromiseKey;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.PromiseTestSuite;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestSerdes;

public class PromiseTest extends PromiseTestSuite {
  @Override
  protected TestDefinitions.TestInvocationBuilder awaitPromise(String promiseKey) {
    return testDefinitionForWorkflow(
        "AwaitPromise",
        Serde.VOID,
        JsonSerdes.STRING,
        (context, unused) ->
            context
                .durablePromise(DurablePromiseKey.of(promiseKey, TestSerdes.STRING))
                .awaitable()
                .await());
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitPeekPromise(
      String promiseKey, String emptyCaseReturnValue) {
    return testDefinitionForWorkflow(
        "PeekPromise",
        Serde.VOID,
        JsonSerdes.STRING,
        (context, unused) ->
            context
                .durablePromise(DurablePromiseKey.of(promiseKey, TestSerdes.STRING))
                .peek()
                .orElse(emptyCaseReturnValue));
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitIsPromiseCompleted(String promiseKey) {
    return testDefinitionForWorkflow(
        "IsCompletedPromise",
        Serde.VOID,
        JsonSerdes.BOOLEAN,
        (context, unused) ->
            context
                .durablePromise(DurablePromiseKey.of(promiseKey, TestSerdes.STRING))
                .isCompleted());
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitResolvePromise(
      String promiseKey, String completionValue) {
    return testDefinitionForWorkflow(
        "ResolvePromise",
        Serde.VOID,
        JsonSerdes.BOOLEAN,
        (context, unused) -> {
          try {
            context
                .durablePromiseHandle(DurablePromiseKey.of(promiseKey, TestSerdes.STRING))
                .resolve(completionValue);
            return true;
          } catch (TerminalException e) {
            return false;
          }
        });
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitRejectPromise(
      String promiseKey, String rejectReason) {
    return testDefinitionForWorkflow(
        "RejectPromise",
        Serde.VOID,
        JsonSerdes.BOOLEAN,
        (context, unused) -> {
          try {
            context
                .durablePromiseHandle(DurablePromiseKey.of(promiseKey, TestSerdes.STRING))
                .reject(rejectReason);
            return true;
          } catch (TerminalException e) {
            return false;
          }
        });
  }
}
