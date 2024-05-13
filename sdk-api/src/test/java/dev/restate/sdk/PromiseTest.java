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

import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.DurablePromiseKey;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.PromiseTestSuite;
import dev.restate.sdk.core.TestDefinitions;

public class PromiseTest extends PromiseTestSuite {
  @Override
  protected TestDefinitions.TestInvocationBuilder awaitPromise(String promiseKey) {
    return testDefinitionForWorkflow(
        "AwaitPromise",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (context, unused) ->
            context.durablePromise(DurablePromiseKey.string(promiseKey)).awaitable().await());
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitPeekPromise(
      String promiseKey, String emptyCaseReturnValue) {
    return testDefinitionForWorkflow(
        "PeekPromise",
        CoreSerdes.VOID,
        CoreSerdes.JSON_STRING,
        (context, unused) ->
            context
                .durablePromise(DurablePromiseKey.string(promiseKey))
                .peek()
                .orElse(emptyCaseReturnValue));
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitIsPromiseCompleted(String promiseKey) {
    return testDefinitionForWorkflow(
        "IsCompletedPromise",
        CoreSerdes.VOID,
        CoreSerdes.JSON_BOOLEAN,
        (context, unused) ->
            context.durablePromise(DurablePromiseKey.string(promiseKey)).isCompleted());
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitResolvePromise(
      String promiseKey, String completionValue) {
    return testDefinitionForWorkflow(
        "ResolvePromise",
        CoreSerdes.VOID,
        CoreSerdes.JSON_BOOLEAN,
        (context, unused) -> {
          try {
            context
                .durablePromiseHandle(DurablePromiseKey.string(promiseKey))
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
        CoreSerdes.VOID,
        CoreSerdes.JSON_BOOLEAN,
        (context, unused) -> {
          try {
            context.durablePromiseHandle(DurablePromiseKey.string(promiseKey)).reject(rejectReason);
            return true;
          } catch (TerminalException e) {
            return false;
          }
        });
  }
}
