package dev.restate.sdk.kotlin

import dev.restate.sdk.core.StateKey
import dev.restate.sdk.core.TypeTag
import dev.restate.sdk.core.impl.GetStateTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers

internal class GetStateTest : GetStateTestSuite() {
  private class GetStateGreeter :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val state: String =
          restateContext().get(StateKey.of("STATE", TypeTag.STRING_UTF8)) ?: "Unknown"
      return greetingResponse { message = "Hello $state" }
    }
  }

  override fun getStateGreeter(): BindableService {
    return GetStateGreeter()
  }
}
