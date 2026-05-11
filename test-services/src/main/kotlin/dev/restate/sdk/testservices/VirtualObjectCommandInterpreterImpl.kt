// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.common.TimeoutException
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.testservices.contracts.VirtualObjectCommandInterpreter
import kotlin.time.Duration.Companion.milliseconds
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class VirtualObjectCommandInterpreterImpl : VirtualObjectCommandInterpreter {

  companion object {
    private val LOG: Logger = LogManager.getLogger(VirtualObjectCommandInterpreterImpl::class.java)
  }

  override suspend fun interpretCommands(
      req: VirtualObjectCommandInterpreter.InterpretRequest,
  ): String {
    LOG.info("Interpreting commands {}", req)

    var result = ""

    req.commands.forEach {
      LOG.info("Start interpreting command {}", it)
      when (it) {
        is VirtualObjectCommandInterpreter.AwaitAny -> {
          val cmds = it.commands.map { it.toAwaitable() }
          result =
              select {
                    for (cmd in cmds) {
                      cmd.onAwait { it }
                    }
                  }
                  .await()
        }
        is VirtualObjectCommandInterpreter.AwaitAnySuccessful -> {
          val cmds = it.commands.map { it.toAwaitable() }.toMutableList()

          while (true) {
            @Suppress("UNCHECKED_CAST")
            val completed = DurableFuture.any(cmds as List<DurableFuture<*>>).await()

            try {
              result = cmds[completed].await()
              break
            } catch (_: TerminalException) {
              // Remove the cmd to make sure we don't fail on it again
              cmds.removeAt(completed)
            }
          }
        }
        is VirtualObjectCommandInterpreter.AwaitOne -> {
          result = it.command.toAwaitable().await()
        }
        is VirtualObjectCommandInterpreter.AwaitFirstCompleted -> {
          val cmds = it.commands.map { it.toAwaitable() }
          result =
              try {
                select { cmds.forEach { cmd -> cmd.onAwait { v -> v } } }.await()
              } catch (e: TerminalException) {
                throw e
              }
        }
        is VirtualObjectCommandInterpreter.AwaitFirstSucceededOrAllFailed -> {
          val cmds = it.commands.map { it.toAwaitable() }.toMutableList()
          var lastError: TerminalException? = null
          while (cmds.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            val completed = DurableFuture.any(cmds as List<DurableFuture<*>>).await()
            try {
              result = cmds[completed].await()
              lastError = null
              break
            } catch (e: TerminalException) {
              lastError = e
              cmds.removeAt(completed)
            }
          }
          if (lastError != null) {
            throw lastError
          }
        }
        is VirtualObjectCommandInterpreter.AwaitAllSucceededOrFirstFailed -> {
          val cmds = it.commands.map { it.toAwaitable() }
          // DurableFuture.all completes on first failure or when all succeed.
          @Suppress("UNCHECKED_CAST") DurableFuture.all(cmds as List<DurableFuture<*>>).await()
          result = cmds.map { c -> c.await() }.joinToString(separator = "|")
        }
        is VirtualObjectCommandInterpreter.AwaitAllCompleted -> {
          val cmds = it.commands.map { it.toAwaitable() }
          // Wait for all to settle (no fail-fast). Accomplish by individually awaiting each.
          val parts = mutableListOf<String>()
          for (cmd in cmds) {
            try {
              parts += "ok:${cmd.await()}"
            } catch (e: TerminalException) {
              parts += "err:${e.message ?: ""}"
            }
          }
          result = parts.joinToString(separator = "|")
        }
        is VirtualObjectCommandInterpreter.GetEnvVariable -> {
          result = runBlock { System.getenv(it.envName) ?: "" }
        }
        is VirtualObjectCommandInterpreter.ResolveAwakeable -> {
          resolveAwakeable(it)
          result = ""
        }
        is VirtualObjectCommandInterpreter.RejectAwakeable -> {
          rejectAwakeable(it)
          result = ""
        }
        is VirtualObjectCommandInterpreter.AwaitAwakeableOrTimeout -> {
          val awk = awakeable<String>()
          state().set("awk-${it.awakeableKey}", awk.id)
          try {
            result = awk.await(it.timeoutMillis.milliseconds)
          } catch (_: TimeoutException) {
            throw TerminalException("await-timeout")
          }
        }
      }
      LOG.info("Command result {}", result)
      appendResult(result)
    }

    return result
  }

  override suspend fun resolveAwakeable(
      resolveAwakeable: VirtualObjectCommandInterpreter.ResolveAwakeable,
  ) {
    awakeableHandle(
            state().get("awk-${resolveAwakeable.awakeableKey}")
                ?: throw TerminalException("awakeable is not registerd yet")
        )
        .resolve(resolveAwakeable.value)
  }

  override suspend fun rejectAwakeable(
      rejectAwakeable: VirtualObjectCommandInterpreter.RejectAwakeable,
  ) {
    awakeableHandle(
            state().get("awk-${rejectAwakeable.awakeableKey}")
                ?: throw TerminalException("awakeable is not registerd yet")
        )
        .reject(rejectAwakeable.reason)
  }

  override suspend fun hasAwakeable(awakeableKey: String): Boolean =
      !state().get<String>("awk-$awakeableKey").isNullOrBlank()

  override suspend fun getResults(): List<String> = state().get("results") ?: listOf()

  private suspend fun VirtualObjectCommandInterpreter.AwaitableCommand.toAwaitable():
      DurableFuture<String> {
    return when (this) {
      is VirtualObjectCommandInterpreter.CreateAwakeable -> {
        val awk = awakeable<String>()
        state().set("awk-${this.awakeableKey}", awk.id)
        awk
      }
      is VirtualObjectCommandInterpreter.RunThrowTerminalException ->
          runAsync<String>("should-fail-with-${this.reason}") {
            throw TerminalException(this.reason)
          }
      is VirtualObjectCommandInterpreter.RunReturns ->
          runAsync<String>("run-returns-${this.value}") { this.value }
      is VirtualObjectCommandInterpreter.Sleep ->
          timer("command-timer", this.timeoutMillis.milliseconds).map { "sleep" }
      is VirtualObjectCommandInterpreter.CreateSignal -> signal<String>(this.signalName)
    }
  }

  private suspend fun appendResult(newResult: String) =
      state().set("results", (state().get("results") ?: listOf<String>()) + listOf(newResult))
}
