// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.*
import dev.restate.sdk.common.Target
import dev.restate.sdk.common.syscalls.Syscalls
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration

/**
 * This interface exposes the Restate functionalities to Restate services. It can be used to
 * interact with other Restate services, record side effects, execute timers and synchronize with
 * external systems.
 *
 * To use it within your Restate service, implement [RestateKtComponent] and get an instance with
 * [RestateKtComponent.restateContext].
 *
 * All methods of this interface, and related interfaces, throws either [TerminalException] or
 * cancels the coroutine. [TerminalException] can be caught and acted upon.
 *
 * NOTE: This interface MUST NOT be accessed concurrently since it can lead to different orderings
 * of user actions, corrupting the execution of the invocation.
 */
sealed interface Context {

  fun request(): Request

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
   * @param target the address of the callee
   * @param inputSerde Input serde
   * @param outputSerde Output serde
   * @param parameter the invocation request parameter.
   * @return the invocation response.
   */
  suspend fun <T : Any, R : Any> call(
      target: Target,
      inputSerde: Serde<T>,
      outputSerde: Serde<R>,
      parameter: T
  ): R {
    return callAsync(target, inputSerde, outputSerde, parameter).await()
  }

  /**
   * Invoke another Restate service method.
   *
   * @param target the address of the callee
   * @param inputSerde Input serde
   * @param outputSerde Output serde
   * @param parameter the invocation request parameter.
   * @return an [Awaitable] that wraps the Restate service method result.
   */
  suspend fun <T : Any, R : Any> callAsync(
      target: Target,
      inputSerde: Serde<T>,
      outputSerde: Serde<R>,
      parameter: T
  ): Awaitable<R>

  /**
   * Invoke another Restate service without waiting for the response.
   *
   * @param target the address of the callee
   * @param inputSerde Input serde
   * @param parameter the invocation request parameter.
   * @param delay time to wait before executing the call
   */
  suspend fun <T : Any> send(
      target: Target,
      inputSerde: Serde<T>,
      parameter: T,
      delay: Duration = Duration.ZERO
  )

  /**
   * Execute a non-deterministic closure, recording the result value in the journal. The result
   * value will be re-played in case of re-invocation (e.g. because of failure recovery or
   * suspension point) without re-executing the closure. Use this feature if you want to perform
   * <b>non-deterministic operations</b>.
   *
   * <p>The closure should tolerate retries, that is Restate might re-execute the closure multiple
   * times until it records a result.
   *
   * <h2>Error handling</h2>
   *
   * Errors occurring within this closure won't be propagated to the caller, unless they are
   * [TerminalException]. Consider the following code:
   * ```
   * // Bad usage of try-catch outside the side effect
   * try {
   *     ctx.sideEffect {
   *         throw IllegalStateException();
   *     };
   * } catch (e: IllegalStateException) {
   *     // This will never be executed,
   *     // but the error will be retried by Restate,
   *     // following the invocation retry policy.
   * }
   *
   * // Good usage of try-catch outside the side effect
   * try {
   *     ctx.sideEffect {
   *         throw TerminalException("my error");
   *     };
   * } catch (e: TerminalException) {
   *     // This is invoked
   * }
   * ```
   *
   * To propagate side effects failures to the side effect call-site, make sure to wrap them in
   * [TerminalException].
   *
   * @param serde the type tag of the return value, used to serialize/deserialize it.
   * @param action to execute for its side effects.
   * @param T type of the return value.
   * @return value of the side effect operation.
   */
  suspend fun <T : Any?> sideEffect(serde: Serde<T>, sideEffectAction: suspend () -> T): T

  /** Like [sideEffect] without a return value. */
  suspend fun sideEffect(sideEffectAction: suspend () -> Unit) {
    sideEffect(KtSerdes.UNIT, sideEffectAction)
  }

  /**
   * Create an [Awakeable], addressable through [Awakeable.id].
   *
   * You can use this feature to implement external asynchronous systems interactions, for example
   * you can send a Kafka record including the [Awakeable.id], and then let another service consume
   * from Kafka the responses of given external system interaction by using [awakeableHandle].
   *
   * @param serde the response type tag to use for deserializing the [Awakeable] result.
   * @return the [Awakeable] to await on.
   * @see Awakeable
   */
  suspend fun <T : Any> awakeable(serde: Serde<T>): Awakeable<T>

