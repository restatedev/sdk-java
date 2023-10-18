package dev.restate.sdk.kotlin

import dev.restate.sdk.core.InvocationId
import dev.restate.sdk.core.impl.InvocationIdTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers

class InvocationIdTest : InvocationIdTestSuite() {
  private class ReturnInvocationId :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      return greetingResponse { message = InvocationId.current().toString() }
    }
  }

  override fun returnInvocationId(): BindableService {
    return ReturnInvocationId()
  }
}
