// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi;

import static dev.restate.sdk.core.javaapi.JavaAPITests.*;

import dev.restate.sdk.core.PromiseTestSuite;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.sdk.types.DurablePromiseKey;
import dev.restate.sdk.types.TerminalException;
import dev.restate.serde.Serde;

public class PromiseTest extends PromiseTestSuite {
  @Override
  protected TestDefinitions.TestInvocationBuilder awaitPromise(String promiseKey) {
    return testDefinitionForWorkflow(
        "AwaitPromise",
        Serde.VOID,
        TestSerdes.STRING,
        (context, unused) ->
            context.promise(DurablePromiseKey.of(promiseKey, String.class)).future().await());
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitPeekPromise(
      String promiseKey, String emptyCaseReturnValue) {
    return testDefinitionForWorkflow(
        "PeekPromise",
        Serde.VOID,
        TestSerdes.STRING,
        (context, unused) ->
            context
                .promise(DurablePromiseKey.of(promiseKey, String.class))
                .peek()
                .orElse(emptyCaseReturnValue));
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitIsPromiseCompleted(String promiseKey) {
    return testDefinitionForWorkflow(
        "IsCompletedPromise",
        Serde.VOID,
        TestSerdes.BOOLEAN,
        (context, unused) ->
            context.promise(DurablePromiseKey.of(promiseKey, String.class)).peek().isReady());
  }

  @Override
  protected TestDefinitions.TestInvocationBuilder awaitResolvePromise(
      String promiseKey, String completionValue) {
    return testDefinitionForWorkflow(
        "ResolvePromise",
        Serde.VOID,
        TestSerdes.BOOLEAN,
        (context, unused) -> {
          try {
            context
                .promiseHandle(DurablePromiseKey.of(promiseKey, String.class))
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
        TestSerdes.BOOLEAN,
        (context, unused) -> {
          try {
            context
                .promiseHandle(DurablePromiseKey.of(promiseKey, String.class))
                .reject(rejectReason);
            return true;
          } catch (TerminalException e) {
            return false;
          }
        });
  }
}
