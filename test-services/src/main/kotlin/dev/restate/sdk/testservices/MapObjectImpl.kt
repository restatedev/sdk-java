// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.Entry
import dev.restate.sdk.testservices.contracts.MapObject

class MapObjectImpl : MapObject {
  override suspend fun set(context: ObjectContext, entry: Entry) {
    context.set(stateKey(entry.key), entry.value)
  }

  override suspend fun get(context: ObjectContext, key: String): String {
    return context.get(stateKey(key)) ?: ""
  }

  override suspend fun clearAll(context: ObjectContext): List<Entry> {
    val keys = context.stateKeys()
    // AH AH AH and here I wanna see if you really respect determinism!!!
    val result = mutableListOf<Entry>()
    for (k in keys) {
      result.add(Entry(k, context.get(stateKey<String>(k))!!))
    }
    context.clearAll()
    return result
  }
}
