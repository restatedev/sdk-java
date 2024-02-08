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
import dev.restate.sdk.core.EagerStateTestSuite
import dev.restate.sdk.core.testservices.*
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.AssertionsForClassTypes.assertThat

class EagerStateTest : EagerStateTestSuite() {
  private class GetEmpty :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = KeyedContext.current()
      val stateIsEmpty = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING)) == null
      return greetingResponse { message = stateIsEmpty.toString() }
    }
  }

  override fun getEmpty(): BindableService {
    return GetEmpty()
  }

  private class Get :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      return greetingResponse {
        message = KeyedContext.current().get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!
      }
    }
  }

  override fun get(): BindableService {
    return Get()
  }

  private class GetAppendAndGet :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = KeyedContext.current()
      val oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!
      ctx.set(StateKey.of("STATE", CoreSerdes.JSON_STRING), oldState + request.getName())
      val newState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!
      return greetingResponse { message = newState }
    }
  }

  override fun getAppendAndGet(): BindableService {
    return GetAppendAndGet()
  }

  private class GetClearAndGet :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = KeyedContext.current()
      val oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!
      ctx.clear(StateKey.of("STATE", CoreSerdes.JSON_STRING))
      assertThat(ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))).isNull()
      return greetingResponse { message = oldState }
    }
  }

  override fun getClearAndGet(): BindableService {
    return GetClearAndGet()
  }

  private class GetClearAllAndGet : GreeterRestateKt.GreeterRestateKtImplBase() {
    override suspend fun greet(context: KeyedContext, request: GreetingRequest): GreetingResponse {
      val ctx = KeyedContext.current()
      val oldState = ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))!!

      ctx.clearAll()

      assertThat(ctx.get(StateKey.of("STATE", CoreSerdes.JSON_STRING))).isNull()
      assertThat(ctx.get(StateKey.of("ANOTHER_STATE", CoreSerdes.JSON_STRING))).isNull()

      return greetingResponse { message = oldState }
    }
  }

  override fun getClearAllAndGet(): BindableService {
    return GetClearAllAndGet()
  }
}
