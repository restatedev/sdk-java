// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx.testservices

import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import dev.restate.sdk.kotlin.RestateCoroutineService
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import org.apache.logging.log4j.LogManager

class GreeterKtService(coroutineContext: CoroutineContext) :
    GreeterGrpcKt.GreeterCoroutineImplBase(coroutineContext), RestateCoroutineService {

  private val LOG = LogManager.getLogger(GreeterKtService::class.java)

  override suspend fun greet(request: GreetingRequest): GreetingResponse {
    LOG.info("Greet invoked!")

    val count = (restateContext().get(BlockingGreeterService.COUNTER) ?: 0) + 1
    restateContext().set(BlockingGreeterService.COUNTER, count)

    restateContext().sleep(1.seconds)

    return greetingResponse { message = "Hello ${request.name}. Count: $count" }
  }
}
