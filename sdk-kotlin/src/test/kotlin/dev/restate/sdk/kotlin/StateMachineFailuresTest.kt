package dev.restate.sdk.kotlin

import dev.restate.sdk.core.Serde
import dev.restate.sdk.core.StateKey
import dev.restate.sdk.core.impl.StateMachineFailuresTestSuite
import dev.restate.sdk.core.impl.testservices.GreeterGrpcKt
import dev.restate.sdk.core.impl.testservices.GreetingRequest
import dev.restate.sdk.core.impl.testservices.GreetingResponse
import dev.restate.sdk.core.impl.testservices.greetingResponse
import io.grpc.BindableService
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers

class StateMachineFailuresTest : StateMachineFailuresTestSuite() {
  private class GetState :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      restateContext().get(STATE)
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

  override fun getState(): BindableService {
    throw UnsupportedOperationException("https://github.com/restatedev/sdk-java/issues/116")
  }

  private class SideEffectFailure(private val serde: Serde<Int>) :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      restateContext().sideEffect(serde) { 0 }
      return greetingResponse { message = "Francesco" }
    }
  }

  override fun sideEffectFailure(serde: Serde<Int>): BindableService {
    throw UnsupportedOperationException("https://github.com/restatedev/sdk-java/issues/116")
  }
}
