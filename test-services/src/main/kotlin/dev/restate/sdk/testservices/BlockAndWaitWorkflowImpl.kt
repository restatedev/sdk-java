// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.kotlin.KtSerdes
import dev.restate.sdk.kotlin.KtStateKey
import dev.restate.sdk.kotlin.SharedWorkflowContext
import dev.restate.sdk.kotlin.WorkflowContext
import dev.restate.sdk.testservices.contracts.BlockAndWaitWorkflow
import dev.restate.sdk.types.DurablePromiseKey
import dev.restate.sdk.types.StateKey
import dev.restate.sdk.types.TerminalException

class BlockAndWaitWorkflowImpl : BlockAndWaitWorkflow {
  companion object {
    private val MY_DURABLE_PROMISE: DurablePromiseKey<String> =
        DurablePromiseKey.of("durable-promise", KtSerdes.json())
    private val MY_STATE: StateKey<String> = KtStateKey.json("my-state")
  }

  override suspend fun run(context: WorkflowContext, input: String): String {
    context.set(MY_STATE, input)

    // Wait on unblock
    val output: String = context.promise(MY_DURABLE_PROMISE).awaitable().await()

    if (!context.promise(MY_DURABLE_PROMISE).peek().isReady) {
      throw TerminalException("Durable promise should be completed")
    }

    return output
  }

  override suspend fun unblock(context: SharedWorkflowContext, output: String) {
    context.promiseHandle(MY_DURABLE_PROMISE).resolve(output)
  }

  override suspend fun getState(context: SharedWorkflowContext): String? {
    return context.get(MY_STATE)
  }
}
