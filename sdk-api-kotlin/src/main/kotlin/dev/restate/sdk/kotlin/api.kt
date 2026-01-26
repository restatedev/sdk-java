// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.common.InvocationOptions
import dev.restate.common.Output
import dev.restate.common.Request
import dev.restate.common.Slice
import dev.restate.common.reflection.kotlin.RequestCaptureProxy
import dev.restate.common.reflection.kotlin.captureInvocation
import dev.restate.common.reflections.ProxySupport
import dev.restate.common.reflections.ReflectionUtils
import dev.restate.sdk.common.DurablePromiseKey
import dev.restate.sdk.common.HandlerRequest
import dev.restate.sdk.common.InvocationId
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.serde.TypeTag
import dev.restate.serde.kotlinx.*
import java.nio.ByteBuffer
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine
import kotlin.random.Random
import kotlin.time.Duration
import kotlinx.coroutines.currentCoroutineContext

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

  fun request(): HandlerRequest

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
   * [DurableFuture.await].
   *
   * @param duration for which to sleep.
   * @param name name to be used for the timer
   */
  suspend fun timer(duration: Duration, name: String? = null): DurableFuture<Unit>

  /**
   * Invoke another Restate handler.
   *
   * @param request Request object. For each service, a class called `<your_class_name>Handlers` is
   *   generated containing the request builders.
   * @return a [CallDurableFuture] that wraps the result.
   */
  suspend fun <Req : Any?, Res : Any?> call(request: Request<Req, Res>): CallDurableFuture<Res>

  /**
   * Invoke another Restate handler without waiting for the response.
   *
   * @param request Request object. For each service, a class called `<your_class_name>Handlers` is
   *   generated containing the request builders.
   * @param delay The delay to send the request, if any.
   * @return an [InvocationHandle] to interact with the sent request.
   */
  suspend fun <Req : Any?, Res : Any?> send(
      request: Request<Req, Res>,
      delay: Duration? = null,
  ): InvocationHandle<Res>

  /**
   * Get an [InvocationHandle] for an already existing invocation. This will let you interact with a
   * running invocation, for example to cancel it or retrieve its result.
   *
   * @param invocationId The invocation to interact with.
   * @param responseClazz The response class.
   */
  fun <Res : Any?> invocationHandle(
      invocationId: String,
      responseTypeTag: TypeTag<Res>,
  ): InvocationHandle<Res>

  /**
   * Execute a closure, recording the result value in the journal. The result value will be
   * re-played in case of re-invocation (e.g. because of failure recovery or suspension point)
   * without re-executing the closure.
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
   * @param typeTag the type tag of the return value, used to serialize/deserialize it.
   * @param name the name of the side effect.
   * @param block closure to execute.
   * @param T type of the return value.
   * @return value of the runBlock operation.
   */
  suspend fun <T : Any?> runBlock(
      typeTag: TypeTag<T>,
      name: String = "",
      retryPolicy: RetryPolicy? = null,
      block: suspend () -> T,
  ): T {
    return runAsync(typeTag, name, retryPolicy, block).await()
  }

  /**
   * Execute a closure asynchronously. This is like [runBlock], but it returns a [DurableFuture]
   * that you can combine and select.
   *
   * ```
   * // Fan out the subtasks - run them in parallel
   * val futures = subTasks.map { subTask ->
   *    ctx.runAsync { subTask.execute() }
   * }
   *
   * // Fan in - Await all results and aggregate
   * val results = futures.awaitAll()
   * ```
   *
   * @see runBlock
   */
  suspend fun <T : Any?> runAsync(
      typeTag: TypeTag<T>,
      name: String = "",
      retryPolicy: RetryPolicy? = null,
      block: suspend () -> T,
  ): DurableFuture<T>

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
  suspend fun <T : Any> awakeable(typeTag: TypeTag<T>): Awakeable<T>

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
   * externally using [runBlock].
   *
   * You MUST NOT use this [Random] instance inside a [runBlock].
   *
   * @return the [Random] instance.
   */
  fun random(): RestateRandom
}

/**
 * Get an [InvocationHandle] for an already existing invocation. This will let you interact with a
 * running invocation, for example to cancel it or retrieve its result.
 *
 * @param invocationId The invocation to interact with.
 */
inline fun <reified Res : Any?> Context.invocationHandle(
    invocationId: String
): InvocationHandle<Res> {
  return this.invocationHandle(invocationId, typeTag<Res>())
}

/**
 * Execute a closure, recording the result value in the journal. The result value will be re-played
 * in case of re-invocation (e.g. because of failure recovery or suspension point) without
 * re-executing the closure.
 *
 * You can name this closure using the `name` parameter. This name will be available in the
 * observability tools.
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
 * @param name the name of the side effect.
 * @param block closure to execute.
 * @param T type of the return value.
 * @return value of the runBlock operation.
 */
