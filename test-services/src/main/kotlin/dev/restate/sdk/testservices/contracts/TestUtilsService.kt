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

/** Collection of various utilities/corner cases scenarios used by tests */
@Service
@Name("TestUtilsService")
interface TestUtilsService {
  /** Just echo */
  @Handler suspend fun echo(input: String): String

  /** Just echo but with uppercase */
  @Handler suspend fun uppercaseEcho(input: String): String

  /** Echo ingress headers */
  @Handler suspend fun echoHeaders(): Map<String, String>

  /** Just echo */
  @Handler @Raw suspend fun rawEcho(@Raw input: ByteArray): ByteArray

  /**
   * Invoke `ctx.run` incrementing a local variable counter (not a restate state key!).
   *
   * Returns the count value.
   *
   * This is used to verify acks will suspend when using the always suspend test-suite
   */
  @Handler suspend fun countExecutedSideEffects(increments: Int): Int

  /** Cancel invocation using the context. */
  @Handler suspend fun cancelInvocation(invocationId: String)

  @Serializable
  data class ResolveSignalRequest(
      val invocationId: String,
      val signalName: String,
      val value: String,
  )

  /** Resolve a named signal on the target invocation with a string value. */
  @Handler suspend fun resolveSignal(req: ResolveSignalRequest)

  @Serializable
  data class RejectSignalRequest(
      val invocationId: String,
      val signalName: String,
      val reason: String,
  )

  /** Reject a named signal on the target invocation. */
  @Handler suspend fun rejectSignal(req: RejectSignalRequest)
}
