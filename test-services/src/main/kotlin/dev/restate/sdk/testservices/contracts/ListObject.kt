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

@VirtualObject
@Name( "ListObject")
interface ListObject {
  /** Append a value to the list object */
  @Handler suspend fun append(context: ObjectContext, value: String)

  /** Get current list */
  @Handler suspend fun get(context: ObjectContext): List<String>

  /** Clear list */
  @Handler suspend fun clear(context: ObjectContext): List<String>
}
