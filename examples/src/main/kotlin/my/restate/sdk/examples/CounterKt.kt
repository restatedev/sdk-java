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
import dev.restate.sdk.kotlin.KtStateKey
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.SharedObjectContext
import dev.restate.sdk.kotlin.endpoint.endpoint
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Serializable data class CounterUpdate(var oldValue: Long, val newValue: Long)

@VirtualObject
class CounterKt {

  companion object {
    private val TOTAL = KtStateKey.json<Long>("total")
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
  @Shared
  suspend fun get(ctx: SharedObjectContext): Long {
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
  val endpoint = endpoint {
    bind(CounterKt)
  }
  RestateHttpServer.listen(endpoint)
}
