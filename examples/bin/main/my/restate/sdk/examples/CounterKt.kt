// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.http.vertx.RestateHttpServer
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.kotlin.endpoint.*
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@VirtualObject
class CounterKt {

  companion object {
    private val TOTAL = stateKey<Long>("total")
    private val LOG: Logger = LogManager.getLogger(CounterKt::class.java)
  }

  @Serializable data class CounterUpdate(var oldValue: Long, val newValue: Long)

  @Handler
  suspend fun reset() {
    state().clear(TOTAL)
  }

  @Handler
  suspend fun add(value: Long) {
    val currentValue = state().get(TOTAL) ?: 0L
    val newValue = currentValue + value
    state().set(TOTAL, newValue)
  }

  @Handler
  @Shared
  suspend fun get(): Long? {
    return state().get(TOTAL)
  }

  @Handler
  suspend fun getAndAdd(value: Long): CounterUpdate {
    LOG.info("Invoked get and add with $value")
    val currentValue = state().get(TOTAL) ?: 0L
    val newValue = currentValue + value
    state().set(TOTAL, newValue)
    return CounterUpdate(currentValue, newValue)
  }
}

fun main() {
  val endpoint = endpoint { bind(CounterKt()) }
  RestateHttpServer.listen(endpoint)
}
