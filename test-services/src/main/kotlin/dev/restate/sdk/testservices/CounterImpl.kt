// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.types.StateKey
import dev.restate.sdk.types.TerminalException
import dev.restate.sdk.kotlin.KtStateKey
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.SharedObjectContext
import dev.restate.sdk.testservices.contracts.Counter
import dev.restate.sdk.testservices.contracts.CounterUpdateResponse
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class CounterImpl : Counter {

  companion object {
    private val logger: Logger = LogManager.getLogger(CounterImpl::class.java)

    private val COUNTER_KEY: StateKey<Long> = KtStateKey.json<Long>("counter")
  }

  override suspend fun reset(context: ObjectContext) {
    logger.info("Counter cleaned up")
    context.clear(COUNTER_KEY)
  }

  override suspend fun addThenFail(context: ObjectContext, value: Long) {
    var counter: Long = context.get(COUNTER_KEY) ?: 0L
    logger.info("Old counter value: {}", counter)

    counter += value
    context.set(COUNTER_KEY, counter)

    logger.info("New counter value: {}", counter)

    throw TerminalException(context.key())
  }

  override suspend fun get(context: SharedObjectContext): Long {
    val counter: Long = context.get(COUNTER_KEY) ?: 0L
    logger.info("Get counter value: {}", counter)
    return counter
  }

  override suspend fun add(context: ObjectContext, value: Long): CounterUpdateResponse {
    val oldCount: Long = context.get(COUNTER_KEY) ?: 0L
    val newCount = oldCount + value
    context.set(COUNTER_KEY, newCount)

    logger.info("Old counter value: {}", oldCount)
    logger.info("New counter value: {}", newCount)

    return CounterUpdateResponse(oldCount, newCount)
  }
}
