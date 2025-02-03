// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx.testservices

import dev.restate.sdk.endpoint.definition.HandlerType
import dev.restate.sdk.endpoint.definition.ServiceType
import dev.restate.sdk.types.StateKey
import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.definition.HandlerSpecification
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.kotlin.HandlerRunner
import dev.restate.sdk.kotlin.KtSerdes
import dev.restate.sdk.kotlin.ObjectContext
import kotlin.time.Duration.Companion.seconds
import org.apache.logging.log4j.LogManager

private val LOG = LogManager.getLogger()
private val COUNTER: StateKey<Long> = BlockingGreeter.COUNTER

fun greeter(): ServiceDefinition<*> =
    ServiceDefinition.of(
        "KtGreeter",
        ServiceType.VIRTUAL_OBJECT,
        listOf(
            HandlerDefinition.of(
                HandlerSpecification.of(
                    "greet", HandlerType.EXCLUSIVE, KtSerdes.json(), KtSerdes.json()),
                HandlerRunner.of { ctx: ObjectContext, request: String ->
                  LOG.info("Greet invoked!")

                  val count = (ctx.get(COUNTER) ?: 0) + 1
                  ctx.set(COUNTER, count)

                  ctx.sleep(1.seconds)

                  "Hello $request. Count: $count"
                })))