suspend inline fun <reified T : Any> Context.runBlock(
    name: String = "",
    retryPolicy: RetryPolicy? = null,
    noinline block: suspend () -> T,
): T {
  return this.runBlock(typeTag<T>(), name, retryPolicy, block)
}

/**
 * Execute a closure asynchronously. This is like [runBlock], but it returns a [DurableFuture] that
 * you can combine and select.
 *
 * ```
 * // Fan out the subtasks - run them in parallel
 * val futures = subTasks.map { subTask ->
 *    ctx.runAsync { subTask.execute() }
 * }
 *
 * // Fan in - Await all results and aggregate
 * val results = futures.awaitAll()
 * ```
 *
 * @see runBlock
 */
suspend inline fun <reified T : Any> Context.runAsync(
    name: String = "",
    retryPolicy: RetryPolicy? = null,
    noinline block: suspend () -> T,
): DurableFuture<T> {
  return this.runAsync(typeTag<T>(), name, retryPolicy, block)
}

/**
 * Create an [Awakeable], addressable through [Awakeable.id].
 *
 * You can use this feature to implement external asynchronous systems interactions, for example you
 * can send a Kafka record including the [Awakeable.id], and then let another service consume from
 * Kafka the responses of given external system interaction by using [awakeableHandle].
 *
 * @return the [Awakeable] to await on.
 * @see Awakeable
 */
suspend inline fun <reified T : Any> Context.awakeable(): Awakeable<T> {
  return this.awakeable(typeTag<T>())
}

/**
 * This interface can be used only within shared handlers of virtual objects. It extends [Context]
 * adding access to the virtual object instance key-value state storage.
 */
sealed interface SharedObjectContext : Context {

  /** @return the key of this object */
  fun key(): String

  /**
   * Gets the state stored under key, deserializing the raw value using the [StateKey.serdeInfo].
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

inline fun <reified T> stateKey(name: String): StateKey<T> {
  return StateKey.of(name, typeTag<T>())
}

suspend inline fun <reified T : Any> SharedObjectContext.get(key: String): T? {
  return this.get(StateKey.of<T>(key, typeTag<T>()))
}

/**
 * This interface can be used only within exclusive handlers of virtual objects. It extends
 * [Context] adding access to the virtual object instance key-value state storage.
 */
sealed interface ObjectContext : SharedObjectContext {

  /**
   * Sets the given value under the given key, serializing the value using the [StateKey.serdeInfo].
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

suspend inline fun <reified T : Any> ObjectContext.set(key: String, value: T) {
  this.set(StateKey.of<T>(key, typeTag<T>()), value)
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
 * A [DurableFuture] allows to await an asynchronous result. Once [await] is called, the execution
 * waits until the asynchronous result is available.
 *
 * The result can be either a success or a failure. In case of a failure, [await] will throw a
 * [dev.restate.sdk.common.TerminalException].
 *
 * @param T type of this future's result
 */
sealed interface DurableFuture<T> {

  /**
   * Wait for this [DurableFuture] to complete.
   *
   * @throws TerminalException if this future was completed with a failure
   */
  suspend fun await(): T

  /**
   * Same as [await] but throws a [dev.restate.sdk.common.TimeoutException] if this [DurableFuture]
   * doesn't complete before the provided `timeout`.
   */
  suspend fun await(duration: Duration): T

  /**
   * Creates a [DurableFuture] that throws a [dev.restate.sdk.common.TimeoutException] if this
   * future doesn't complete before the provided `timeout`.
   */
  suspend fun withTimeout(duration: Duration): DurableFuture<T>

  /** Clause for [select] operator. */
  val onAwait: SelectClause<T>

  /**
   * Map the success result of this [DurableFuture].
   *
   * @param transform the mapper to execute if this [DurableFuture] completes with success. The
   *   mapper can throw a [dev.restate.sdk.common.TerminalException], thus failing the returned
   *   [DurableFuture].
   * @return a new [DurableFuture] with the mapped result, when completed
   */
  suspend fun <R> map(transform: suspend (value: T) -> R): DurableFuture<R>

  /**
   * Map both the success and the failure result of this [DurableFuture].
   *
   * @param transformSuccess the mapper to execute if this [DurableFuture] completes with success.
   *   The mapper can throw a [dev.restate.sdk.common.TerminalException], thus failing the returned
   *   [DurableFuture].
   * @param transformFailure the mapper to execute if this [DurableFuture] completes with failure.
   *   The mapper can throw a [dev.restate.sdk.common.TerminalException], thus failing the returned
   *   [DurableFuture].
   * @return a new [DurableFuture] with the mapped result, when completed
   */
  suspend fun <R> map(
      transformSuccess: suspend (value: T) -> R,
      transformFailure: suspend (exception: TerminalException) -> R,
  ): DurableFuture<R>

