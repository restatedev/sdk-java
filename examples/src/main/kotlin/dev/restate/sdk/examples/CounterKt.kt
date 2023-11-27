// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.examples

import com.google.protobuf.Empty
import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.examples.generated.*
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder
import dev.restate.sdk.kotlin.RestateCoroutineService
import kotlinx.coroutines.Dispatchers
import org.apache.logging.log4j.LogManager

class CounterKt :
    CounterGrpcKt.CounterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {

  private val LOG = LogManager.getLogger(CounterKt::class.java)

  private val TOTAL = StateKey.of("total", CoreSerdes.LONG)

  override suspend fun reset(request: CounterRequest): Empty {
    restateContext().clear(TOTAL)
    return Empty.getDefaultInstance()
  }

  override suspend fun add(request: CounterAddRequest): Empty {
    updateCounter(request.value)
    return Empty.getDefaultInstance()
  }

  override suspend fun get(request: CounterRequest): GetResponse {
    return getResponse { value = getCounter() }
  }

  override suspend fun getAndAdd(request: CounterAddRequest): CounterUpdateResult {
    LOG.info("Invoked get and add with " + request.value)
    val (old, new) = updateCounter(request.value)
    return counterUpdateResult {
      oldValue = old
      newValue = new
    }
  }

  private suspend fun getCounter(): Long {
    return restateContext().get(TOTAL) ?: 0L
  }

  private suspend fun updateCounter(add: Long): Pair<Long, Long> {
    val currentValue = getCounter()
    val newValue = currentValue + add

    restateContext().set(TOTAL, newValue)

    return currentValue to newValue
  }
}

fun main() {
  RestateHttpEndpointBuilder.builder().withService(CounterKt()).buildAndListen()
}
