// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.common.Output
import dev.restate.sdk.types.DurablePromiseKey
import dev.restate.sdk.types.Request
import dev.restate.sdk.types.StateKey
import dev.restate.sdk.types.TerminalException
import dev.restate.serde.Serde
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration

/**
 * This interface exposes the Restate functionalities to Restate services. It can be used to
 * interact with other Restate services, record non-deterministic closures, execute timers and
 * synchronize with external systems.
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
   * @param name name to be used for the timer
   */
  suspend fun timer(duration: Duration, name: String? = null): Awaitable<Unit>

  /**
   * Invoke another Restate service method and wait for the response. Same as
   * `call(methodDescriptor, parameter).await()`.
   *
   * @param target the address of the callee
   * @param inputSerde Input serde
   * @param outputSerde Output serde
   * @param parameter the invocation request parameter.
   * @param callOptions request options.
   * @return the invocation response.
   */
  suspend fun <T : Any?, R : Any?> call(
      target: dev.restate.common.Target,
      inputSerde: Serde<T>,
      outputSerde: Serde<R>,
      parameter: T,
      callOptions: CallOptions = CallOptions.DEFAULT
  ): R {
    return callAsync(target, inputSerde, outputSerde, parameter, callOptions).await()
  }

  /**
   * Invoke another Restate service method.
   *
   * @param target the address of the callee
   * @param inputSerde Input serde
   * @param outputSerde Output serde
   * @param parameter the invocation request parameter.
   * @param callOptions request options.
   * @return an [Awaitable] that wraps the Restate service method result.
   */
  suspend fun <T : Any?, R : Any?> callAsync(
      target: dev.restate.common.Target,
      inputSerde: Serde<T>,
      outputSerde: Serde<R>,
      parameter: T,
      callOptions: CallOptions = CallOptions.DEFAULT
  ): Awaitable<R>

  /**
   * Invoke another Restate service without waiting for the response.
   *
   * @param target the address of the callee
   * @param inputSerde Input serde
   * @param parameter the invocation request parameter.
   * @param sendOptions request options.
   */
  suspend fun <T : Any?> send(
      target: dev.restate.common.Target,
      inputSerde: Serde<T>,
      parameter: T,
      sendOptions: SendOptions = SendOptions.DEFAULT
  ): Unit

  /**
   * Execute a non-deterministic closure, recording the result value in the journal. The result
   * value will be re-played in case of re-invocation (e.g. because of failure recovery or
   * suspension point) without re-executing the closure. Use this feature if you want to perform
   * <b>non-deterministic operations</b>.
   *
   * You can name this closure using the `name` parameter. This name will be available in the
   * observability tools.
   *
   * <p>The closure should tolerate retries, that is Restate might re-execute the closure multiple
   * times until it records a result. To control and limit the amount of retries, pass a
   * [RetryPolicy] to this function.
   *
   * <h2>Error handling</h2>
   *
   * Errors occurring within this closure won't be propagated to the caller, unless they are
   * [TerminalException]. Consider the following code:
   * ```
   * // Bad usage of try-catch outside the runBlock
   * try {
   *     ctx.runBlock {
   *         throw IllegalStateException();
   *     };
   * } catch (e: IllegalStateException) {
   *     // This will never be executed,
   *     // but the error will be retried by Restate,
   *     // following the invocation retry policy.
   * }
   *
   * // Good usage of try-catch outside the runBlock
   * try {
   *     ctx.runBlock {
   *         throw TerminalException("my error");
   *     };
   * } catch (e: TerminalException) {
   *     // This is invoked
   * }
   * ```
   *
   * To propagate failures to the run call-site, make sure to wrap them in [TerminalException].
   *
   * @param serde the type tag of the return value, used to serialize/deserialize it.
   * @param name the name of the side effect.
   * @param block closure to execute.
   * @param T type of the return value.
   * @return value of the runBlock operation.
   */
  suspend fun <T : Any?> runBlock(
      serde: Serde<T>,
      name: String = "",
      retryPolicy: RetryPolicy? = null,
      block: suspend () -> T
  ): T {
    return runAsync(serde, name, retryPolicy, block).await()
  }

  suspend fun <T : Any?> runAsync(
      serde: Serde<T>,
      name: String = "",
      retryPolicy: RetryPolicy? = null,
      block: suspend () -> T
  ): Awaitable<T>

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
   * [dev.restate.sdk.types.InvocationId], which is not secret.
   *
   * This instance is useful to generate identifiers, idempotency keys, and for uniform sampling
   * from a set of options. If a cryptographically secure value is needed, please generate that
   * externally using [runBlock].
   *
   * You MUST NOT use this [Random] instance inside a [runBlock].
   *
   * @return the [Random] instance.
   */
  fun random(): RestateRandom
}

