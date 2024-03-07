// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples

import dev.restate.sdk.common.StateKey
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder
import dev.restate.sdk.kotlin.Component
import dev.restate.sdk.kotlin.KtSerdes
import kotlinx.serialization.Serializable

@Serializable data class CounterUpdate(var oldValue: Long, val newValue: Long)

private val totalKey = StateKey.of<Long>("total", KtSerdes.json())

val counter =
    Component.virtualObject("Counter") {
      handler("reset") { ctx, _: Unit -> ctx.clear(totalKey) }
      handler("add") { ctx, value: Long ->
        val currentValue = ctx.get(totalKey) ?: 0L
        val newValue = currentValue + value
        ctx.set(totalKey, newValue)
      }
      handler("get") { ctx, _: Unit -> ctx.get(totalKey) ?: 0L }
      handler("getAndAdd") { ctx, value: Long ->
        val currentValue = ctx.get(totalKey) ?: 0L
        val newValue = currentValue + value
        ctx.set(totalKey, newValue)
        CounterUpdate(currentValue, newValue)
      }
    }

fun main() {
  RestateHttpEndpointBuilder.builder().with(counter).buildAndListen()
}
