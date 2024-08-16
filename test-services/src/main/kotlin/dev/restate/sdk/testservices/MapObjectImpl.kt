// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.kotlin.KtStateKey
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.testservices.contracts.Entry
import dev.restate.sdk.testservices.contracts.MapObject

class MapObjectImpl : MapObject {
  override suspend fun set(context: ObjectContext, entry: Entry) {
    context.set(KtStateKey.json(entry.key), entry.value)
  }

  override suspend fun get(context: ObjectContext, key: String): String {
    return context.get(KtStateKey.json(key)) ?: ""
  }

  override suspend fun clearAll(context: ObjectContext): List<Entry> {
    val keys = context.stateKeys()
    // AH AH AH and here I wanna see if you really respect determinism!!!
    val result = mutableListOf<Entry>()
    for (k in keys) {
      result.add(Entry(k, context.get(KtStateKey.json<String>(k))!!))
    }
    context.clearAll()
    return result
  }
}