/**
 * Execute a non-deterministic closure, recording the result value in the journal using
 * [KtSerdes.json]. The result value will be re-played in case of re-invocation (e.g. because of
 * failure recovery or suspension point) without re-executing the closure. Use this feature if you
 * want to perform <b>non-deterministic operations</b>.
 *
 * <p>The closure should tolerate retries, that is Restate might re-execute the closure multiple
 * times until it records a result. To control and limit the amount of retries, pass a [RetryPolicy]
 * to this function.
 *
 * <h2>Error handling</h2>
 *
 * Errors occurring within this closure won't be propagated to the caller, unless they are
 * [TerminalException]. Consider the following code:
 * ```
 * // Bad usage of try-catch outside the runBlock
 * try {
 *     ctx.runBlock {
 *         throw IllegalStateException();
 *     };
 * } catch (e: IllegalStateException) {
 *     // This will never be executed,
 *     // but the error will be retried by Restate,
 *     // following the invocation retry policy.
 * }
 *
 * // Good usage of try-catch outside the runBlock
 * try {
 *     ctx.runBlock {
 *         throw TerminalException("my error");
 *     };
 * } catch (e: TerminalException) {
 *     // This is invoked
 * }
 * ```
 *
 * To propagate failures to the run call-site, make sure to wrap them in [TerminalException].
 *
 * @param block closure to execute.
 * @param T type of the return value.
 * @return value of the runBlock operation.
 */
suspend inline fun <reified T : Any> Context.runBlock(
    name: String = "",
    retryPolicy: RetryPolicy? = null,
    noinline block: suspend () -> T
): T {
  return this.runBlock(KtSerdes.json(), name, retryPolicy, block)
}

suspend inline fun <reified T : Any> Context.runAsync(
    name: String = "",
    retryPolicy: RetryPolicy? = null,
    noinline block: suspend () -> T
): Awaitable<T> {
  return this.runAsync(KtSerdes.json(), name, retryPolicy, block)
}

/**
 * Create an [Awakeable] using [KtSerdes.json] deserializer, addressable through [Awakeable.id].
 *
 * You can use this feature to implement external asynchronous systems interactions, for example you
 * can send a Kafka record including the [Awakeable.id], and then let another service consume from
 * Kafka the responses of given external system interaction by using [awakeableHandle].
 *
 * @return the [Awakeable] to await on.
 * @see Awakeable
 */
suspend inline fun <reified T : Any> Context.awakeable(): Awakeable<T> {
  return this.awakeable(KtSerdes.json())
}

/**
 * This interface can be used only within shared handlers of virtual objects. It extends [Context]
 * adding access to the virtual object instance key-value state storage.
 */
sealed interface SharedObjectContext : Context {

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
}

/**
 * This interface can be used only within exclusive handlers of virtual objects. It extends
 * [Context] adding access to the virtual object instance key-value state storage.
 */
sealed interface ObjectContext : SharedObjectContext {

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

/**
 * This interface can be used only within shared handlers of workflow. It extends [Context] adding
 * access to the workflow instance key-value state storage and to the [DurablePromise] API.
 *
 * NOTE: This interface MUST NOT be accessed concurrently since it can lead to different orderings
 * of user actions, corrupting the execution of the invocation.
 *
 * @see Context
 * @see SharedObjectContext
 */
sealed interface SharedWorkflowContext : SharedObjectContext {
  /**
   * Create a [DurablePromise] for the given key.
   *
   * You can use this feature to implement interaction between different workflow handlers, e.g. to
   * send a signal from a shared handler to the workflow handler.
   *
   * @see DurablePromise
   */
  fun <T : Any> promise(key: DurablePromiseKey<T>): DurablePromise<T>

