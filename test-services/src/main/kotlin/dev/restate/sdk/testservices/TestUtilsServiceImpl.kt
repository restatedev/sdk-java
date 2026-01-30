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
  override suspend fun echo(input: String): String {
    return input
  }

  override suspend fun uppercaseEcho(input: String): String {
    return input.uppercase(Locale.getDefault())
  }

  override suspend fun echoHeaders(): Map<String, String> {
    return request().headers
  }

  override suspend fun rawEcho(input: ByteArray): ByteArray {
    check(input.contentEquals(request().bodyAsByteArray))
    return input
  }

  override suspend fun sleepConcurrently(millisDuration: List<Long>) {
    val timers = millisDuration.map { timer("${it.milliseconds}ms", it.milliseconds) }.toList()

    timers.awaitAll()
  }

  override suspend fun countExecutedSideEffects(increments: Int): Int {
    val invokedSideEffects = AtomicInteger(0)

    for (i in 0..<increments) {
      runBlock { invokedSideEffects.incrementAndGet() }
    }

    return invokedSideEffects.get()
  }

  override suspend fun cancelInvocation(invocationId: String) {
    invocationHandle<Unit>(invocationId).cancel()
  }
}
