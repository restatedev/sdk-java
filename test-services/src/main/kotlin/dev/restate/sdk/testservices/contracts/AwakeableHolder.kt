// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdktesting.contracts

import dev.restate.sdk.annotation.Exclusive
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.kotlin.ObjectContext

// This is a generic utility service that can be used in various situations where
// we need to synchronize the services with the test runner using an awakeable.
@VirtualObject(name = "AwakeableHolder")
interface AwakeableHolder {
  @Exclusive suspend fun hold(context: ObjectContext, id: String)

  @Exclusive suspend fun hasAwakeable(context: ObjectContext): Boolean

  @Exclusive suspend fun unlock(context: ObjectContext, payload: String)
}
