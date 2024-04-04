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
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder
import dev.restate.sdk.kotlin.KtSerdes
import dev.restate.sdk.kotlin.ObjectContext
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Serializable data class CounterUpdate(var oldValue: Long, val newValue: Long)

@VirtualObject
class CounterKt {

  companion object {
    private val TOTAL = StateKey.of<Long>("total", KtSerdes.json())
    private val LOG: Logger = LogManager.getLogger(CounterKt::class.java)
  }

  @Handler
  suspend fun reset(ctx: ObjectContext) {
    ctx.clear(TOTAL)
  }

  @Handler
  suspend fun add(ctx: ObjectContext, value: Long) {
    val currentValue = ctx.get(TOTAL) ?: 0L
    val newValue = currentValue + value
    ctx.set(TOTAL, newValue)
  }

  @Handler
  suspend fun get(ctx: ObjectContext): Long {
    return ctx.get(TOTAL) ?: 0L
  }

  @Handler
  suspend fun getAndAdd(ctx: ObjectContext, value: Long): CounterUpdate {
    LOG.info("Invoked get and add with $value")
    val currentValue = ctx.get(TOTAL) ?: 0L
    val newValue = currentValue + value
    ctx.set(TOTAL, newValue)
    return CounterUpdate(currentValue, newValue)
  }
}

fun main() {
  RestateHttpEndpointBuilder.builder().bind(CounterKt()).buildAndListen()
}
