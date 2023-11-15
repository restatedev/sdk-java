package dev.restate.sdk.kotlin

import dev.restate.sdk.core.TerminalException
import dev.restate.sdk.core.impl.UserFailuresTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers

class UserFailuresTest : UserFailuresTestSuite() {
  private class ThrowIllegalStateException :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      throw IllegalStateException("Whatever")
    }
  }

  override fun throwIllegalStateException(): BindableService {
    return ThrowIllegalStateException()
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

  private class ThrowTerminalException(
      private val code: TerminalException.Code,
      private val message: String
  ) : GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      throw TerminalException(code, message)
    }
  }

  override fun throwTerminalException(
      code: TerminalException.Code,
      message: String
  ): BindableService {
    return ThrowTerminalException(code, message)
  }

  private class SideEffectThrowTerminalException(
      private val code: TerminalException.Code,
      private val message: String
  ) : GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      restateContext().sideEffect { throw TerminalException(code, message) }
      throw IllegalStateException("Not expected to reach this point")
    }
  }

  override fun sideEffectThrowTerminalException(
      code: TerminalException.Code,
      message: String
  ): BindableService {
    return SideEffectThrowTerminalException(code, message)
  }
}