  /**
   * Create a new [AwakeableHandle] for the provided identifier. You can use it to
   * [AwakeableHandle.resolve] or [AwakeableHandle.reject] the linked [Awakeable].
   *
   * @see Awakeable
   */
  fun awakeableHandle(id: String): AwakeableHandle

  /**
   * Create a [RestateRandom] instance inherently predictable, seeded on the
   * [dev.restate.sdk.common.InvocationId], which is not secret.
   *
   * This instance is useful to generate identifiers, idempotency keys, and for uniform sampling
   * from a set of options. If a cryptographically secure value is needed, please generate that
   * externally using [sideEffect].
   *
   * You MUST NOT use this [Random] instance inside a [sideEffect].
   *
   * @return the [Random] instance.
   */
  fun random(): RestateRandom
}

/**
 * This interface extends [Context] adding access to the virtual object instance key-value state
 * storage.
 */
sealed interface ObjectContext : Context {

  /** @return the key of this object */
  fun key(): String

  /**
   * Gets the state stored under key, deserializing the raw value using the [StateKey.serde].
   *
   * @param key identifying the state to get and its type.
   * @return the value containing the stored state deserialized.
   * @throws RuntimeException when the state cannot be deserialized.
   */
  suspend fun <T : Any> get(key: StateKey<T>): T?

  /**
   * Gets all the known state keys for this virtual object instance.
   *
   * @return the immutable collection of known state keys.
   */
  suspend fun stateKeys(): Collection<String>

  /**
   * Sets the given value under the given key, serializing the value using the [StateKey.serde].
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

  /** Clears all the state of this virtual object instance key-value state storage */
  suspend fun clearAll()
}

class RestateRandom(seed: Long, private val syscalls: Syscalls) : Random() {
  private val r = Random(seed)

  override fun nextBits(bitCount: Int): Int {
    check(!syscalls.isInsideSideEffect) { "You can't use RestateRandom inside a side effect!" }
    return r.nextBits(bitCount)
  }

  fun nextUUID(): UUID {
    return UUID(this.nextLong(), this.nextLong())
  }
}

/**
 * An [Awaitable] allows to await an asynchronous result. Once [await] is called, the execution
 * waits until the asynchronous result is available.
 *
 * The result can be either a success or a failure. In case of a failure, [await] will throw a
 * [dev.restate.sdk.core.TerminalException].
 *
 * @param T type of the awaitable result
 */
sealed interface Awaitable<T> {
  suspend fun await(): T

  /** Clause for [select] operator. */
  val onAwait: SelectClause<T>

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

/**
 * Like [kotlinx.coroutines.awaitAll], but for [Awaitable].
 *
 * ```
 *  val ctx = restateContext()
 *  val a1 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Francesco" })
 *  val a2 = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Till" })
 *
 *  val result = listOf(a1, a2)
 *    .awaitAll()
 *    .joinToString(separator = "-", transform = GreetingResponse::getMessage)
 * ```
 */
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
 * Like [kotlinx.coroutines.selects.select], but for [Awaitable]
 *
 * ```
 * val ctx = restateContext()
 * val callAwaitable = ctx.callAsync(GreeterGrpcKt.greetMethod, greetingRequest { name = "Francesco" })
 * val timeout = ctx.timer(10.seconds)
 * val result = select {
 *   callAwaitable.onAwait { it.message }
 *   timeout.onAwait { throw TimeoutException() }
 * }
 * ```
 */
suspend inline fun <R> select(crossinline builder: SelectBuilder<R>.() -> Unit): R {
  val selectImpl = SelectImplementation<R>()
  builder.invoke(selectImpl)
  return selectImpl.doSelect()
}

sealed interface SelectBuilder<in R> {
  /** Registers a clause in this [select] expression. */
  operator fun <T> SelectClause<T>.invoke(block: suspend (T) -> R)
}

sealed interface SelectClause<T> {
  val awaitable: Awaitable<T>
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
   * @param serde used to serialize the [Awakeable] result payload.
   * @param payload the result payload.
   * @see Awakeable
   */
  suspend fun <T : Any> resolve(serde: Serde<T>, payload: T)

  /**
   * Complete with failure the [Awakeable].
   *
   * @param reason the rejection reason.
   * @see Awakeable
   */
  suspend fun reject(reason: String)
}
