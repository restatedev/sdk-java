package dev.restate.sdk.vertx.testservices

import RestateCoroutineService
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import kotlin.coroutines.CoroutineContext

class GreeterKtService(coroutineContext: CoroutineContext) :
    GreeterGrpcKt.GreeterCoroutineImplBase(coroutineContext), RestateCoroutineService {

  override suspend fun greet(request: GreetingRequest): GreetingResponse {
    val ctx = restateContext()

    val count = (ctx.get(BlockingGreeterService.COUNTER) ?: 0) + 1
    ctx.set(BlockingGreeterService.COUNTER, count)

    return greetingResponse { message = "Hello ${request.name}. Count: $count" }
  }
}
