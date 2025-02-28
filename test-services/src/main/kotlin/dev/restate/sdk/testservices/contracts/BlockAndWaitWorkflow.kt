// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices.contracts

import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.Workflow
import dev.restate.sdk.kotlin.SharedWorkflowContext
import dev.restate.sdk.kotlin.WorkflowContext

@Workflow(name = "BlockAndWaitWorkflow")
interface BlockAndWaitWorkflow {
  @Workflow suspend fun run(context: WorkflowContext, input: String): String

  @Shared suspend fun unblock(context: SharedWorkflowContext, output: String)

  @Shared suspend fun getState(context: SharedWorkflowContext): String?
}
