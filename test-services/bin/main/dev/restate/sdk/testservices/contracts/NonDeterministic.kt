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
@Name("NonDeterministic")
interface NonDeterministic {
  /** On first invocation sleeps, on second invocation calls */
  @Handler suspend fun eitherSleepOrCall()

  @Handler suspend fun callDifferentMethod()

  @Handler suspend fun backgroundInvokeWithDifferentTargets()

  @Handler suspend fun setDifferentKey()
}
