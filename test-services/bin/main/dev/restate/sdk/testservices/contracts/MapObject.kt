// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices.contracts

import dev.restate.sdk.annotation.*
import dev.restate.sdk.kotlin.*
import kotlinx.serialization.Serializable

@VirtualObject
@Name("MapObject")
interface MapObject {

  @Serializable data class Entry(val key: String, val value: String)

  /**
   * Set value in map.
   *
   * The individual entries should be stored as separate Restate state keys, and not in a single
   * state key
   */
  @Handler suspend fun set(entry: Entry)

  /** Get value from map. */
  @Handler suspend fun get(key: String): String

  /** Clear all entries */
  @Handler suspend fun clearAll(): List<Entry>
}