  /**
   * Map the failure result of this [DurableFuture].
   *
   * @param transform the mapper to execute if this [DurableFuture] completes with failure. The
   *   mapper can throw a [dev.restate.sdk.common.TerminalException], thus failing the returned
   *   [DurableFuture].
   * @return a new [DurableFuture] with the mapped result, when completed
   */
  suspend fun mapFailure(transform: suspend (exception: TerminalException) -> T): DurableFuture<T>

  companion object {
    /** @see awaitAll */
    fun all(
        first: DurableFuture<*>,
        second: DurableFuture<*>,
        vararg others: DurableFuture<*>,
    ): DurableFuture<Unit> {
      return wrapAllDurableFuture(listOf(first) + listOf(second) + others.asList())
    }

    /** @see awaitAll */
    fun all(durableFutures: List<DurableFuture<*>>): DurableFuture<Unit> {
      return wrapAllDurableFuture(durableFutures)
    }

    /** @see select */
    fun any(
        first: DurableFuture<*>,
        second: DurableFuture<*>,
        vararg others: DurableFuture<*>,
    ): DurableFuture<Int> {
      return wrapAnyDurableFuture(listOf(first) + listOf(second) + others.asList())
    }

    /** @see select */
    fun any(durableFutures: List<DurableFuture<*>>): DurableFuture<Int> {
      return wrapAnyDurableFuture(durableFutures)
    }
  }
}

/**
 * Like [kotlinx.coroutines.awaitAll], but for [DurableFuture].
 *
 * ```
 *  val a1 = ctx.awakeable<String>()
 *  val a2 = ctx.awakeable<String>()
 *
 *  val result = listOf(a1, a2)
 *    .awaitAll()
 *    .joinToString(separator = "-")
 * ```
 */
suspend fun <T> Collection<DurableFuture<T>>.awaitAll(): List<T> {
  return awaitAll(*toTypedArray())
}

/** @see Collection.awaitAll */
suspend fun <T> awaitAll(vararg durableFutures: DurableFuture<T>): List<T> {
  if (durableFutures.isEmpty()) {
    return emptyList()
  }
  if (durableFutures.size == 1) {
    return listOf(durableFutures[0].await())
  }
  wrapAllDurableFuture(durableFutures.asList()).await()
  return durableFutures.map { it.await() }.toList()
}

/**
 * Like [kotlinx.coroutines.selects.select], but for [DurableFuture]
 *
 * ```
 * val callFuture = ctx.awakeable()
 * val timeout = ctx.timer(10.seconds)
 *
 * val result = select {
 *   callFuture.onAwait { it.message }
 *   timeout.onAwait { throw TimeoutException() }
 * }.await()
 * ```
 */
suspend inline fun <R> select(crossinline builder: SelectBuilder<R>.() -> Unit): DurableFuture<R> {
  val selectImpl = SelectImplementation<R>()
  builder.invoke(selectImpl)
  return selectImpl.build()
}

sealed interface SelectBuilder<in R> {
  /** Registers a clause in this [select] expression. */
  operator fun <T> SelectClause<T>.invoke(block: suspend (T) -> R)
}

sealed interface SelectClause<T> {
  val durableFuture: DurableFuture<T>
}

/** The [DurableFuture] returned by a [Context.call]. */
sealed interface CallDurableFuture<T> : DurableFuture<T> {
  /** Get the invocation id of this call. */
  suspend fun invocationId(): String
}

/** An invocation handle, that can be used to interact with a running invocation. */
sealed interface InvocationHandle<Res : Any?> {
  /** @return the invocation id of this invocation */
  suspend fun invocationId(): String

  /** Cancel this invocation. */
  suspend fun cancel()

  /** Attach to this invocation. This will wait for the invocation to complete */
  suspend fun attach(): DurableFuture<Res>

