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
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.ListObject

class ListObjectImpl : ListObject {
  companion object {
    private val LIST_KEY: StateKey<List<String>> =
        stateKey(
            "list",
        )
  }

  override suspend fun append(value: String) {
    val list = state().get(LIST_KEY) ?: emptyList()
    state().set(LIST_KEY, list + value)
  }

  override suspend fun get(): List<String> {
    return state().get(LIST_KEY) ?: emptyList()
  }

  override suspend fun clear(): List<String> {
    val result = state().get(LIST_KEY) ?: emptyList()
    state().clear(LIST_KEY)
    return result
  }
}
