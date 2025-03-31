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

/** Collection of various utilities/corner cases scenarios used by tests */
@Service
@Name( "TestUtilsService")
interface TestUtilsService {
  /** Just echo */
  @Handler suspend fun echo(context: Context, input: String): String

  /** Just echo but with uppercase */
  @Handler suspend fun uppercaseEcho(context: Context, input: String): String

  /** Echo ingress headers */
  @Handler suspend fun echoHeaders(context: Context): Map<String, String>

  /** Just echo */
  @Handler @Raw suspend fun rawEcho(context: Context, @Raw input: ByteArray): ByteArray

  /** Create timers and await them all. Durations in milliseconds */
  @Handler suspend fun sleepConcurrently(context: Context, millisDuration: List<Long>)

  /**
   * Invoke `ctx.run` incrementing a local variable counter (not a restate state key!).
   *
   * Returns the count value.
   *
   * This is used to verify acks will suspend when using the always suspend test-suite
   */
  @Handler suspend fun countExecutedSideEffects(context: Context, increments: Int): Int

  /** Cancel invocation using the context. */
  @Handler suspend fun cancelInvocation(context: Context, invocationId: String)
}
