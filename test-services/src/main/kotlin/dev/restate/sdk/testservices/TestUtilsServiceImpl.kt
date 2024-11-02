// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class TestUtilsServiceImpl : TestUtilsService {
  override suspend fun echo(context: Context, input: String): String {
    return input
  }

  override suspend fun uppercaseEcho(context: Context, input: String): String {
    return input.uppercase(Locale.getDefault())
  }

  override suspend fun echoHeaders(context: Context): Map<String, String> {
    return context.request().headers()
  }

  override suspend fun rawEcho(context: Context, input: ByteArray): ByteArray {
    return input
  }

  override suspend fun createAwakeableAndAwaitIt(
      ctx: Context,
      req: CreateAwakeableAndAwaitItRequest
  ): CreateAwakeableAndAwaitItResponse {
    val awakeable = ctx.awakeable<String>()
    AwakeableHolderClient.fromContext(ctx, req.awakeableKey).hold(awakeable.id)

    if (req.awaitTimeout == null) {
      return AwakeableResultResponse(awakeable.await())
    }

    val timeout = ctx.timer(req.awaitTimeout.milliseconds)
    return select {
      awakeable.onAwait { AwakeableResultResponse(it) }
      timeout.onAwait { TimeoutResponse }
    }
  }

  override suspend fun sleepConcurrently(context: Context, millisDuration: List<Long>) {
    val timers = millisDuration.map { context.timer(it.milliseconds) }.toList()

    timers.awaitAll()
  }

  override suspend fun countExecutedSideEffects(context: Context, increments: Int): Int {
    val invokedSideEffects = AtomicInteger(0)

    for (i in 0 ..< increments) {
      context.runBlock { invokedSideEffects.incrementAndGet() }
    }

    return invokedSideEffects.get()
  }

  override suspend fun getEnvVariable(context: Context, env: String): String {
    return context.runBlock { System.getenv(env) ?: "" }
  }

  override suspend fun interpretCommands(context: Context, req: InterpretRequest) {
    val listClient = ListObjectClient.fromContext(context, req.listName).send()
    req.commands.forEach {
      when (it) {
        is CreateAwakeableAndAwaitIt -> {
          val awakeable = context.awakeable<String>()
          AwakeableHolderClient.fromContext(context, it.awakeableKey).hold(awakeable.id)
          listClient.append(awakeable.await())
        }
        is GetEnvVariable -> listClient.append(getEnvVariable(context, it.envName))
      }
    }
  }
}
