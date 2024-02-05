// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.Serde
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.core.StateMachineFailuresTestSuite
import dev.restate.sdk.core.testservices.GreeterGrpcKt
import dev.restate.sdk.core.testservices.GreetingRequest
import dev.restate.sdk.core.testservices.GreetingResponse
import dev.restate.sdk.core.testservices.greetingResponse
import io.grpc.BindableService
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers

class StateMachineFailuresTest : StateMachineFailuresTestSuite() {
  private class GetState(private val nonTerminalExceptionsSeen: AtomicInteger) :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      try {
        KeyedContext.current().get(STATE)
      } catch (e: Throwable) {
        // A user should never catch Throwable!!!
        if (e !is CancellationException && e !is TerminalException) {
          nonTerminalExceptionsSeen.addAndGet(1)
        } else {
          throw e
        }
      }
      return greetingResponse { message = "Francesco" }
    }

    companion object {
      private val STATE =
          StateKey.of(
              "STATE",
              Serde.using({ i: Int -> i.toString().toByteArray(StandardCharsets.UTF_8) }) {
                  b: ByteArray? ->
                String(b!!, StandardCharsets.UTF_8).toInt()
              })
    }
  }

  override fun getState(nonTerminalExceptionsSeen: AtomicInteger): BindableService {
    return GetState(nonTerminalExceptionsSeen)
  }

  private class SideEffectFailure(private val serde: Serde<Int>) :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      KeyedContext.current().sideEffect(serde) { 0 }
      return greetingResponse { message = "Francesco" }
    }
  }

  override fun sideEffectFailure(serde: Serde<Int>): BindableService {
    return SideEffectFailure(serde)
  }
}
