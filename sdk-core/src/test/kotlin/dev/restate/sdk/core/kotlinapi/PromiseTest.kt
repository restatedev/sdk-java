// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.core.PromiseTestSuite
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForWorkflow
import dev.restate.sdk.kotlin.*

class PromiseTest : PromiseTestSuite() {
  override fun awaitPromise(promiseKey: String): TestDefinitions.TestInvocationBuilder =
      testDefinitionForWorkflow("AwaitPromise") { ctx, _: Unit ->
        ctx.promise(durablePromiseKey<String>(promiseKey)).future().await()
      }

  override fun awaitPeekPromise(
      promiseKey: String,
      emptyCaseReturnValue: String,
  ): TestDefinitions.TestInvocationBuilder =
      testDefinitionForWorkflow("AwaitPeekPromise") { ctx, _: Unit ->
        ctx.promise(durablePromiseKey<String>(promiseKey)).peek().orElse(emptyCaseReturnValue)
      }

  override fun awaitIsPromiseCompleted(promiseKey: String): TestDefinitions.TestInvocationBuilder =
      testDefinitionForWorkflow("IsCompletedPromise") { ctx, _: Unit ->
        ctx.promise(durablePromiseKey<String>(promiseKey)).peek().isReady
      }

  override fun awaitResolvePromise(
      promiseKey: String,
      completionValue: String,
  ): TestDefinitions.TestInvocationBuilder =
      testDefinitionForWorkflow("ResolvePromise") { ctx, _: Unit ->
        try {
          ctx.promiseHandle(durablePromiseKey<String>(promiseKey)).resolve(completionValue)
          return@testDefinitionForWorkflow true
        } catch (e: TerminalException) {
          return@testDefinitionForWorkflow false
        }
      }

  override fun awaitRejectPromise(
      promiseKey: String,
      rejectReason: String,
  ): TestDefinitions.TestInvocationBuilder =
      testDefinitionForWorkflow("RejectPromise") { ctx, _: Unit ->
        try {
          ctx.promiseHandle(durablePromiseKey<String>(promiseKey)).reject(rejectReason)
          return@testDefinitionForWorkflow true
        } catch (e: TerminalException) {
          return@testDefinitionForWorkflow false
        }
      }
}
