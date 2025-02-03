// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.common.Slice
import dev.restate.sdk.endpoint.definition.AsyncResult
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.sdk.types.TerminalException
import dev.restate.sdk.types.TimeoutException
import dev.restate.serde.Serde
import java.util.concurrent.ExecutionException
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.future.await

internal abstract class BaseAwaitableImpl<T : Any?> : Awaitable<T> {
  abstract fun asyncResult(): AsyncResult<T>

  override val onAwait: SelectClause<T>
    get() = SelectClauseImpl(this)

  override suspend fun await(): T {
    return asyncResult().poll().await()
  }

  override suspend fun await(duration: Duration): T {
    return withTimeout(duration).await()
  }

  override suspend fun withTimeout(duration: Duration): Awaitable<T> {
    return Awaitable.any(
            this, SingleAwaitableImpl(asyncResult().ctx().sleep(duration.toJavaDuration()).await()))
        .map {
          if (it == 1) {
            throw TimeoutException("Timed out waiting for awaitable after $duration")
          }

          try {
            @Suppress("UNCHECKED_CAST") return@map this.asyncResult().poll() as T
          } catch (e: ExecutionException) {
            throw e.cause ?: e // unwrap original cause from ExecutionException
          }
        }
  }

  override fun <R> map(transform: (T) -> R): Awaitable<R> {
    return SingleAwaitableImpl(this.asyncResult().map(transform))
  }
}

internal open class SingleAwaitableImpl<T : Any?>(private val asyncResult: AsyncResult<T>) :
    BaseAwaitableImpl<T>() {
  override fun asyncResult(): AsyncResult<T> {
    return asyncResult
  }
}

internal fun wrapAllAwaitable(awaitables: List<Awaitable<*>>): Awaitable<Unit> {
  val ctx = (awaitables.get(0) as BaseAwaitableImpl<*>).asyncResult().ctx()
  return SingleAwaitableImpl(
          ctx.createAllAsyncResult(awaitables.map { (it as BaseAwaitableImpl<*>).asyncResult() }))
      .map {}
}

internal fun wrapAnyAwaitable(awaitables: List<Awaitable<*>>): Awaitable<Int> {
  val ctx = (awaitables.get(0) as BaseAwaitableImpl<*>).asyncResult().ctx()
  return SingleAwaitableImpl(
      ctx.createAnyAsyncResult(awaitables.map { (it as BaseAwaitableImpl<*>).asyncResult() }))
}

internal class AwakeableImpl<T : Any?>
internal constructor(asyncResult: AsyncResult<Slice>, serde: Serde<T>, override val id: String) :
    SingleAwaitableImpl<T>(asyncResult.map { serde.deserialize(it) }), Awakeable<T>

internal class AwakeableHandleImpl(val handlerContext: HandlerContext, val id: String) :
    AwakeableHandle {
  override suspend fun <T : Any> resolve(serde: Serde<T>, payload: T) {
    handlerContext
        .resolveAwakeable(id, serde.serializeWrappingException(handlerContext, payload))
        .await()
  }

  override suspend fun reject(reason: String) {
    return
    handlerContext.rejectAwakeable(id, TerminalException(reason)).await()
  }
}

internal class SelectClauseImpl<T>(override val awaitable: Awaitable<T>) : SelectClause<T>

@PublishedApi
internal class SelectImplementation<R> : SelectBuilder<R> {

  private val clauses: MutableList<Pair<Awaitable<*>, suspend (Any?) -> R>> = mutableListOf()

  @Suppress("UNCHECKED_CAST")
  override fun <T> SelectClause<T>.invoke(block: suspend (T) -> R) {
    clauses.add(this.awaitable as Awaitable<*> to block as suspend (Any?) -> R)
  }

  suspend fun doSelect(): R {
    val index = wrapAnyAwaitable(clauses.map { it.first }).await()
    val resolved = clauses[index]
    return resolved.first.await().let { resolved.second(it) }
  }
}
