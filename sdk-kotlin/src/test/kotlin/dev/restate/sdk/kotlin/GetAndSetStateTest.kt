package dev.restate.sdk.kotlin

import dev.restate.sdk.core.StateKey
import dev.restate.sdk.core.TypeTag
import dev.restate.sdk.core.impl.GetAndSetStateTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers

internal class GetAndSetStateTest : GetAndSetStateTestSuite() {
  private class GetAndSetGreeter :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()

      val state = ctx.get(StateKey.of("STATE", TypeTag.STRING_UTF8))!!
      ctx.set(StateKey.of("STATE", TypeTag.STRING_UTF8), request.getName())

      return greetingResponse { message = "Hello $state" }
    }
  }

  override fun getAndSetGreeter(): BindableService {
    return GetAndSetGreeter()
  }

  override fun setNullState(): BindableService {
    throw UnsupportedOperationException("The kotlin type system enforces non null state values")
  }
}