  /** @return the output of this invocation, if present. */
  suspend fun output(): Output<Res>
}

/**
 * An [Awakeable] is a special type of [DurableFuture] which can be arbitrarily completed by another
 * service, by addressing it with its [id].
 *
 * It can be used to let a service wait on a specific condition/result, which is fulfilled by
 * another service or by an external system at a later point in time.
 *
 * For example, you can send a Kafka record including the [Awakeable.id], and then let another
 * service consume from Kafka the responses of given external system interaction by using
 * [RestateContext.awakeableHandle].
 */
sealed interface Awakeable<T> : DurableFuture<T> {
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

/**
 * Complete with success the [Awakeable].
 *
 * @param payload the result payload.
 * @see Awakeable
 */
suspend inline fun <reified T : Any> AwakeableHandle.resolve(payload: T) {
  return this.resolve(typeTag<T>(), payload)
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
  /** @return the future to await the promise result on. */
  suspend fun future(): DurableFuture<T>

  @Deprecated(
      message = "Use future() instead",
      level = DeprecationLevel.WARNING,
      replaceWith = ReplaceWith(expression = "future()"),
  )
  suspend fun awaitable(): DurableFuture<T> {
    return future()
  }

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

inline fun <reified T> durablePromiseKey(name: String): DurablePromiseKey<T> {
  return DurablePromiseKey.of(name, typeTag<T>())
}

/** Shorthand for [Context.call] */
suspend fun <Req : Any?, Res : Any?> Request<Req, Res>.call(
    context: Context
): CallDurableFuture<Res> {
  return context.call(this)
}

/** Shorthand for [Context.send] */
suspend fun <Req : Any?, Res : Any?> Request<Req, Res>.send(
    context: Context,
    delay: Duration? = null,
): InvocationHandle<Res> {
  return context.send(this, delay)
}

val HandlerRequest.invocationId: InvocationId
  get() = this.invocationId()
val HandlerRequest.openTelemetryContext: io.opentelemetry.context.Context
  get() = this.openTelemetryContext()
val HandlerRequest.body: Slice
  get() = this.body()
val HandlerRequest.bodyAsByteArray: ByteArray
  get() = this.bodyAsByteArray()
val HandlerRequest.bodyAsByteBuffer: ByteBuffer
  get() = this.bodyAsBodyBuffer()
val HandlerRequest.headers: Map<String, String>
  get() = this.headers()

// =============================================================================
// Free-floating API functions for the reflection-based API
// =============================================================================

/**
 * Get the current Restate [Context] from within a handler.
 *
 * This function must be called from within a Restate handler's suspend function. It retrieves the
 * context from the coroutine context.
 *
 * Example usage:
 * ```kotlin
 * @Service
 * class MyService {
 *     @Handler
 *     suspend fun myHandler(input: String): String {
 *         val ctx = context()
 *         // Use ctx for Restate operations
 *         return "processed: $input"
 *     }
 * }
 * ```
 *
 * @throws IllegalStateException if called outside of a Restate handler
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun context(): Context {
  val element =
      currentCoroutineContext()[dev.restate.sdk.kotlin.internal.RestateContextElement]
          ?: error("context() must be called from within a Restate handler")
  return element.ctx
}

/**
 * Get the current request information.
 *
 * @throws IllegalStateException if called outside of a Restate handler
 * @see Context.request
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun request(): HandlerRequest {
  return context().request()
}

/**
 * Get the deterministic random instance.
 *
 * @throws IllegalStateException if called outside of a Restate handler
 * @see Context.random
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun random(): RestateRandom {
  return context().random()
}

/**
 * Causes the current execution of the function invocation to sleep for the given duration.
 *
 * @param duration for which to sleep.
 * @throws IllegalStateException if called outside of a Restate handler
 * @see Context.sleep
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun sleep(duration: Duration) {
  context().sleep(duration)
}

/**
 * Causes the start of a timer for the given duration.
 *
 * @param duration for which to sleep.
 * @param name name to be used for the timer
 * @throws IllegalStateException if called outside of a Restate handler
 * @see Context.timer
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun timer(name: String = "", duration: Duration): DurableFuture<Unit> {
  return context().timer(duration, name)
}

/**
 * Execute a closure, recording the result value in the journal.
 *
 * @param name the name of the side effect.
 * @param retryPolicy optional retry policy.
 * @param block closure to execute.
 * @return value of the run operation.
 * @throws IllegalStateException if called outside of a Restate handler
 * @see Context.runBlock
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend inline fun <reified T : Any> runBlock(
    name: String = "",
    retryPolicy: RetryPolicy? = null,
    noinline block: suspend () -> T,
): T {
  return context().runBlock(typeTag<T>(), name, retryPolicy, block)
}

/**
 * Execute a closure asynchronously.
 *
 * @param name the name of the side effect.
 * @param retryPolicy optional retry policy.
 * @param block closure to execute.
 * @return a [DurableFuture] that you can combine and select.
 * @throws IllegalStateException if called outside of a Restate handler
 * @see Context.runAsync
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend inline fun <reified T : Any> runAsync(
    name: String = "",
    retryPolicy: RetryPolicy? = null,
    noinline block: suspend () -> T,
): DurableFuture<T> {
  return context().runAsync(typeTag<T>(), name, retryPolicy, block)
}

/**
 * Create an [Awakeable], addressable through [Awakeable.id].
 *
 * @return the [Awakeable] to await on.
 * @throws IllegalStateException if called outside of a Restate handler
 * @see Context.awakeable
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend inline fun <reified T : Any> awakeable(): Awakeable<T> {
  return context().awakeable(typeTag<T>())
}

/**
 * Create an [Awakeable], addressable through [Awakeable.id].
 *
 * You can use this feature to implement external asynchronous systems interactions, for example you
 * can send a Kafka record including the [Awakeable.id], and then let another service consume from
 * Kafka the responses of given external system interaction by using [awakeableHandle].
 *
 * @param typeTag the type tag for deserializing the [Awakeable] result.
 * @return the [Awakeable] to await on.
 * @see Awakeable
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun <T : Any> awakeable(typeTag: TypeTag<T>): Awakeable<T> {
  return context().awakeable(typeTag)
}

/**
 * Create a new [AwakeableHandle] for the provided identifier.
 *
 * @throws IllegalStateException if called outside of a Restate handler
 * @see Context.awakeableHandle
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun awakeableHandle(id: String): AwakeableHandle {
  return context().awakeableHandle(id)
}

/**
 * Get an [InvocationHandle] for an already existing invocation.
 *
 * @param invocationId The invocation to interact with.
 * @throws IllegalStateException if called outside of a Restate handler
 * @see Context.invocationHandle
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend inline fun <reified R : Any?> invocationHandle(invocationId: String): InvocationHandle<R> {
  return context().invocationHandle(invocationId, typeTag<R>())
}

/**
 * Get the key of this Virtual Object or Workflow.
 *
 * @return the key of this object
 * @throws IllegalStateException if called from a regular Service handler or outside of a Restate
 *   handler
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun key(): String {
  val ctx = context()
  val handlerContext =
      dev.restate.sdk.endpoint.definition.HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL.get()
          ?: error("key() must be called from within a Restate handler")

  if (!handlerContext.canReadState()) {
    error(
        "key() can be used only within Virtual Object or Workflow handlers. " +
            "Check https://docs.restate.dev/develop/java/state for more details."
    )
  }

  return (ctx as SharedObjectContext).key()
}

/**
 * Access to this Virtual Object/Workflow state.
 *
 * @return [KotlinState] for this Virtual Object/Workflow
 * @throws IllegalStateException if called from a regular Service handler or outside of a Restate
 *   handler
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun state(): KotlinState {
  val ctx = context()
  val handlerContext =
      dev.restate.sdk.endpoint.definition.HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL.get()
          ?: error("state() must be called from within a Restate handler")

  if (!handlerContext.canReadState()) {
    error(
        "state() can be used only within Virtual Object or Workflow handlers. " +
            "Check https://docs.restate.dev/develop/java/state for more details."
    )
  }

  return KotlinStateImpl(ctx as SharedObjectContext, handlerContext)
}

/**
 * Create a [DurablePromise] for the given key.
 *
 * @throws IllegalStateException if called from a non-Workflow handler or outside of a Restate
 *   handler
 * @see SharedWorkflowContext.promise
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun <T : Any> promise(key: DurablePromiseKey<T>): DurablePromise<T> {
  val ctx = context()
  val handlerContext =
      dev.restate.sdk.endpoint.definition.HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL.get()
          ?: error("promise() must be called from within a Restate handler")

  if (!handlerContext.canReadPromises() || !handlerContext.canWritePromises()) {
    error(
        "promise(key) can be used only within Workflow handlers. " +
            "Check https://docs.restate.dev/develop/java/external-events#durable-promises for more details."
    )
  }

  return (ctx as SharedWorkflowContext).promise(key)
}

/**
 * Create a new [DurablePromiseHandle] for the provided key.
 *
 * @throws IllegalStateException if called from a non-Workflow handler or outside of a Restate
 *   handler
 * @see SharedWorkflowContext.promiseHandle
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend fun <T : Any> promiseHandle(key: DurablePromiseKey<T>): DurablePromiseHandle<T> {
  val ctx = context()
  val handlerContext =
      dev.restate.sdk.endpoint.definition.HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL.get()
          ?: error("promiseHandle() must be called from within a Restate handler")

  if (!handlerContext.canReadPromises() || !handlerContext.canWritePromises()) {
    error(
        "promiseHandle(key) can be used only within Workflow handlers. " +
            "Check https://docs.restate.dev/develop/java/external-events#durable-promises for more details."
    )
  }

  return (ctx as SharedWorkflowContext).promiseHandle(key)
}

/**
 * Interface for accessing Virtual Object/Workflow state in the reflection-based API.
 *
 * This interface provides suspend-friendly state operations that can be used from within Restate
 * handlers using the free-floating `state()` function.
 *
 * Example usage:
 * ```kotlin
 * @VirtualObject
 * class Counter {
 *     companion object {
 *         private val COUNT = stateKey<Long>("count")
 *     }
 *
 *     @Handler
 *     suspend fun increment(): Long {
 *         val current = state().get(COUNT) ?: 0L
 *         val next = current + 1
 *         state().set(COUNT, next)
 *         return next
 *     }
 * }
 * ```
 */
@org.jetbrains.annotations.ApiStatus.Experimental
interface KotlinState {
  /**
   * Gets the state stored under key, deserializing the raw value using the [StateKey.serdeInfo].
   *
   * @param key identifying the state to get and its type.
   * @return the value containing the stored state deserialized, or null if not set.
   * @throws RuntimeException when the state cannot be deserialized.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental suspend fun <T : Any> get(key: StateKey<T>): T?

  /**
   * Sets the given value under the given key, serializing the value using the [StateKey.serdeInfo].
   *
   * @param key identifying the value to store and its type.
   * @param value to store under the given key.
   * @throws IllegalStateException if called from a Shared handler
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  suspend fun <T : Any> set(key: StateKey<T>, value: T)

  /**
   * Clears the state stored under key.
   *
   * @param key identifying the state to clear.
   * @throws IllegalStateException if called from a Shared handler
   */
  @org.jetbrains.annotations.ApiStatus.Experimental suspend fun clear(key: StateKey<*>)

  /**
   * Clears all the state of this virtual object instance key-value state storage.
   *
   * @throws IllegalStateException if called from a Shared handler
   */
  @org.jetbrains.annotations.ApiStatus.Experimental suspend fun clearAll()

  /**
   * Gets all the known state keys for this virtual object instance.
   *
   * @return the immutable collection of known state keys.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental suspend fun keys(): Collection<String>
}

/**
 * Gets the state stored under key.
 *
 * @param key the name of the state key.
 * @return the value containing the stored state deserialized, or null if not set.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend inline fun <reified T : Any> KotlinState.get(key: String): T? {
  return this.get(StateKey.of<T>(key, typeTag<T>()))
}

/**
 * Sets the given value under the given key.
 *
 * @param key the name of the state key.
 * @param value to store under the given key.
 * @throws IllegalStateException if called from a Shared handler
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend inline fun <reified T : Any> KotlinState.set(key: String, value: T) {
  this.set(StateKey.of<T>(key, typeTag<T>()), value)
}

// Internal implementation of KotlinState
private class KotlinStateImpl(
    private val ctx: SharedObjectContext,
    private val handlerContext: dev.restate.sdk.endpoint.definition.HandlerContext,
) : KotlinState {
  override suspend fun <T : Any> get(key: StateKey<T>): T? {
    return ctx.get(key)
  }

  override suspend fun <T : Any> set(key: StateKey<T>, value: T) {
    checkCanWriteState("set")
    (ctx as ObjectContext).set(key, value)
  }

  override suspend fun clear(key: StateKey<*>) {
    checkCanWriteState("clear")
    (ctx as ObjectContext).clear(key)
  }

  override suspend fun clearAll() {
    checkCanWriteState("clearAll")
    (ctx as ObjectContext).clearAll()
  }

  override suspend fun keys(): Collection<String> {
    return ctx.stateKeys()
  }

  private fun checkCanWriteState(opName: String) {
    if (!handlerContext.canWriteState()) {
      error(
          "state().$opName() cannot be used in shared handlers. " +
              "Check https://docs.restate.dev/develop/java/state for more details."
      )
    }
  }
}

/**
 * Kotlin-idiomatic request for invoking Restate services from within a handler.
 *
 * Example usage:
 * ```kotlin
 * toService<CounterKt>()
 *     .request { it.add(1) }
 *     .withOptions { idempotencyKey = "123" }
 *     .call()
 * ```
 *
 * @param Req the request type
 * @param Res the response type
 */
@org.jetbrains.annotations.ApiStatus.Experimental
interface KRequest<Req, Res> : Request<Req, Res> {

  /**
   * Configure invocation options using a DSL.
   *
   * @param block builder block for options
   * @return a new request with the configured options
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  fun withOptions(block: InvocationOptions.Builder.() -> Unit): KRequest<Req, Res>

  /**
   * Call the target handler and return a [CallDurableFuture] for the result.
   *
   * @return a [CallDurableFuture] that will contain the response
   */
  @org.jetbrains.annotations.ApiStatus.Experimental suspend fun call(): CallDurableFuture<Res>

  /**
   * Send the request without waiting for the response.
   *
   * @param delay optional delay before the invocation is executed
   * @return an [InvocationHandle] to interact with the sent request
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  suspend fun send(delay: Duration? = null): InvocationHandle<Res>
}

/**
 * Builder for creating type-safe requests from within a handler.
 *
 * This builder allows the response type to be inferred from the lambda passed to [request].
 *
 * @param SVC the service/virtual object/workflow class
 */
@org.jetbrains.annotations.ApiStatus.Experimental
class KRequestBuilder<SVC : Any>
@PublishedApi
internal constructor(
    private val clazz: Class<SVC>,
    private val key: String?,
) {
  /**
   * Create a request by invoking a method on the target.
   *
   * The response type is inferred from the return type of the invoked method.
   *
   * @param Res the response type (inferred from the lambda)
   * @param block a suspend lambda that invokes a method on the target
   * @return a [KRequest] with the correct response type
   */
  @Suppress("UNCHECKED_CAST")
  fun <Res> request(block: suspend (SVC) -> Res): KRequest<Any?, Res> {
    return KRequestImpl(
        RequestCaptureProxy(clazz, key).capture(block as suspend (SVC) -> Any?).toRequest()
    )
        as KRequest<Any?, Res>
  }
}

/**
 * Create a builder for invoking a Restate service from within a handler.
 *
 * Example usage:
 * ```kotlin
 * @Handler
 * suspend fun myHandler(): String {
 *     val result = toService<Greeter>()
 *         .request { it.greet("Alice") }
 *         .call()
 *         .await()
 *     return result
 * }
 * ```
 *
 * @param SVC the service class annotated with @Service
 * @return a builder for creating typed requests
 */
@org.jetbrains.annotations.ApiStatus.Experimental
inline fun <reified SVC : Any> toService(): KRequestBuilder<SVC> {
  ReflectionUtils.mustHaveServiceAnnotation(SVC::class.java)
  require(ReflectionUtils.isKotlinClass(SVC::class.java)) {
    "Using Java classes with Kotlin's API is not supported"
  }
  return KRequestBuilder(SVC::class.java, null)
}

/**
 * Create a builder for invoking a Restate virtual object from within a handler.
 *
 * Example usage:
 * ```kotlin
 * @Handler
 * suspend fun myHandler(): Long {
 *     val result = toVirtualObject<Counter>("my-counter")
 *         .request { it.add(1) }
 *         .call()
 *         .await()
 *     return result
 * }
 * ```
 *
 * @param SVC the virtual object class annotated with @VirtualObject
 * @param key the key identifying the specific virtual object instance
 * @return a builder for creating typed requests
 */
@org.jetbrains.annotations.ApiStatus.Experimental
inline fun <reified SVC : Any> toVirtualObject(key: String): KRequestBuilder<SVC> {
  ReflectionUtils.mustHaveVirtualObjectAnnotation(SVC::class.java)
  require(ReflectionUtils.isKotlinClass(SVC::class.java)) {
    "Using Java classes with Kotlin's API is not supported"
  }
  return KRequestBuilder(SVC::class.java, key)
}

/**
 * Create a builder for invoking a Restate workflow from within a handler.
 *
 * Example usage:
 * ```kotlin
 * @Handler
 * suspend fun myHandler(): String {
 *     val result = toWorkflow<MyWorkflow>("workflow-123")
 *         .request { it.run("input") }
 *         .call()
 *         .await()
 *     return result
 * }
 * ```
 *
 * @param SVC the workflow class annotated with @Workflow
 * @param key the key identifying the specific workflow instance
 * @return a builder for creating typed requests
 */
@org.jetbrains.annotations.ApiStatus.Experimental
inline fun <reified SVC : Any> toWorkflow(key: String): KRequestBuilder<SVC> {
  ReflectionUtils.mustHaveWorkflowAnnotation(SVC::class.java)
  require(ReflectionUtils.isKotlinClass(SVC::class.java)) {
    "Using Java classes with Kotlin's API is not supported"
  }
  return KRequestBuilder(SVC::class.java, key)
}

/** Implementation of [KRequest] for SDK context. */
private class KRequestImpl<Req, Res>(private val request: Request<Req, Res>) :
    KRequest<Req, Res>, Request<Req, Res> by request {
  override fun withOptions(block: InvocationOptions.Builder.() -> Unit): KRequest<Req, Res> {
    val builder = InvocationOptions.builder()
    builder.block()
    return KRequestImpl(
        this.toBuilder().headers(builder.headers).idempotencyKey(builder.idempotencyKey).build()
    )
  }

  override suspend fun call(): CallDurableFuture<Res> {
    return context().call(request)
  }

  override suspend fun send(delay: Duration?): InvocationHandle<Res> {
    return context().send(request, delay)
  }
}

/**
 * Create a proxy client for a Restate service.
 *
 * This creates a proxy that allows calling service methods directly. The proxy intercepts method
 * calls, converts them to Restate requests, and awaits the result.
 *
 * Example usage:
 * ```kotlin
 * @Handler
 * suspend fun myHandler(): String {
 *     val greeter = service<Greeter>()
 *     val response = greeter.greet("Alice")
 *     return "Got: $response"
 * }
 * ```
 *
 * @param SVC the service class annotated with @Service
 * @return a proxy client to invoke the service
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend inline fun <reified SVC : Any> service(): SVC {
  return service(SVC::class.java)
}

/**
 * Create a proxy client for a Restate virtual object.
 *
 * Example usage:
 * ```kotlin
 * @Handler
 * suspend fun myHandler(): Long {
 *     val counter = virtualObject<Counter>("my-counter")
 *     return counter.increment()
 * }
 * ```
 *
 * @param SVC the virtual object class annotated with @VirtualObject
 * @param key the key identifying the specific virtual object instance
 * @return a proxy client to invoke the virtual object
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend inline fun <reified SVC : Any> virtualObject(key: String): SVC {
  return virtualObject(SVC::class.java, key)
}

/**
 * Create a proxy client for a Restate workflow.
 *
 * @param SVC the workflow class annotated with @Workflow
 * @param key the key identifying the specific workflow instance
 * @return a proxy client to invoke the workflow
 */
@org.jetbrains.annotations.ApiStatus.Experimental
suspend inline fun <reified SVC : Any> workflow(key: String): SVC {
  return workflow(SVC::class.java, key)
}

@PublishedApi
internal fun <SVC : Any> service(clazz: Class<SVC>): SVC {
  ReflectionUtils.mustHaveServiceAnnotation(clazz)
  require(ReflectionUtils.isKotlinClass(clazz)) {
    "Using Java classes with Kotlin's API is not supported"
  }
  val serviceName = ReflectionUtils.extractServiceName(clazz)

  return ProxySupport.createProxy(clazz) { invocation ->
    val request = invocation.captureInvocation(serviceName, null).toRequest()

    // Last argument is the continuation for suspend functions
    @Suppress("UNCHECKED_CAST") val continuation = invocation.arguments.last() as Continuation<Any?>

    // Start a coroutine that calls the client and resumes the continuation
    val suspendBlock: suspend () -> Any? = { context().call(request).await() }
    suspendBlock.startCoroutine(continuation)
    COROUTINE_SUSPENDED
  }
}

@PublishedApi
internal fun <SVC : Any> virtualObject(clazz: Class<SVC>, key: String): SVC {
  ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz)
  require(ReflectionUtils.isKotlinClass(clazz)) {
    "Using Java classes with Kotlin's API is not supported"
  }
  val serviceName = ReflectionUtils.extractServiceName(clazz)

  return ProxySupport.createProxy(clazz) { invocation ->
    val request = invocation.captureInvocation(serviceName, key).toRequest()

    // Last argument is the continuation for suspend functions
    @Suppress("UNCHECKED_CAST") val continuation = invocation.arguments.last() as Continuation<Any?>

    // Start a coroutine that calls the client and resumes the continuation
    val suspendBlock: suspend () -> Any? = { context().call(request).await() }
    suspendBlock.startCoroutine(continuation)
    COROUTINE_SUSPENDED
  }
}

@PublishedApi
internal fun <SVC : Any> workflow(clazz: Class<SVC>, key: String): SVC {
  ReflectionUtils.mustHaveWorkflowAnnotation(clazz)
  require(ReflectionUtils.isKotlinClass(clazz)) {
    "Using Java classes with Kotlin's API is not supported"
  }
  val serviceName = ReflectionUtils.extractServiceName(clazz)

  return ProxySupport.createProxy(clazz) { invocation ->
    val request = invocation.captureInvocation(serviceName, key).toRequest()

    // Last argument is the continuation for suspend functions
    @Suppress("UNCHECKED_CAST") val continuation = invocation.arguments.last() as Continuation<Any?>

    // Start a coroutine that calls the client and resumes the continuation
    val suspendBlock: suspend () -> Any? = { context().call(request).await() }
    suspendBlock.startCoroutine(continuation)
    COROUTINE_SUSPENDED
  }
}
