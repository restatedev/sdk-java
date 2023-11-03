package dev.restate.sdk.kotlin

import dev.restate.sdk.core.CoreSerdes
import dev.restate.sdk.core.StateKey
import dev.restate.sdk.core.impl.StateTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers

class StateTest : StateTestSuite() {
  private class GetState :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val state: String =
          restateContext().get(StateKey.of("STATE", CoreSerdes.STRING_UTF8)) ?: "Unknown"
      return greetingResponse { message = "Hello $state" }
    }
  }

  override fun getState(): BindableService {
    return GetState()
  }

  private class GetAndSetState :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()

      val state = ctx.get(StateKey.of("STATE", CoreSerdes.STRING_UTF8))!!
      ctx.set(StateKey.of("STATE", CoreSerdes.STRING_UTF8), request.getName())

      return greetingResponse { message = "Hello $state" }
    }
  }

  override fun getAndSetState(): BindableService {
    return GetAndSetState()
  }

  override fun setNullState(): BindableService {
    throw UnsupportedOperationException("The kotlin type system enforces non null state values")
  }
}
