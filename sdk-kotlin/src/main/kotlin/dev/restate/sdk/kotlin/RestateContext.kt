package dev.restate.sdk.kotlin

import com.google.protobuf.MessageLite
import dev.restate.generated.core.AwakeableIdentifier
import dev.restate.sdk.core.StateKey
import dev.restate.sdk.core.TypeTag
import io.grpc.MethodDescriptor
import kotlin.time.Duration

sealed interface RestateContext {

  suspend fun <T : Any> get(key: StateKey<T>): T?

  suspend fun <T : Any> set(key: StateKey<T>, value: T)

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

  suspend fun <T : MessageLite> delayedCall(
      methodDescriptor: MethodDescriptor<T, MessageLite>,
      parameter: T,
      delay: Duration
  )

  suspend fun <T> sideEffect(typeTag: TypeTag<T>, sideEffectAction: suspend () -> T?): T?

  suspend fun sideEffect(sideEffectAction: suspend () -> Unit) {
    sideEffect(TypeTag.VOID) {
      sideEffectAction()
      null
    }
  }

  suspend fun <T> awakeable(typeTag: TypeTag<T>): Awakeable<T>

  fun awakeableHandle(id: AwakeableIdentifier): AwakeableHandle
}

sealed interface AwakeableHandle {
  suspend fun <T : Any> complete(typeTag: TypeTag<T>, payload: T)
}
