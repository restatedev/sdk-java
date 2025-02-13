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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch

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
    return (Awaitable.any(
            this,
            SingleAwaitableImpl(asyncResult().ctx().timer(duration.toJavaDuration(), null).await()))
            as BaseAwaitableImpl<*>)
        .simpleMap {
          if (it == 1) {
            throw TimeoutException("Timed out waiting for awaitable after $duration")
          }

          try {
            @Suppress("UNCHECKED_CAST") return@simpleMap this.asyncResult().poll().getNow(null) as T
          } catch (e: ExecutionException) {
            throw e.cause ?: e // unwrap original cause from ExecutionException
          }
        }
  }

  fun <R> simpleMap(transform: (T) -> R): Awaitable<R> {
    return SingleAwaitableImpl(
        this.asyncResult().map { CompletableFuture.completedFuture(transform(it)) })
  }

  override suspend fun <R> map(transform: suspend (T) -> R): Awaitable<R> {
    var ctx = currentCoroutineContext()
    return SingleAwaitableImpl(
        this.asyncResult().map { t ->
          val completableFuture = CompletableFuture<R>()
          CoroutineScope(ctx).launch {
            val r: R
            try {
              r = transform(t)
            } catch (throwable: Throwable) {
              completableFuture.completeExceptionally(throwable)
              return@launch
            }
            completableFuture.complete(r)
          }
          completableFuture
        })
  }

  override suspend fun <R> map(
    transformSuccess: suspend (T) -> R,
    transformFailure: suspend (TerminalException) -> R
  ): Awaitable<R> {
    var ctx = currentCoroutineContext()
    return SingleAwaitableImpl(
      this.asyncResult().map({ t ->
        val completableFuture = CompletableFuture<R>()
        CoroutineScope(ctx).launch {
          val r: R
          try {
            r = transformSuccess(t)
          } catch (throwable: Throwable) {
            completableFuture.completeExceptionally(throwable)
            return@launch
          }
          completableFuture.complete(r)
        }
        completableFuture
      }, {t ->
        val completableFuture = CompletableFuture<R>()
        CoroutineScope(ctx).launch {
          val r: R
          try {
            r = transformFailure(t)
          } catch (throwable: Throwable) {
            completableFuture.completeExceptionally(throwable)
            return@launch
          }
          completableFuture.complete(r)
        }
        completableFuture
      }))
  }

  override suspend fun mapFailure(transform: suspend (TerminalException) -> T): Awaitable<T> {
    var ctx = currentCoroutineContext()
    return SingleAwaitableImpl(
      this.asyncResult().mapFailure{t ->
        val completableFuture = CompletableFuture<T>()
        CoroutineScope(ctx).launch {
          val newT: T
          try {
            newT = transform(t)
          } catch (throwable: Throwable) {
            completableFuture.completeExceptionally(throwable)
            return@launch
          }
          completableFuture.complete(newT)
        }
        completableFuture
      })
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
      .simpleMap {}
}

internal fun wrapAnyAwaitable(awaitables: List<Awaitable<*>>): BaseAwaitableImpl<Int> {
  val ctx = (awaitables.get(0) as BaseAwaitableImpl<*>).asyncResult().ctx()
  return SingleAwaitableImpl(
      ctx.createAnyAsyncResult(awaitables.map { (it as BaseAwaitableImpl<*>).asyncResult() }))
}

internal class AwakeableImpl<T : Any?>
internal constructor(asyncResult: AsyncResult<Slice>, serde: Serde<T>, override val id: String) :
    SingleAwaitableImpl<T>(
        asyncResult.map { CompletableFuture.completedFuture(serde.deserialize(it)) }),
    Awakeable<T>

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

  private val clauses: MutableList<Pair<BaseAwaitableImpl<*>, suspend (Any?) -> R>> =
      mutableListOf()

  @Suppress("UNCHECKED_CAST")
  override fun <T> SelectClause<T>.invoke(block: suspend (T) -> R) {
    clauses.add(this.awaitable as BaseAwaitableImpl<*> to block as suspend (Any?) -> R)
  }

  suspend fun build(): Awaitable<R> {
    return wrapAnyAwaitable(clauses.map { it.first }).map { index ->
      clauses[index].let { resolved -> resolved.first.await().let { resolved.second(it) } }
    }
  }
}
