package dev.restate.sdk.kotlin

import dev.restate.sdk.core.TypeTag
import dev.restate.sdk.core.impl.SideEffectTestSuite
import dev.restate.sdk.core.impl.testservices.*
import io.grpc.BindableService
import java.util.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers

internal class SideEffectTest : SideEffectTestSuite() {
  private class SideEffect(private val sideEffectOutput: String) :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx: RestateContext = restateContext()
      val result = ctx.sideEffect(TypeTag.STRING_UTF8) { sideEffectOutput }
      return greetingResponse { message = "Hello $result" }
    }
  }

  override fun sideEffect(sideEffectOutput: String): BindableService {
    return SideEffect(sideEffectOutput)
  }

  private class ConsecutiveSideEffect(private val sideEffectOutput: String) :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx: RestateContext = restateContext()
      val firstResult = ctx.sideEffect(TypeTag.STRING_UTF8) { sideEffectOutput }
      val secondResult =
          ctx.sideEffect(TypeTag.STRING_UTF8) { firstResult.uppercase(Locale.getDefault()) }
      return greetingResponse { message = "Hello $secondResult" }
    }
  }

  override fun consecutiveSideEffect(sideEffectOutput: String): BindableService {
    return ConsecutiveSideEffect(sideEffectOutput)
  }

  private class CheckContextSwitching :
      GreeterGrpcKt.GreeterCoroutineImplBase(
          Dispatchers.Unconfined + CoroutineName("CheckContextSwitchingTestCoroutine")),
      RestateCoroutineService {

    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val sideEffectThread =
          restateContext().sideEffect(TypeTag.STRING_UTF8) { Thread.currentThread().name }
      check(sideEffectThread.contains("CheckContextSwitchingTestCoroutine")) {
        "Side effect thread is not running within the same coroutine context of the handler method: $sideEffectThread"
      }
      return greetingResponse { message = "Hello" }
    }
  }

  override fun checkContextSwitching(): BindableService {
    return CheckContextSwitching()
  }

  private class SideEffectGuard :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      ctx.sideEffect {
        ctx.oneWayCall(GreeterGrpcKt.greetMethod, greetingRequest { name = "something" })
      }
      throw IllegalStateException("This point should not be reached")
    }
  }

  override fun sideEffectGuard(): BindableService {
    return SideEffectGuard()
  }

  private class SideEffectThenAwakeable :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      ctx.sideEffect { throw IllegalStateException("This should be replayed") }
      ctx.awakeable(TypeTag.BYTES).await()
      return greetingResponse { message = "Hello" }
    }
  }

  override fun sideEffectThenAwakeable(): BindableService {
    return SideEffectThenAwakeable()
  }
}
