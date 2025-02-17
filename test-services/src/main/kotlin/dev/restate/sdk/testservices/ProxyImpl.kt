// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.common.CallRequest
import dev.restate.common.SendRequest
import dev.restate.common.Target
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.ManyCallRequest
import dev.restate.sdk.testservices.contracts.Proxy
import dev.restate.sdk.testservices.contracts.ProxyRequest
import dev.restate.serde.Serde
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

class ProxyImpl : Proxy {
  private fun ProxyRequest.toTarget(): Target {
    return if (this.virtualObjectKey == null) {
      Target.service(this.serviceName, this.handlerName)
    } else {
      Target.virtualObject(this.serviceName, this.virtualObjectKey, this.handlerName)
    }
  }

  override suspend fun call(context: Context, request: ProxyRequest): ByteArray {
    return context
        .call(CallRequest.of(request.toTarget(), Serde.RAW, Serde.RAW, request.message))
        .await()
  }

  override suspend fun oneWayCall(context: Context, request: ProxyRequest): Unit {
    val ignored =
        context.send(
            SendRequest.of(request.toTarget(), Serde.RAW, request.message)
                .delay((request.delayMillis?.milliseconds ?: Duration.ZERO).toJavaDuration()))
  }

  override suspend fun manyCalls(context: Context, requests: List<ManyCallRequest>) {
    val toAwait = mutableListOf<Awaitable<ByteArray>>()

    for (request in requests) {
      if (request.oneWayCall) {
        context.send(
            SendRequest.of(request.proxyRequest.toTarget(), Serde.RAW, request.proxyRequest.message)
                .delay(
                    (request.proxyRequest.delayMillis?.milliseconds ?: Duration.ZERO)
                        .toJavaDuration()))
      } else {
        val awaitable =
            context.call(
                CallRequest.of(
                    request.proxyRequest.toTarget(),
                    Serde.RAW,
                    Serde.RAW,
                    request.proxyRequest.message))
        if (request.awaitAtTheEnd) {
          toAwait.add(awaitable)
        }
      }
    }

    toAwait.toList().awaitAll()
  }
}
