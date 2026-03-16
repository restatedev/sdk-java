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
import kotlinx.serialization.Serializable

@VirtualObject
@Name("Failing")
interface Failing {
  @Serializable
  data class FailureToPropagate(val errorMessage: String, val metadata: Map<String, String>? = null)

  @Handler suspend fun terminallyFailingCall(failureToPropagate: FailureToPropagate)

  @Handler suspend fun callTerminallyFailingCall(failureToPropagate: FailureToPropagate): String

  @Handler suspend fun failingCallWithEventualSuccess(): Int

  @Handler suspend fun terminallyFailingSideEffect(failureToPropagate: FailureToPropagate)

  /**
   * `minimumAttempts` should be used to check when to succeed. The retry policy should be
   * configured to be infinite.
   *
   * @return the number of executed attempts. In order to implement this count, an atomic counter in
   *   the service should be used.
   */
  @Handler
  suspend fun sideEffectSucceedsAfterGivenAttempts(
      minimumAttempts: Int,
  ): Int

  /**
   * `retryPolicyMaxRetryCount` should be used to configure the retry policy.
   *
   * @return the number of executed attempts. In order to implement this count, an atomic counter in
   *   the service should be used.
   */
  @Handler
  suspend fun sideEffectFailsAfterGivenAttempts(
      retryPolicyMaxRetryCount: Int,
  ): Int
}
