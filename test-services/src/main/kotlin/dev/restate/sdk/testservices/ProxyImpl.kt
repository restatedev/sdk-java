// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.common.Request
import dev.restate.common.Target
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.testservices.contracts.ManyCallRequest
import dev.restate.sdk.testservices.contracts.Proxy
import dev.restate.sdk.testservices.contracts.ProxyRequest
import dev.restate.serde.Serde
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ProxyImpl : Proxy {
  private fun ProxyRequest.toTarget(): Target {
    return if (this.virtualObjectKey == null) {
      Target.service(this.serviceName, this.handlerName)
    } else {
      Target.virtualObject(this.serviceName, this.virtualObjectKey, this.handlerName)
    }
  }

  override suspend fun call(context: Context, request: ProxyRequest): ByteArray {
    return Request.of(request.toTarget(), Serde.RAW, Serde.RAW, request.message)
        .also {
          if (request.idempotencyKey != null) {
            it.idempotencyKey = request.idempotencyKey
          }
        }
        .call(context)
        .await()
  }

  override suspend fun oneWayCall(context: Context, request: ProxyRequest): String =
      Request.of(request.toTarget(), Serde.RAW, Serde.SLICE, request.message)
          .also {
            if (request.idempotencyKey != null) {
              it.idempotencyKey = request.idempotencyKey
            }
          }
          .send(context, request.delayMillis?.milliseconds ?: Duration.ZERO)
          .invocationId()

  override suspend fun manyCalls(context: Context, requests: List<ManyCallRequest>) {
    val toAwait = mutableListOf<DurableFuture<ByteArray>>()

    for (request in requests) {
      if (request.oneWayCall) {
        Request.of(
                request.proxyRequest.toTarget(),
                Serde.RAW,
                Serde.SLICE,
                request.proxyRequest.message)
            .also {
              if (request.proxyRequest.idempotencyKey != null) {
                it.idempotencyKey = request.proxyRequest.idempotencyKey
              }
            }
            .send(context, request.proxyRequest.delayMillis?.milliseconds ?: Duration.ZERO)
      } else {
        val fut =
            Request.of(
                    request.proxyRequest.toTarget(),
                    Serde.RAW,
                    Serde.RAW,
                    request.proxyRequest.message)
                .also {
                  if (request.proxyRequest.idempotencyKey != null) {
                    it.idempotencyKey = request.proxyRequest.idempotencyKey
                  }
                }
                .call(context)
        if (request.awaitAtTheEnd) {
          toAwait.add(fut)
        }
      }
    }

    toAwait.toList().awaitAll()
  }
}
