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
@Name("Failing")
interface Failing {
  @Handler suspend fun terminallyFailingCall(context: ObjectContext, errorMessage: String)

  @Handler
  suspend fun callTerminallyFailingCall(context: ObjectContext, errorMessage: String): String

  @Handler suspend fun failingCallWithEventualSuccess(context: ObjectContext): Int

  @Handler suspend fun terminallyFailingSideEffect(context: ObjectContext, errorMessage: String)

  /**
   * `minimumAttempts` should be used to check when to succeed. The retry policy should be
   * configured to be infinite.
   *
   * @return the number of executed attempts. In order to implement this count, an atomic counter in
   *   the service should be used.
   */
  @Handler
  suspend fun sideEffectSucceedsAfterGivenAttempts(
      context: ObjectContext,
      minimumAttempts: Int
  ): Int

  /**
   * `retryPolicyMaxRetryCount` should be used to configure the retry policy.
   *
   * @return the number of executed attempts. In order to implement this count, an atomic counter in
   *   the service should be used.
   */
  @Handler
  suspend fun sideEffectFailsAfterGivenAttempts(
      context: ObjectContext,
      retryPolicyMaxRetryCount: Int
  ): Int
}
