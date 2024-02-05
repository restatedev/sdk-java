// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.core.SideEffectTestSuite
import dev.restate.sdk.core.testservices.*
import io.grpc.BindableService
import java.util.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers

class SideEffectTest : SideEffectTestSuite() {
  private class SideEffect(private val sideEffectOutput: String) :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = KeyedContext.current()
      val result = ctx.sideEffect(CoreSerdes.JSON_STRING) { sideEffectOutput }
      return greetingResponse { message = "Hello $result" }
    }
  }

  override fun sideEffect(sideEffectOutput: String): BindableService {
    return SideEffect(sideEffectOutput)
  }

  private class ConsecutiveSideEffect(private val sideEffectOutput: String) :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = KeyedContext.current()
      val firstResult = ctx.sideEffect(CoreSerdes.JSON_STRING) { sideEffectOutput }
      val secondResult =
          ctx.sideEffect(CoreSerdes.JSON_STRING) { firstResult.uppercase(Locale.getDefault()) }
      return greetingResponse { message = "Hello $secondResult" }
    }
  }

  override fun consecutiveSideEffect(sideEffectOutput: String): BindableService {
    return ConsecutiveSideEffect(sideEffectOutput)
  }

  private class CheckContextSwitching :
      GreeterGrpcKt.GreeterCoroutineImplBase(
          Dispatchers.Unconfined + CoroutineName("CheckContextSwitchingTestCoroutine")),
      RestateKtService {

    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val sideEffectThread =
          KeyedContext.current().sideEffect(CoreSerdes.JSON_STRING) { Thread.currentThread().name }
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
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = KeyedContext.current()
      ctx.sideEffect {
        ctx.oneWayCall(GreeterGrpcKt.greetMethod, greetingRequest { name = "something" })
      }
      throw IllegalStateException("This point should not be reached")
    }
  }

  override fun sideEffectGuard(): BindableService {
    return SideEffectGuard()
  }
}
