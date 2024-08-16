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
import dev.restate.sdk.kotlin.KtStateKey
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.testservices.contracts.ListObject

class ListObjectImpl : ListObject {
  companion object {
    private val LIST_KEY: StateKey<List<String>> =
        KtStateKey.json<List<String>>(
            "list",
        )
  }

  override suspend fun append(context: ObjectContext, value: String) {
    val list = context.get(LIST_KEY) ?: emptyList()
    context.set(LIST_KEY, list + value)
  }

  override suspend fun get(context: ObjectContext): List<String> {
    return context.get(LIST_KEY) ?: emptyList()
  }

  override suspend fun clear(context: ObjectContext): List<String> {
    val result = context.get(LIST_KEY) ?: emptyList()
    context.clear(LIST_KEY)
    return result
  }
}
