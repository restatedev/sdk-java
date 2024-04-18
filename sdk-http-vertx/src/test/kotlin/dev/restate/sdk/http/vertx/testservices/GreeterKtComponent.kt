// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx.testservices

import dev.restate.sdk.common.BindableService
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.kotlin.Service
import kotlin.time.Duration.Companion.seconds
import org.apache.logging.log4j.LogManager

private val LOG = LogManager.getLogger()
private val COUNTER: StateKey<Long> = BlockingGreeter.COUNTER

fun greeter(): BindableService<*> =
    Service.virtualObject("KtGreeter") {
      exclusiveHandler("greet") { ctx, request: String ->
        LOG.info("Greet invoked!")

        val count = (ctx.get(COUNTER) ?: 0) + 1
        ctx.set(COUNTER, count)

        ctx.sleep(1.seconds)

        "Hello $request. Count: $count"
      }
    }
