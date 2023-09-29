package dev.restate.sdk.kotlin

import com.google.protobuf.MessageLite
import dev.restate.sdk.core.BindableNonBlockingService
import dev.restate.sdk.core.StateKey
import dev.restate.sdk.core.TypeTag
import dev.restate.sdk.core.syscalls.Syscalls
import io.grpc.MethodDescriptor
import java.util.*
import kotlin.time.Duration

/**
 * This interface exposes the Restate functionalities to Restate services. It can be used to access
 * the service instance key-value state storage, interact with other Restate services, record side
 * effects, execute timers and synchronize with external systems.
 *
 * To use it within your Restate service, implement [RestateCoroutineService] and get an instance
 * with [RestateCoroutineService.restateContext].
 *
 * NOTE: This interface should never be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 */
sealed interface RestateContext {

  /**
   * Gets the state stored under key, deserializing the raw value using the registered
   * [dev.restate.sdk.core.serde.Serde] in the interceptor.
   *
   * @param key identifying the state to get and its type.
   * @return the value containing the stored state deserialized.
   * @throws RuntimeException when the state cannot be deserialized.
   */
  suspend fun <T : Any> get(key: StateKey<T>): T?

  /**
   * Sets the given value under the given key, serializing the value using the registered
   * [dev.restate.sdk.core.serde.Serde] in the interceptor.
   *
   * @param key identifying the value to store and its type.
   * @param value to store under the given key.
   */
  suspend fun <T : Any> set(key: StateKey<T>, value: T)

  /**
   * Clears the state stored under key.
   *
   * @param key identifying the state to clear.
   */
  suspend fun clear(key: StateKey<*>)

  /**
   * Causes the current execution of the function invocation to sleep for the given duration.
   *
   * @param duration for which to sleep.
   */
  suspend fun sleep(duration: Duration) {
    timer(duration).await()
  }

  /**
   * Causes the start of a timer for the given duration. You can await on the timer end by invoking
   * [Awaitable.await].
   *
   * @param duration for which to sleep.
   */
  suspend fun timer(duration: Duration): Awaitable<Unit>

  /**
   * Invoke another Restate service method and wait for the response. Same as
   * `call(methodDescriptor, parameter).await()`.
   *
   * @param methodDescriptor The method descriptor of the method to invoke. This is found in the
   * generated `*Grpc` class.
   * @param parameter the invocation request parameter.
   * @return the invocation response.
   */
  suspend fun <T : MessageLite, R : MessageLite> call(
      methodDescriptor: MethodDescriptor<T, R>,
      parameter: T
  ): R {
    return callAsync(methodDescriptor, parameter).await()
  }

  /**
   * Invoke another Restate service method.
   *
   * @param methodDescriptor The method descriptor of the method to invoke. This is found in the
   * generated `*Grpc` class.
   * @param parameter the invocation request parameter.
   * @return an [Awaitable] that wraps the Restate service method result.
   */
  suspend fun <T : MessageLite, R : MessageLite> callAsync(
      methodDescriptor: MethodDescriptor<T, R>,
      parameter: T
  ): Awaitable<R>

  /**
   * Invoke another Restate service in a fire and forget fashion.
   *
   * @param methodDescriptor The method descriptor of the method to invoke. This is found in the
   * generated `*Grpc` class.
   * @param parameter the invocation request parameter.
   */
  suspend fun <T : MessageLite> backgroundCall(
      methodDescriptor: MethodDescriptor<T, MessageLite>,
      parameter: T
  )

  /**
   * Invoke another Restate service in a fire and forget fashion after the provided `delay` has
   * elapsed.
   *
   * This method returns immediately, as the timer is executed and awaited on Restate.
   *
   * @param methodDescriptor The method descriptor of the method to invoke. This is found in the
   * generated `*Grpc` class.
   * @param parameter the invocation request parameter.
   * @param delay time to wait before executing the call
   */
  suspend fun <T : MessageLite> delayedCall(
      methodDescriptor: MethodDescriptor<T, MessageLite>,
      parameter: T,
      delay: Duration
  )

  /**
   * Registers side effects that will be re-played in case of re-invocation (e.g. because of failure
   * recovery or suspension point).
   *
   * <p>Use this function if you want to perform non-deterministic operations.
   *
   * @param typeTag the type tag of the return value, used to serialize/deserialize it.
   * @param sideEffectAction to execute for its side effects.
   * @param T type of the return value.
   * @return value of the side effect operation.
   */
  suspend fun <T> sideEffect(typeTag: TypeTag<T>, sideEffectAction: suspend () -> T?): T?

