// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.RandomTestSuite
import dev.restate.sdk.core.testservices.GreeterGrpcKt
import dev.restate.sdk.core.testservices.GreetingRequest
import dev.restate.sdk.core.testservices.GreetingResponse
import dev.restate.sdk.core.testservices.greetingResponse
import io.grpc.BindableService
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers

class RandomTest : RandomTestSuite() {
  private class RandomShouldBeDeterministic :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {

    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val number = restateContext().random().nextInt()
      return greetingResponse { message = number.toString() }
    }
  }

  override fun randomShouldBeDeterministic(): BindableService {
    return RandomShouldBeDeterministic()
  }

  private class RandomInsideSideEffect :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      ctx.sideEffect { ctx.random().nextInt() }
      throw IllegalStateException("This should not unreachable")
    }
  }

  override fun randomInsideSideEffect(): BindableService {
    return RandomInsideSideEffect()
  }

  override fun getExpectedInt(seed: Long): Int {
    return Random(seed).nextInt()
  }
}
