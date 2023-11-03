package dev.restate.sdk.kotlin

import dev.restate.sdk.core.CoreSerdes
import dev.restate.sdk.core.StateKey
import dev.restate.sdk.core.impl.EagerStateTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.AssertionsForClassTypes.assertThat

class EagerStateTest : EagerStateTestSuite() {
  private class GetEmpty :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val stateIsEmpty = ctx.get(StateKey.of("STATE", CoreSerdes.STRING_UTF8)) == null
      return greetingResponse { message = stateIsEmpty.toString() }
    }
  }

  override fun getEmpty(): BindableService {
    return GetEmpty()
  }

  private class Get :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      return greetingResponse {
        message = restateContext().get(StateKey.of("STATE", CoreSerdes.STRING_UTF8))!!
      }
    }
  }

  override fun get(): BindableService {
    return Get()
  }

  private class GetAppendAndGet :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val oldState = ctx.get(StateKey.of("STATE", CoreSerdes.STRING_UTF8))!!
      ctx.set(StateKey.of("STATE", CoreSerdes.STRING_UTF8), oldState + request.getName())
      val newState = ctx.get(StateKey.of("STATE", CoreSerdes.STRING_UTF8))!!
      return greetingResponse { message = newState }
    }
  }

  override fun getAppendAndGet(): BindableService {
    return GetAppendAndGet()
  }

  private class GetClearAndGet :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val oldState = ctx.get(StateKey.of("STATE", CoreSerdes.STRING_UTF8))!!
      ctx.clear(StateKey.of("STATE", CoreSerdes.STRING_UTF8))
      assertThat(ctx.get(StateKey.of("STATE", CoreSerdes.STRING_UTF8))).isNull()
      return greetingResponse { message = oldState }
    }
  }

  override fun getClearAndGet(): BindableService {
    return GetClearAndGet()
  }
}
