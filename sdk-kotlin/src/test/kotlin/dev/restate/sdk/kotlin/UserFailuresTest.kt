package dev.restate.sdk.kotlin

import dev.restate.sdk.core.impl.UserFailuresTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import io.grpc.BindableService
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.Dispatchers

class UserFailuresTest : UserFailuresTestSuite() {
  private class ThrowIllegalStateException :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      throw IllegalStateException("Whatever")
    }
  }

  override fun throwIllegalStateException(): BindableService {
    throw UnsupportedOperationException("https://github.com/restatedev/sdk-java/issues/116")
  }

  private class SideEffectThrowIllegalStateException :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      restateContext().sideEffect { throw IllegalStateException("Whatever") }
      throw IllegalStateException("Not expected to reach this point")
    }
  }

  override fun sideEffectThrowIllegalStateException(): BindableService {
    return SideEffectThrowIllegalStateException()
  }

  private class ThrowStatusRuntimeException(private val status: Status) :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      throw StatusRuntimeException(status)
    }
  }

  override fun throwStatusRuntimeException(status: Status): BindableService {
    return ThrowStatusRuntimeException(status)
  }

  private class SideEffectThrowStatusRuntimeException(private val status: Status) :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      restateContext().sideEffect { throw StatusRuntimeException(status) }
      throw IllegalStateException("Not expected to reach this point")
    }
  }

  override fun sideEffectThrowStatusRuntimeException(status: Status): BindableService {
    return SideEffectThrowStatusRuntimeException(status)
  }
}
