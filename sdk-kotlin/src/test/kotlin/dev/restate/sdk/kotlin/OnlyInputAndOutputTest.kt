package dev.restate.sdk.kotlin

import dev.restate.sdk.core.impl.OnlyInputAndOutputTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers

internal class OnlyInputAndOutputTest : OnlyInputAndOutputTestSuite() {
  private class NoSyscallsGreeter :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      return greetingResponse { message = "Hello " + request.getName() }
    }
  }

  override fun noSyscallsGreeter(): BindableService {
    return NoSyscallsGreeter()
  }
}
