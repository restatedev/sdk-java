package dev.restate.sdk.kotlin

import dev.restate.sdk.core.StateKey
import dev.restate.sdk.core.TypeTag
import dev.restate.sdk.core.impl.CompensationTestSuite
import dev.restate.sdk.core.impl.ProtoUtils
import dev.restate.sdk.core.impl.testservices.*
import io.grpc.BindableService
import io.grpc.Status
import java.lang.UnsupportedOperationException
import kotlinx.coroutines.Dispatchers

class CompensationTest : CompensationTestSuite() {
  private class ThrowManually :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      ctx.compensate { ctx.set(StateKey.of("message", TypeTag.STRING_UTF8), "none") }
      throw Status.FAILED_PRECONDITION.asRuntimeException()
    }
  }

  override fun throwManually(): BindableService {
    return ThrowManually()
  }

  private class NonTerminalErrorDoesntExecuteCompensations :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      ctx.compensate { ctx.set(StateKey.of("message", TypeTag.STRING_UTF8), "none") }
      throw RuntimeException("non-terminal")
    }
  }

  override fun nonTerminalErrorDoesntExecuteCompensations(): BindableService {
    throw UnsupportedOperationException("https://github.com/restatedev/sdk-java/issues/116")
  }

  private class CallCompensate :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      ctx.compensate { ctx.set(StateKey.of("message", TypeTag.STRING_UTF8), "none") }
      val res = ctx.call(GreeterGrpcKt.greetMethod, ProtoUtils.greetingRequest("Francesco"))
      ctx.set(StateKey.of("message", TypeTag.STRING_UTF8), res.getMessage())
      return greetingResponse { message = "ok" }
    }
  }

  override fun callCompensate(): BindableService {
    return CallCompensate()
  }

  private class IllegalGetStateWithinCompensation :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      ctx.compensate { ctx.get(StateKey.of("message", TypeTag.STRING_UTF8)) }
      throw Status.FAILED_PRECONDITION.asRuntimeException()
    }
  }

  override fun illegalGetStateWithinCompensation(): BindableService {
    return IllegalGetStateWithinCompensation()
  }
}
