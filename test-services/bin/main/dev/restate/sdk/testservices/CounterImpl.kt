// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.Counter
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class CounterImpl : Counter {

  companion object {
    private val logger: Logger = LogManager.getLogger(CounterImpl::class.java)

    private val COUNTER_KEY: StateKey<Long> = stateKey<Long>("counter")
  }

  override suspend fun reset() {
    logger.info("Counter cleaned up")
    state().clear(COUNTER_KEY)
  }

  override suspend fun addThenFail(value: Long) {
    var counter: Long = state().get(COUNTER_KEY) ?: 0L
    logger.info("Old counter value: {}", counter)

    counter += value
    state().set(COUNTER_KEY, counter)

    logger.info("New counter value: {}", counter)

    throw TerminalException(objectKey())
  }

  override suspend fun get(): Long {
    val counter: Long = state().get(COUNTER_KEY) ?: 0L
    logger.info("Get counter value: {}", counter)
    return counter
  }

  override suspend fun add(value: Long): Counter.CounterUpdateResponse {
    val oldCount: Long = state().get(COUNTER_KEY) ?: 0L
    val newCount = oldCount + value
    state().set(COUNTER_KEY, newCount)

    logger.info("Old counter value: {}", oldCount)
    logger.info("New counter value: {}", newCount)

    return Counter.CounterUpdateResponse(oldCount, newCount)
  }
}
