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
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.core.StateTestSuite
import dev.restate.sdk.core.testservices.GreeterGrpcKt
import dev.restate.sdk.core.testservices.GreetingRequest
import dev.restate.sdk.core.testservices.GreetingResponse
import dev.restate.sdk.core.testservices.greetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers

class StateTest : StateTestSuite() {
  private class GetState :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val state: String =
          restateContext().get(StateKey.of("STATE", CoreSerdes.STRING_UTF8)) ?: "Unknown"
      return greetingResponse { message = "Hello $state" }
    }
  }

  override fun getState(): BindableService {
    return GetState()
  }

  private class GetAndSetState :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()

      val state = ctx.get(StateKey.of("STATE", CoreSerdes.STRING_UTF8))!!
      ctx.set(StateKey.of("STATE", CoreSerdes.STRING_UTF8), request.getName())

      return greetingResponse { message = "Hello $state" }
    }
  }

  override fun getAndSetState(): BindableService {
    return GetAndSetState()
  }

  override fun setNullState(): BindableService {
    throw UnsupportedOperationException("The kotlin type system enforces non null state values")
  }
}