  /** Like [sideEffect] without a return value. */
  suspend fun sideEffect(sideEffectAction: suspend () -> Unit) {
    sideEffect(TypeTag.VOID) {
      sideEffectAction()
      null
    }
  }

  /**
   * Create an [Awakeable], addressable through [Awakeable.id].
   *
   * You can use this feature to implement external asynchronous systems interactions, for example
   * you can send a Kafka record including the [Awakeable.id], and then let another service consume
   * from Kafka the responses of given external system interaction by using [awakeableHandle].
   *
   * @param typeTag the response type tag to use for deserializing the [Awakeable] result.
   * @return the [Awakeable] to await on.
   * @see Awakeable
   */
  suspend fun <T> awakeable(typeTag: TypeTag<T>): Awakeable<T>

  /**
   * Create a new [AwakeableHandle] for the provided identifier. You can use it to
   * [AwakeableHandle.resolve] or [AwakeableHandle.reject] the linked [Awakeable].
   *
   * @see Awakeable
   */
  fun awakeableHandle(id: String): AwakeableHandle
}

/**
 * An [Awaitable] allows to await an asynchronous result. Once [await] is called, the execution
 * waits until the asynchronous result is available.
 *
 * The result can be either a success or a failure. In case of a failure, [await] will throw a
 * [StatusRuntimeException].
 *
 * @param T type of the awaitable result
 */
sealed interface Awaitable<T> {
  suspend fun await(): T

  companion object {
    fun all(
        first: Awaitable<*>,
        second: Awaitable<*>,
        vararg others: Awaitable<*>
    ): Awaitable<Unit> {
      return wrapAllAwaitable(listOf(first) + listOf(second) + others.asList())
    }

    fun any(first: Awaitable<*>, second: Awaitable<*>, vararg others: Awaitable<*>): AnyAwaitable {
      return wrapAnyAwaitable(listOf(first) + listOf(second) + others.asList())
    }
  }
}

/** Like [kotlinx.coroutines.awaitAll], but for [Awaitable]. */
suspend fun <T> Collection<Awaitable<T>>.awaitAll(): List<T> {
  return awaitAll(*toTypedArray())
}

/** Like [kotlinx.coroutines.awaitAll], but for [Awaitable]. */
suspend fun <T> awaitAll(vararg awaitables: Awaitable<T>): List<T> {
  if (awaitables.isEmpty()) {
    return emptyList()
  }
  if (awaitables.size == 1) {
    return listOf(awaitables[0].await())
  }
  wrapAllAwaitable(awaitables.asList()).await()
  return awaitables.map { it.await() }.toList()
}

sealed interface AnyAwaitable : Awaitable<Any> {
  /** Same as [Awaitable.await], but returns the index of the first completed element. */
  suspend fun awaitIndex(): Int
}

/**
 * An [Awakeable] is a special type of [Awaitable] which can be arbitrarily completed by another
 * service, by addressing it with its [id].
 *
 * It can be used to let a service wait on a specific condition/result, which is fulfilled by
 * another service or by an external system at a later point in time.
 *
 * For example, you can send a Kafka record including the [Awakeable.id], and then let another
 * service consume from Kafka the responses of given external system interaction by using
 * [RestateContext.awakeableHandle].
 */
sealed interface Awakeable<T> : Awaitable<T> {
  /** The unique identifier of this [Awakeable] instance. */
  val id: String
}

/** This class represents a handle to an [Awakeable] created in another service. */
sealed interface AwakeableHandle {
  /**
   * Complete with success the [Awakeable].
   *
   * @param typeTag used to serialize the [Awakeable] result payload.
   * @param payload the result payload.
   * @see Awakeable
   */
  suspend fun <T : Any> resolve(typeTag: TypeTag<T>, payload: T)

  /**
   * Complete with failure the [Awakeable].
   *
   * @param reason the rejection reason.
   * @see Awakeable
   */
  suspend fun reject(reason: String)
}

/** Marker interface for Kotlin Restate coroutine services. */
interface RestateCoroutineService : BindableNonBlockingService {
  /** @return an instance of the [RestateContext]. */
  fun restateContext(): RestateContext {
    return RestateContextImpl(Syscalls.SYSCALLS_KEY.get())
  }
}
