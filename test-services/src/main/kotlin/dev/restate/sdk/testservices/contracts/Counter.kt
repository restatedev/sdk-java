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

@Serializable data class CounterUpdateResponse(val oldValue: Long, val newValue: Long)

@VirtualObject
@Name( "Counter")
interface Counter {
  /** Add value to counter */
  @Handler suspend fun add(context: ObjectContext, value: Long): CounterUpdateResponse

  /** Add value to counter, then fail with a Terminal error */
  @Handler suspend fun addThenFail(context: ObjectContext, value: Long)

  /** Get count */
  @Shared suspend fun get(context: SharedObjectContext): Long

  /** Reset count */
  @Handler suspend fun reset(context: ObjectContext)
}
