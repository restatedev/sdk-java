package dev.restate.sdk.kotlin

import com.google.protobuf.MessageLite
import dev.restate.generated.core.CallbackIdentifier
import dev.restate.sdk.core.StateKey
import dev.restate.sdk.core.TypeTag
import io.grpc.MethodDescriptor
import kotlin.time.Duration

sealed interface RestateContext {

  suspend fun <T : Any> get(key: StateKey<T>): T?

  suspend fun <T> set(key: StateKey<T>, value: T)

  suspend fun clear(key: StateKey<*>)

  suspend fun sleep(duration: Duration) {
    timer(duration).await()
  }

  suspend fun timer(duration: Duration): Awaitable<Unit>

  suspend fun <T : MessageLite, R : MessageLite> call(
      methodDescriptor: MethodDescriptor<T, R>,
      parameter: T
  ): R {
    return callAsync(methodDescriptor, parameter).await()
  }

  suspend fun <T : MessageLite, R : MessageLite> callAsync(
      methodDescriptor: MethodDescriptor<T, R>,
      parameter: T
  ): Awaitable<R>

  suspend fun <T : MessageLite> backgroundCall(
      methodDescriptor: MethodDescriptor<T, MessageLite>,
      parameter: T
  )

  suspend fun <T> sideEffect(typeTag: TypeTag<T>, sideEffectAction: suspend () -> T?): T?

  suspend fun sideEffect(sideEffectAction: suspend () -> Unit) {
    sideEffect(TypeTag.VOID) {
      sideEffectAction()
      null
    }
  }

  suspend fun <T> callback(
      typeTag: TypeTag<T>,
      callbackAction: suspend (CallbackIdentifier) -> Unit
  ): T {
    return callbackAsync(typeTag, callbackAction).await()
  }

  suspend fun <T> callbackAsync(
      typeTag: TypeTag<T>,
      callbackAction: suspend (CallbackIdentifier) -> Unit
  ): Awaitable<T>

  suspend fun completeCallback(id: CallbackIdentifier, payload: Any)
}
