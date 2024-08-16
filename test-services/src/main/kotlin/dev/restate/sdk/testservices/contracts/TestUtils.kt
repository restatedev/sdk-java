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
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.kotlin.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateAwakeableAndAwaitItRequest(
    val awakeableKey: String,
    // If not null, then await it with orTimeout()
    val awaitTimeout: Long? = null
)

@Serializable sealed interface CreateAwakeableAndAwaitItResponse
// This is serialized as `{"type": "timeout"}`
@Serializable
@SerialName("timeout")
data object TimeoutResponse : CreateAwakeableAndAwaitItResponse
// This is serialized as `{"type": "result", "value": <VALUE>}`
@Serializable
@SerialName("result")
data class AwakeableResultResponse(val value: String) : CreateAwakeableAndAwaitItResponse

@Serializable data class InterpretRequest(val listName: String, val commands: List<Command>)

@Serializable sealed interface Command
// This is serialized as `{"type": "createAwakeableAndAwaitIt", ...}`
@Serializable
@SerialName("createAwakeableAndAwaitIt")
data class CreateAwakeableAndAwaitIt(val awakeableKey: String) : Command

// This is serialized as `{"type": "getEnvVariable", ...}`
// Reading an environment variable should be done within a side effect!
@Serializable
@SerialName("getEnvVariable")
data class GetEnvVariable(val envName: String) : Command

/** Collection of various utilities/corner cases scenarios used by tests */
@Service(name = "TestUtilsService")
interface TestUtilsService {
  /** Just echo */
  @Handler suspend fun echo(context: Context, input: String): String

  /** Just echo but with uppercase */
  @Handler suspend fun uppercaseEcho(context: Context, input: String): String

  /** Echo ingress headers */
  @Handler suspend fun echoHeaders(context: Context): Map<String, String>

  /** Create an awakeable, register it to AwakeableHolder#hold, then await it. */
  @Handler
  suspend fun createAwakeableAndAwaitIt(
      ctx: Context,
      req: CreateAwakeableAndAwaitItRequest
  ): CreateAwakeableAndAwaitItResponse

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

  /** Read an environment variable */
  @Handler suspend fun getEnvVariable(context: Context, env: String): String

  /**
   * This handler should iterate through the list of commands and execute them.
   *
   * For each command, the output should be appended to the given list name.
   */
  @Handler suspend fun interpretCommands(context: Context, req: InterpretRequest)
}
