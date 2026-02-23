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
@Name("Counter")
interface Counter {
  @Serializable data class CounterUpdateResponse(val oldValue: Long, val newValue: Long)

  /** Add value to counter */
  @Handler suspend fun add(value: Long): CounterUpdateResponse

  /** Add value to counter, then fail with a Terminal error */
  @Handler suspend fun addThenFail(value: Long)

  /** Get count */
  @Shared suspend fun get(): Long

  /** Reset count */
  @Handler suspend fun reset()
}
