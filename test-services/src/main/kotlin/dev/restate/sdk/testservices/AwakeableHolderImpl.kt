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
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.AwakeableHolder

class AwakeableHolderImpl : AwakeableHolder {
  companion object {
    private val ID_KEY: StateKey<String> = stateKey<String>("id")
  }

  override suspend fun hold(id: String) {
    state().set(ID_KEY, id)
  }

  override suspend fun hasAwakeable(): Boolean {
    return state().get(ID_KEY) != null
  }

  override suspend fun unlock(payload: String) {
    val awakeableId = state().get(ID_KEY) ?: throw TerminalException("No awakeable registered")
    awakeableHandle(awakeableId).resolve(payload)
  }
}
