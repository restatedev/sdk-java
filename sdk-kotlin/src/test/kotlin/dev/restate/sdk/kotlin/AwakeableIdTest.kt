package dev.restate.sdk.kotlin

import dev.restate.sdk.core.TypeTag
import dev.restate.sdk.core.impl.AwakeableIdTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers

internal class AwakeableIdTest : AwakeableIdTestSuite() {
  private class ReturnAwakeableId :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {

    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val id: String = restateContext().awakeable(TypeTag.STRING_UTF8).id
      return greetingResponse { message = id }
    }
  }

  override fun returnAwakeableId(): BindableService {
    return ReturnAwakeableId()
  }
}
