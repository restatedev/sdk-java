// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices.contracts

import dev.restate.sdk.annotation.*
import dev.restate.sdk.kotlin.*
import kotlinx.serialization.Serializable

@Serializable
data class ProxyRequest(
    val serviceName: String,
    val virtualObjectKey: String? = null, // If null, the request is to a service
    val handlerName: String,
    // Bytes are encoded as array of numbers
    val message: ByteArray,
    val delayMillis: Int? = null,
    val idempotencyKey: String? = null
)

@Serializable
data class ManyCallRequest(
    val proxyRequest: ProxyRequest,
    /** If true, perform a one way call instead of a regular call */
    val oneWayCall: Boolean,
    /**
     * If await at the end, then perform the call as regular call, and collect all the futures to
     * wait at the end, before returning, instead of awaiting them immediately.
     */
    val awaitAtTheEnd: Boolean
)

@Service
@Name( "Proxy")
interface Proxy {
  // Bytes are encoded as array of numbers
  @Handler suspend fun call(context: Context, request: ProxyRequest): ByteArray

  // Returns the invocation id of the call
  @Handler suspend fun oneWayCall(context: Context, request: ProxyRequest): String

  @Handler suspend fun manyCalls(context: Context, requests: List<ManyCallRequest>)
}
