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
import dev.restate.sdk.testservices.contracts.MapObject

class MapObjectImpl : MapObject {
  override suspend fun set(entry: MapObject.Entry) {
    state().set(stateKey(entry.key), entry.value)
  }

  override suspend fun get(key: String): String {
    return state().get(stateKey(key)) ?: ""
  }

  override suspend fun clearAll(): List<MapObject.Entry> {
    val keys = state().keys()
    // AH AH AH and here I wanna see if you really respect determinism!!!
    val result = mutableListOf<MapObject.Entry>()
    for (k in keys) {
      result.add(MapObject.Entry(k, state().get(stateKey<String>(k))!!))
    }
    state().clearAll()
    return result
  }
}
