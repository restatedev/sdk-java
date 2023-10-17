package dev.restate.sdk.kotlin

import dev.restate.sdk.core.impl.SleepTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import io.grpc.BindableService
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers

class SleepTest : SleepTestSuite() {
  private class SleepGreeter :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      ctx.sleep(1000.milliseconds)
      return greetingResponse { message = "Hello" }
    }
  }

  override fun sleepGreeter(): BindableService {
    return SleepGreeter()
  }

  private class ManySleeps :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val awaitables = mutableListOf<Awaitable<Unit>>()
      for (i in 0..9) {
        awaitables.add(ctx.timer(1000.milliseconds))
      }
      awaitables.awaitAll()
      return greetingResponse {}
    }
  }

  override fun manySleeps(): BindableService {
    return ManySleeps()
  }
}