  /**
   * Create a new [DurablePromiseHandle] for the provided key. You can use it to
   * [DurablePromiseHandle.resolve] or [DurablePromiseHandle.reject] the given [DurablePromise].
   *
   * @see DurablePromise
   */
  fun <T : Any> promiseHandle(key: DurablePromiseKey<T>): DurablePromiseHandle<T>
}

/**
 * This interface can be used only within workflow handlers of workflow. It extends [Context] adding
 * access to the workflow instance key-value state storage and to the [DurablePromise] API.
 *
 * NOTE: This interface MUST NOT be accessed concurrently since it can lead to different orderings
 * of user actions, corrupting the execution of the invocation.
 *
 * @see Context
 * @see ObjectContext
 */
sealed interface WorkflowContext : SharedWorkflowContext, ObjectContext

class RestateRandom(seed: Long) : Random() {
  private val r = Random(seed)

  override fun nextBits(bitCount: Int): Int {
    return r.nextBits(bitCount)
  }

  /** Generate a UUID that is stable across retries and replays. */
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
 * @param T type o1f the awaitable result
 */
sealed interface Awaitable<T> {
  suspend fun await(): T

  suspend fun await(duration: Duration): T

  suspend fun withTimeout(duration: Duration): Awaitable<T>

  /** Clause for [select] operator. */
  val onAwait: SelectClause<T>

  suspend fun <R> map(transform: suspend (value: T) -> R): Awaitable<R>

  suspend fun <R> map(transformSuccess: suspend (value: T) -> R, transformFailure: suspend (exception: TerminalException) -> R): Awaitable<R>

  suspend fun mapFailure(transform: suspend (exception: TerminalException) -> T): Awaitable<T>

  companion object {
    fun all(
        first: Awaitable<*>,
        second: Awaitable<*>,
        vararg others: Awaitable<*>
    ): Awaitable<Unit> {
      return wrapAllAwaitable(listOf(first) + listOf(second) + others.asList())
    }

    fun any(
        first: Awaitable<*>,
        second: Awaitable<*>,
        vararg others: Awaitable<*>
    ): Awaitable<Int> {
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
 * }.await()
 * ```
 */
suspend inline fun <R> select(crossinline builder: SelectBuilder<R>.() -> Unit): Awaitable<R> {
  val selectImpl = SelectImplementation<R>()
  builder.invoke(selectImpl)
  return selectImpl.build()
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

/**
 * Complete with success the [Awakeable] using [KtSerdes.json] serializer.
 *
 * @param payload the result payload.
 * @see Awakeable
 */
suspend inline fun <reified T : Any> AwakeableHandle.resolve(payload: T) {
  return this.resolve(KtSerdes.json(), payload)
}

/**
 * A [DurablePromise] is a durable, distributed version of a Kotlin's Deferred, or more commonly of
 * a future/promise. Restate keeps track of the [DurablePromise] across restarts/failures.
 *
 * You can use this feature to implement interaction between different workflow handlers, e.g. to
 * send a signal from a shared handler to the workflow handler.
 *
 * Use [SharedWorkflowContext.promiseHandle] to complete a durable promise, either by
 * [DurablePromiseHandle.resolve] or [DurablePromiseHandle.reject].
 *
 * A [DurablePromise] is tied to a single workflow execution and can only be resolved or rejected
 * while the workflow run is still ongoing. Once the workflow is cleaned up, all its associated
 * promises with their completions will be cleaned up as well.
 *
 * NOTE: This interface MUST NOT be accessed concurrently since it can lead to different orderings
 * of user actions, corrupting the execution of the invocation.
 */
sealed interface DurablePromise<T> {
  /** @return the awaitable to await the promise on. */
  suspend fun awaitable(): Awaitable<T>

  /** @return the value, if already present, otherwise returns an empty optional. */
  suspend fun peek(): Output<T>
}

/** This class represents a handle to a [DurablePromise] created in another service. */
sealed interface DurablePromiseHandle<T> {
  /**
   * Complete with success the [DurablePromise].
   *
   * @param payload the result payload.
   * @see DurablePromise
   */
  suspend fun resolve(payload: T)

  /**
   * Complete with failure the [DurablePromise].
   *
   * @param reason the rejection reason.
   * @see DurablePromise
   */
  suspend fun reject(reason: String)
}
