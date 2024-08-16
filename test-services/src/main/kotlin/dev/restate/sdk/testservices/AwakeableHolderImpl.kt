// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.kotlin.KtStateKey
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.resolve
import dev.restate.sdk.testservices.contracts.AwakeableHolder

class AwakeableHolderImpl : AwakeableHolder {
  companion object {
    private val ID_KEY: StateKey<String> = KtStateKey.json<String>("id")
  }

  override suspend fun hold(context: ObjectContext, id: String) {
    context.set(ID_KEY, id)
  }

  override suspend fun hasAwakeable(context: ObjectContext): Boolean {
    return context.get(ID_KEY) != null
  }

  override suspend fun unlock(context: ObjectContext, payload: String) {
    val awakeableId: String =
        context.get(ID_KEY) ?: throw TerminalException("No awakeable registered")
    context.awakeableHandle(awakeableId).resolve(payload)
  }
}
