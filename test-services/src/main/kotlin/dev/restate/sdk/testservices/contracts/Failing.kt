// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices.contracts

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.kotlin.ObjectContext

@VirtualObject
interface Failing {
  @Handler suspend fun terminallyFailingCall(context: ObjectContext, errorMessage: String)

  @Handler
  suspend fun callTerminallyFailingCall(context: ObjectContext, errorMessage: String): String

  @Handler suspend fun failingCallWithEventualSuccess(context: ObjectContext): Int

  @Handler suspend fun failingSideEffectWithEventualSuccess(context: ObjectContext): Int

  @Handler suspend fun terminallyFailingSideEffect(context: ObjectContext, errorMessage: String)
}
