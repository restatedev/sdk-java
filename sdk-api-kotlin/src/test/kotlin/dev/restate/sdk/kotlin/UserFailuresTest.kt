// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.core.UserFailuresTestSuite
import dev.restate.sdk.core.testservices.GreeterGrpcKt
import dev.restate.sdk.core.testservices.GreetingRequest
import dev.restate.sdk.core.testservices.GreetingResponse
import io.grpc.BindableService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers

class UserFailuresTest : UserFailuresTestSuite() {
  private class ThrowIllegalStateException :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      throw IllegalStateException("Whatever")
    }
  }

  override fun throwIllegalStateException(): BindableService {
    return ThrowIllegalStateException()
  }

  private class SideEffectThrowIllegalStateException(
      private val nonTerminalExceptionsSeen: AtomicInteger
  ) : GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      try {
        restateContext().sideEffect { throw IllegalStateException("Whatever") }
      } catch (e: Throwable) {
        if (e !is CancellationException && e !is TerminalException) {
          nonTerminalExceptionsSeen.addAndGet(1)
        } else {
          throw e
        }
      }
      throw IllegalStateException("Not expected to reach this point")
    }
  }

  override fun sideEffectThrowIllegalStateException(
      nonTerminalExceptionsSeen: AtomicInteger
  ): BindableService {
    return SideEffectThrowIllegalStateException(nonTerminalExceptionsSeen)
  }

  private class ThrowTerminalException(
      private val code: TerminalException.Code,
      private val message: String
  ) : GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      throw TerminalException(code, message)
    }
  }

  override fun throwTerminalException(
      code: TerminalException.Code,
      message: String
  ): BindableService {
    return ThrowTerminalException(code, message)
  }

  private class SideEffectThrowTerminalException(
      private val code: TerminalException.Code,
      private val message: String
  ) : GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateKtService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      restateContext().sideEffect { throw TerminalException(code, message) }
      throw IllegalStateException("Not expected to reach this point")
    }
  }

  override fun sideEffectThrowTerminalException(
      code: TerminalException.Code,
      message: String
  ): BindableService {
    return SideEffectThrowTerminalException(code, message)
  }
}
