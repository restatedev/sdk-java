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
import dev.restate.sdk.testservices.contracts.Proxy
import dev.restate.serde.Serde
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ProxyImpl : Proxy {
  private fun Proxy.ProxyRequest.toTarget(): Target {
    return if (this.virtualObjectKey == null) {
      Target.service(this.serviceName, this.handlerName)
    } else {
      Target.virtualObject(this.serviceName, this.virtualObjectKey, this.handlerName)
    }
  }

  override suspend fun call(request: Proxy.ProxyRequest): ByteArray {
    return context()
        .call(
            Request.of(request.toTarget(), Serde.RAW, Serde.RAW, request.message).also {
              if (request.idempotencyKey != null) {
                it.idempotencyKey = request.idempotencyKey
              }
            }
        )
        .await()
  }

  override suspend fun oneWayCall(request: Proxy.ProxyRequest): String =
      context()
          .send(
              Request.of(request.toTarget(), Serde.RAW, Serde.SLICE, request.message).also {
                if (request.idempotencyKey != null) {
                  it.idempotencyKey = request.idempotencyKey
                }
              },
              request.delayMillis?.milliseconds ?: Duration.ZERO,
          )
          .invocationId()

  override suspend fun manyCalls(requests: List<Proxy.ManyCallRequest>) {
    val toAwait = mutableListOf<DurableFuture<ByteArray>>()

    for (request in requests) {
      if (request.oneWayCall) {
        context()
            .send(
                Request.of(
                        request.proxyRequest.toTarget(),
                        Serde.RAW,
                        Serde.SLICE,
                        request.proxyRequest.message,
                    )
                    .also {
                      if (request.proxyRequest.idempotencyKey != null) {
                        it.idempotencyKey = request.proxyRequest.idempotencyKey
                      }
                    },
                request.proxyRequest.delayMillis?.milliseconds ?: Duration.ZERO,
            )
      } else {
        val fut =
            context()
                .call(
                    Request.of(
                            request.proxyRequest.toTarget(),
                            Serde.RAW,
                            Serde.RAW,
                            request.proxyRequest.message,
                        )
                        .also {
                          if (request.proxyRequest.idempotencyKey != null) {
                            it.idempotencyKey = request.proxyRequest.idempotencyKey
                          }
                        }
                )
        if (request.awaitAtTheEnd) {
          toAwait.add(fut)
        }
      }
    }

    toAwait.toList().awaitAll()
  }
}
