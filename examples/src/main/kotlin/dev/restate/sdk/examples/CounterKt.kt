// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.examples

import dev.restate.sdk.common.StateKey
import dev.restate.sdk.examples.generated.*
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder
import dev.restate.sdk.kotlin.KeyedContext
import dev.restate.sdk.kotlin.KtSerdes
import org.apache.logging.log4j.LogManager

class CounterKt : CounterRestateKt.CounterRestateKtImplBase() {

  private val LOG = LogManager.getLogger(CounterKt::class.java)

  private val TOTAL = StateKey.of<Long>("total", KtSerdes.json())

  override suspend fun reset(context: KeyedContext, request: CounterRequest) {
    context.clear(TOTAL)
  }

  override suspend fun add(context: KeyedContext, request: CounterAddRequest) {
    updateCounter(context, request.value)
  }

  override suspend fun get(context: KeyedContext, request: CounterRequest): GetResponse {
    return getResponse { value = context.get(TOTAL) ?: 0L }
  }

  override suspend fun getAndAdd(
      context: KeyedContext,
      request: CounterAddRequest
  ): CounterUpdateResult {
    LOG.info("Invoked get and add with " + request.value)
    val (old, new) = updateCounter(context, request.value)
    return counterUpdateResult {
      oldValue = old
      newValue = new
    }
  }

  private suspend fun updateCounter(context: KeyedContext, add: Long): Pair<Long, Long> {
    val currentValue = context.get(TOTAL) ?: 0L
    val newValue = currentValue + add

    context.set(TOTAL, newValue)

    return currentValue to newValue
  }
}

fun main() {
  RestateHttpEndpointBuilder.builder().withService(CounterKt()).buildAndListen()
}
