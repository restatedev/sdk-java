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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@VirtualObject
  @Name( "VirtualObjectCommandInterpreter")
interface VirtualObjectCommandInterpreter {

  @Serializable sealed interface AwaitableCommand

  // This is serialized as `{"type": "createAwakeable", ...}`
  @Serializable
  @SerialName("createAwakeable")
  data class CreateAwakeable(val awakeableKey: String) : AwaitableCommand

  // This is serialized as `{"type": "sleep", ...}`
  @Serializable @SerialName("sleep") data class Sleep(val timeoutMillis: Long) : AwaitableCommand

  // This is serialized as `{"type": "runThrowTerminalException", ...}`
  @Serializable
  @SerialName("runThrowTerminalException")
  data class RunThrowTerminalException(val reason: String) : AwaitableCommand

  @Serializable sealed interface Command

  // Returns the index of the one that completed first successfully
  @Serializable
  @SerialName("awaitAnySuccessful")
  data class AwaitAnySuccessful(val commands: List<AwaitableCommand>) : Command

  // Returns the index of the one that completed first
  @Serializable
  @SerialName("awaitAny")
  data class AwaitAny(val commands: List<AwaitableCommand>) : Command

  // Returns the result
  @Serializable
  @SerialName("awaitOne")
  data class AwaitOne(val command: AwaitableCommand) : Command

  // This is serialized as `{"type": "awaitAwakeableOrTimeout", ...}`
  // The timeout throws a terminal error with "await-timeout" string in it
  @Serializable
  @SerialName("awaitAwakeableOrTimeout")
  data class AwaitAwakeableOrTimeout(val awakeableKey: String, val timeoutMillis: Long) : Command

  @Serializable data class InterpretRequest(val commands: List<Command>)

  @Serializable
  @SerialName("resolveAwakeable")
  data class ResolveAwakeable(val awakeableKey: String, val value: String) : Command

  @Serializable
  @SerialName("rejectAwakeable")
  data class RejectAwakeable(val awakeableKey: String, val reason: String) : Command

  // This is serialized as `{"type": "getEnvVariable", ...}`
  // Reading an environment variable should be done within a side effect!
  @Serializable
  @SerialName("getEnvVariable")
  data class GetEnvVariable(val envName: String) : Command

  /**
   * This handler should iterate through the list of commands and execute them.
   *
   * For each command, the output should be appended to the given list name. Returns the result of
   * the last command, or empty string otherwise.
   */
  @Handler suspend fun interpretCommands(context: ObjectContext, req: InterpretRequest): String

  @Shared
  suspend fun resolveAwakeable(context: SharedObjectContext, resolveAwakeable: ResolveAwakeable)

  @Shared
  suspend fun rejectAwakeable(context: SharedObjectContext, rejectAwakeable: RejectAwakeable)

  @Shared suspend fun hasAwakeable(context: SharedObjectContext, awakeableKey: String): Boolean

  @Shared suspend fun getResults(context: SharedObjectContext): List<String>
}
