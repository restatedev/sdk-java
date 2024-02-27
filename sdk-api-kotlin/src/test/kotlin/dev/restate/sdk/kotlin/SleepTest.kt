// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.SleepTestSuite
import dev.restate.sdk.core.testservices.GreeterGrpcKt
import dev.restate.sdk.core.testservices.GreetingRequest
import dev.restate.sdk.core.testservices.GreetingResponse
import dev.restate.sdk.core.testservices.greetingResponse
import io.grpc.BindableService
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers

class SleepTest : SleepTestSuite() {
  private class SleepGreeter :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtComponent {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = ObjectContext.current()
      ctx.sleep(1000.milliseconds)
      return greetingResponse { message = "Hello" }
    }
  }

  override fun sleepGreeter(): BindableService {
    return SleepGreeter()
  }

  private class ManySleeps :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtComponent {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = ObjectContext.current()
      val awaitables = mutableListOf<Awaitable<Unit>>()
      for (i in 0..9) {
        awaitables.add(ctx.timer(1000.milliseconds))
      }
      awaitables.awaitAll()
      return greetingResponse {}
    }
  }

  override fun manySleeps(): BindableService {
    return ManySleeps()
  }
}
