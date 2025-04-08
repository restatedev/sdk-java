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
import dev.restate.common.Slice
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.common.TimeoutException
import dev.restate.sdk.endpoint.definition.AsyncResult
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.serde.Serde
import dev.restate.serde.TypeTag
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch

internal abstract class BaseDurableFutureImpl<T : Any?> : DurableFuture<T> {
  abstract fun asyncResult(): AsyncResult<T>

  override val onAwait: SelectClause<T>
    get() = SelectClauseImpl(this)

  override suspend fun await(): T {
    return asyncResult().poll().await()
  }

  override suspend fun await(duration: Duration): T {
    return withTimeout(duration).await()
  }

  override suspend fun withTimeout(duration: Duration): DurableFuture<T> {
    return (DurableFuture.any(
            this,
            SingleDurableFutureImpl(
                asyncResult().ctx().timer(duration.toJavaDuration(), null).await()))
            as BaseDurableFutureImpl<*>)
        .simpleMap {
          if (it == 1) {
            throw TimeoutException("Timed out waiting for durable future after $duration")
          }

          try {
            @Suppress("UNCHECKED_CAST") return@simpleMap this.asyncResult().poll().getNow(null) as T
          } catch (e: ExecutionException) {
            throw e.cause ?: e // unwrap original cause from ExecutionException
          }
        }
  }

  fun <R> simpleMap(transform: (T) -> R): DurableFuture<R> {
    return SingleDurableFutureImpl(
        this.asyncResult().map { CompletableFuture.completedFuture(transform(it)) })
  }

  override suspend fun <R> map(transform: suspend (T) -> R): DurableFuture<R> {
    var ctx = currentCoroutineContext()
    return SingleDurableFutureImpl(
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
  ): DurableFuture<R> {
    var ctx = currentCoroutineContext()
    return SingleDurableFutureImpl(
        this.asyncResult()
            .map(
                { t ->
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
                },
                { t ->
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

  override suspend fun mapFailure(transform: suspend (TerminalException) -> T): DurableFuture<T> {
    var ctx = currentCoroutineContext()
    return SingleDurableFutureImpl(
        this.asyncResult().mapFailure { t ->
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

internal open class SingleDurableFutureImpl<T : Any?>(private val asyncResult: AsyncResult<T>) :
    BaseDurableFutureImpl<T>() {
  override fun asyncResult(): AsyncResult<T> {
    return asyncResult
  }
}

internal fun wrapAllDurableFuture(durableFutures: List<DurableFuture<*>>): DurableFuture<Unit> {
  check(durableFutures.isNotEmpty()) { "The durable futures list should be non empty" }
  val ctx = (durableFutures.get(0) as BaseDurableFutureImpl<*>).asyncResult().ctx()
  return SingleDurableFutureImpl(
          ctx.createAllAsyncResult(
              durableFutures.map { (it as BaseDurableFutureImpl<*>).asyncResult() }))
      .simpleMap {}
}

internal fun wrapAnyDurableFuture(
    durableFutures: List<DurableFuture<*>>
): BaseDurableFutureImpl<Int> {
  check(durableFutures.isNotEmpty()) { "The durable futures list should be non empty" }
  val ctx = (durableFutures.get(0) as BaseDurableFutureImpl<*>).asyncResult().ctx()
  return SingleDurableFutureImpl(
      ctx.createAnyAsyncResult(
          durableFutures.map { (it as BaseDurableFutureImpl<*>).asyncResult() }))
}

internal class CallDurableFutureImpl<T : Any?>
internal constructor(
    callAsyncResult: AsyncResult<T>,
    private val invocationIdAsyncResult: AsyncResult<String>
) : SingleDurableFutureImpl<T>(callAsyncResult), CallDurableFuture<T> {
  override suspend fun invocationId(): String {
    return invocationIdAsyncResult.poll().await()
  }
}

internal abstract class BaseInvocationHandle<Res>
internal constructor(
    private val handlerContext: HandlerContext,
    private val responseSerde: Serde<Res>
) : InvocationHandle<Res> {
  override suspend fun cancel() {
    val ignored = handlerContext.cancelInvocation(invocationId()).await()
  }

  override suspend fun attach(): DurableFuture<Res> =
      SingleDurableFutureImpl(
          handlerContext.attachInvocation(invocationId()).await().map {
            CompletableFuture.completedFuture<Res>(responseSerde.deserialize(it))
          })

  override suspend fun output(): Output<Res> =
      SingleDurableFutureImpl(handlerContext.getInvocationOutput(invocationId()).await())
          .simpleMap { it.map { responseSerde.deserialize(it) } }
          .await()
}

internal class AwakeableImpl<T : Any?>
internal constructor(asyncResult: AsyncResult<Slice>, serde: Serde<T>, override val id: String) :
    SingleDurableFutureImpl<T>(
        asyncResult.map { CompletableFuture.completedFuture(serde.deserialize(it)) }),
    Awakeable<T>

internal class AwakeableHandleImpl(val contextImpl: ContextImpl, val id: String) : AwakeableHandle {
  override suspend fun <T : Any> resolve(typeTag: TypeTag<T>, payload: T) {
    contextImpl.handlerContext
        .resolveAwakeable(id, contextImpl.resolveAndSerialize(typeTag, payload))
        .await()
  }

  override suspend fun reject(reason: String) {
    return
    contextImpl.handlerContext.rejectAwakeable(id, TerminalException(reason)).await()
  }
}

internal class SelectClauseImpl<T>(override val durableFuture: DurableFuture<T>) : SelectClause<T>

@PublishedApi
internal class SelectImplementation<R> : SelectBuilder<R> {

  private val clauses: MutableList<Pair<BaseDurableFutureImpl<*>, suspend (Any?) -> R>> =
      mutableListOf()

  @Suppress("UNCHECKED_CAST")
  override fun <T> SelectClause<T>.invoke(block: suspend (T) -> R) {
    clauses.add(this.durableFuture as BaseDurableFutureImpl<*> to block as suspend (Any?) -> R)
  }

  suspend fun build(): DurableFuture<R> {
    return wrapAnyDurableFuture(clauses.map { it.first }).map { index ->
      clauses[index].let { resolved -> resolved.first.await().let { resolved.second(it) } }
    }
  }
}
