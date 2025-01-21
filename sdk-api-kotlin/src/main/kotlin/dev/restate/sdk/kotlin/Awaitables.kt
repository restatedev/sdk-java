// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.serde.Serde
import dev.restate.sdk.endpoint.AsyncResult
import dev.restate.sdk.endpoint.HandlerContext
import dev.restate.sdk.endpoint.Result
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

internal abstract class BaseAwaitableImpl<T : Any?>
internal constructor(internal val handlerContext: HandlerContext) : Awaitable<T> {
  abstract fun deferred(): AsyncResult<*>

  abstract suspend fun awaitResult(): Result<T>

  override val onAwait: SelectClause<T>
    get() = SelectClauseImpl(this)

  override suspend fun await(): T {
    val res = awaitResult()
    if (!res.isSuccess) {
      throw res.failure!!
    }
    @Suppress("UNCHECKED_CAST") return res.value as T
  }
}

internal class SingleAwaitableImpl<T : Any?>(
  handlerContext: HandlerContext,
  private val asyncResult: AsyncResult<T>
) : BaseAwaitableImpl<T>(handlerContext) {
  private var result: Result<T>? = null

  override fun deferred(): AsyncResult<*> {
    return asyncResult
  }

  override suspend fun awaitResult(): Result<T> {
    if (!deferred().isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        handlerContext.resolveDeferred(deferred(), completingUnitContinuation(cont))
      }
    }
    if (this.result == null) {
      this.result = asyncResult.toResult()
    }
    return this.result!!
  }
}

internal abstract class BaseSingleMappedAwaitableImpl<T : Any?, U : Any?>(
    private val inner: BaseAwaitableImpl<T>
) : BaseAwaitableImpl<U>(inner.handlerContext) {
  private var mappedResult: Result<U>? = null

  override fun deferred(): AsyncResult<*> {
    return inner.deferred()
  }

  abstract suspend fun map(res: Result<T>): Result<U>

  override suspend fun awaitResult(): Result<U> {
    if (mappedResult == null) {
      this.mappedResult = map(inner.awaitResult())
    }
    return mappedResult!!
  }
}

internal open class SingleSerdeAwaitableImpl<T : Any?>
internal constructor(
  handlerContext: HandlerContext,
  asyncResult: AsyncResult<ByteBuffer>,
  private val serde: Serde<T>,
) :
    BaseSingleMappedAwaitableImpl<ByteBuffer, T>(
        SingleAwaitableImpl(handlerContext, asyncResult),
    ) {
  @Suppress("UNCHECKED_CAST")
  override suspend fun map(res: Result<ByteBuffer>): Result<T> {
    return if (res.isSuccess) {
      // This propagates exceptions as non-terminal
      Result.success(serde.deserializeWrappingException(handlerContext, res.value!!))
    } else {
      res as Result<T>
    }
  }
}

internal class UnitAwakeableImpl(handlerContext: HandlerContext, asyncResult: AsyncResult<Void>) :
    BaseSingleMappedAwaitableImpl<Void, Unit>(SingleAwaitableImpl(handlerContext, asyncResult)) {
  @Suppress("UNCHECKED_CAST")
  override suspend fun map(res: Result<Void>): Result<Unit> {
    return if (res.isSuccess) {
      Result.success(Unit)
    } else {
      res as Result<Unit>
    }
  }
}

internal class AnyAwaitableImpl
internal constructor(handlerContext: HandlerContext, private val awaitables: List<Awaitable<*>>) :
    BaseSingleMappedAwaitableImpl<Int, Any>(
        SingleAwaitableImpl<Int>(
            handlerContext,
            handlerContext.createAnyDeferred(
                awaitables.map { (it as BaseAwaitableImpl<*>).deferred() }))),
    AnyAwaitable {

  override suspend fun awaitIndex(): Int {
    if (!deferred().isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        handlerContext.resolveDeferred(deferred(), completingUnitContinuation(cont))
      }
    }

    return deferred().toResult()!!.value as Int
  }

  @Suppress("UNCHECKED_CAST")
  override suspend fun map(res: Result<Int>): Result<Any> {
    return if (res.isSuccess)
        ((awaitables[res.value!!] as BaseAwaitableImpl<*>).awaitResult() as Result<Any>)
    else (res as Result<Any>)
  }
}

internal fun wrapAllAwaitable(awaitables: List<Awaitable<*>>): Awaitable<Unit> {
  val syscalls = (awaitables.get(0) as BaseAwaitableImpl<*>).handlerContext
  return UnitAwakeableImpl(
      syscalls,
      syscalls.createAllDeferred(awaitables.map { (it as BaseAwaitableImpl<*>).deferred() }),
  )
}

internal fun wrapAnyAwaitable(awaitables: List<Awaitable<*>>): AnyAwaitable {
  val syscalls = (awaitables.get(0) as BaseAwaitableImpl<*>).handlerContext
  return AnyAwaitableImpl(syscalls, awaitables)
}

internal class AwakeableImpl<T : Any>
internal constructor(
  handlerContext: HandlerContext,
  asyncResult: AsyncResult<ByteBuffer>,
  serde: Serde<T>,
  override val id: String
) : SingleSerdeAwaitableImpl<T>(handlerContext, asyncResult, serde), Awakeable<T> {}

internal class AwakeableHandleImpl(val handlerContext: HandlerContext, val id: String) : AwakeableHandle {
  override suspend fun <T : Any> resolve(serde: Serde<T>, payload: T) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      handlerContext.resolveAwakeable(
          id, serde.serializeWrappingException(handlerContext, payload), completingUnitContinuation(cont))
    }
  }

  override suspend fun reject(reason: String) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      handlerContext.rejectAwakeable(id, reason, completingUnitContinuation(cont))
    }
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
    val index = wrapAnyAwaitable(clauses.map { it.first }).awaitIndex()
    val resolved = clauses[index]
    return resolved.first.await().let { resolved.second(it) }
  }
}
