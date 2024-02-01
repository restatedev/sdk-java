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
import dev.restate.sdk.core.DeferredTestSuite
import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.testservices.*
import io.grpc.BindableService
import java.util.stream.Stream
import kotlinx.coroutines.Dispatchers

class AwaitableTest : DeferredTestSuite() {
  private class ReverseAwaitOrder :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val a1 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Francesco" })
      val a2 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Till" })
      val a2Res = a2.await().getMessage()
      ctx.set(StateKey.of("A2", CoreSerdes.JSON_STRING), a2Res)
      val a1Res = a1.await().getMessage()
      return greetingResponse { message = "$a1Res-$a2Res" }
    }
  }

  override fun reverseAwaitOrder(): BindableService {
    return ReverseAwaitOrder()
  }

  private class AwaitTwiceTheSameAwaitable :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val a = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Francesco" })
      return greetingResponse { message = a.await().getMessage() + "-" + a.await().getMessage() }
    }
  }

  override fun awaitTwiceTheSameAwaitable(): BindableService {
    return AwaitTwiceTheSameAwaitable()
  }

  private class AwaitAll :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val a1 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Francesco" })
      val a2 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Till" })

      return greetingResponse {
        message =
            listOf(a1, a2)
                .awaitAll()
                .joinToString(separator = "-", transform = GreetingResponse::getMessage)
      }
    }
  }

  override fun awaitAll(): BindableService {
    return AwaitAll()
  }

  private class AwaitAny :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val a1 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Francesco" })
      val a2 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Till" })
      return Awaitable.any(a1, a2).await() as GreetingResponse
    }
  }

  private class AwaitSelect :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val a1 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Francesco" })
      val a2 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Till" })
      return select {
        a1.onAwait { it }
        a2.onAwait { it }
      }
    }
  }

  override fun awaitAny(): BindableService {
    return AwaitAny()
  }

  private class CombineAnyWithAll :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val a1 = ctx.awakeable(CoreSerdes.JSON_STRING)
      val a2 = ctx.awakeable(CoreSerdes.JSON_STRING)
      val a3 = ctx.awakeable(CoreSerdes.JSON_STRING)
      val a4 = ctx.awakeable(CoreSerdes.JSON_STRING)
      val a12 = Awaitable.any(a1, a2)
      val a23 = Awaitable.any(a2, a3)
      val a34 = Awaitable.any(a3, a4)
      Awaitable.all(a12, a23, a34).await()

      return greetingResponse {
        message = a12.await().toString() + a23.await() as String? + a34.await()
      }
    }
  }

  override fun combineAnyWithAll(): BindableService {
    return CombineAnyWithAll()
  }

  private class AwaitAnyIndex :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val a1 = ctx.awakeable(CoreSerdes.JSON_STRING)
      val a2 = ctx.awakeable(CoreSerdes.JSON_STRING)
      val a3 = ctx.awakeable(CoreSerdes.JSON_STRING)
      val a4 = ctx.awakeable(CoreSerdes.JSON_STRING)

      return greetingResponse {
        message = Awaitable.any(a1, Awaitable.all(a2, a3), a4).awaitIndex().toString()
      }
    }
  }

  override fun awaitAnyIndex(): BindableService {
    return AwaitAnyIndex()
  }

  private class AwaitOnAlreadyResolvedAwaitables :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val ctx = restateContext()
      val a1 = ctx.awakeable(CoreSerdes.JSON_STRING)
      val a2 = ctx.awakeable(CoreSerdes.JSON_STRING)
      val a12 = Awaitable.all(a1, a2)
      val a12and1 = Awaitable.all(a12, a1)
      val a121and12 = Awaitable.all(a12and1, a12)
      a12and1.await()
      a121and12.await()

      return greetingResponse { message = a1.await() + a2.await() }
    }
  }

  override fun awaitOnAlreadyResolvedAwaitables(): BindableService {
    return AwaitOnAlreadyResolvedAwaitables()
  }

  override fun awaitWithTimeout(): BindableService {
    throw UnsupportedOperationException("Not supported yet")
  }

  override fun definitions(): Stream<TestDefinition> {
    return Stream.concat(super.definitions(), super.anyTestDefinitions { AwaitSelect() })
  }
}
