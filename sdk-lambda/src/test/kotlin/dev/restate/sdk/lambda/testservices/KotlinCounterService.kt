// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda.testservices

import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.endpoint.definition.HandlerType
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.endpoint.definition.ServiceType
import dev.restate.sdk.kotlin.HandlerRunner
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.serialization.KotlinSerializationSerdeFactory
import dev.restate.sdk.kotlin.serialization.jsonSerde
import dev.restate.sdk.types.StateKey
import dev.restate.serde.Serde
import java.nio.charset.StandardCharsets

private val COUNTER: StateKey<Long> =
    StateKey.of(
        "counter",
        Serde.using(
            { l: Long -> l.toString().toByteArray(StandardCharsets.UTF_8) },
            { v: ByteArray? -> String(v!!, StandardCharsets.UTF_8).toLong() }))

fun counter(): ServiceDefinition =
    ServiceDefinition.of(
        "KtCounter",
        ServiceType.VIRTUAL_OBJECT,
        listOf(
            HandlerDefinition.of(
                "get",
                HandlerType.EXCLUSIVE,
                jsonSerde<Unit>(),
                jsonSerde<Long>(),
                HandlerRunner.of(KotlinSerializationSerdeFactory()) { ctx: ObjectContext, _: Unit ->
                  ctx.get(COUNTER) ?: -1
                })))
