// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.Serde
import dev.restate.sdk.common.syscalls.Deferred
import dev.restate.sdk.common.syscalls.Result
import dev.restate.sdk.common.syscalls.Syscalls
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

internal abstract class BaseAwaitableImpl<T : Any?>
internal constructor(internal val syscalls: Syscalls) : Awaitable<T> {
  abstract fun deferred(): Deferred<*>

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
    syscalls: Syscalls,
    private val deferred: Deferred<T>
) : BaseAwaitableImpl<T>(syscalls) {
  private var result: Result<T>? = null

  override fun deferred(): Deferred<*> {
    return deferred
  }

  override suspend fun awaitResult(): Result<T> {
    if (!deferred().isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        syscalls.resolveDeferred(deferred(), completingUnitContinuation(cont))
      }
    }
    if (this.result == null) {
      this.result = deferred.toResult()
    }
    return this.result!!
  }
}

internal abstract class BaseSingleMappedAwaitableImpl<T : Any, U : Any>(
    private val inner: BaseAwaitableImpl<T>
) : BaseAwaitableImpl<U>(inner.syscalls) {
  private var mappedResult: Result<U>? = null

  override fun deferred(): Deferred<*> {
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

internal open class SingleSerdeAwaitableImpl<T : Any>
internal constructor(
    syscalls: Syscalls,
    deferred: Deferred<ByteBuffer>,
    private val serde: Serde<T>,
) :
    BaseSingleMappedAwaitableImpl<ByteBuffer, T>(
        SingleAwaitableImpl(syscalls, deferred),
    ) {
  @Suppress("UNCHECKED_CAST")
  override suspend fun map(res: Result<ByteBuffer>): Result<T> {
    return if (res.isSuccess) {
      // This propagates exceptions as non-terminal
      Result.success(serde.deserializeWrappingException(syscalls, res.value!!))
    } else {
      res as Result<T>
    }
  }
}

internal class UnitAwakeableImpl(syscalls: Syscalls, deferred: Deferred<Void>) :
    BaseSingleMappedAwaitableImpl<Void, Unit>(SingleAwaitableImpl(syscalls, deferred)) {
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
internal constructor(syscalls: Syscalls, private val awaitables: List<Awaitable<*>>) :
    BaseSingleMappedAwaitableImpl<Int, Any>(
        SingleAwaitableImpl<Int>(
            syscalls,
            syscalls.createAnyDeferred(
                awaitables.map { (it as BaseAwaitableImpl<*>).deferred() }))),
    AnyAwaitable {

  override suspend fun awaitIndex(): Int {
    if (!deferred().isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        syscalls.resolveDeferred(deferred(), completingUnitContinuation(cont))
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
  val syscalls = (awaitables.get(0) as BaseAwaitableImpl<*>).syscalls
  return UnitAwakeableImpl(
      syscalls,
      syscalls.createAllDeferred(awaitables.map { (it as BaseAwaitableImpl<*>).deferred() }),
  )
}

internal fun wrapAnyAwaitable(awaitables: List<Awaitable<*>>): AnyAwaitable {
  val syscalls = (awaitables.get(0) as BaseAwaitableImpl<*>).syscalls
  return AnyAwaitableImpl(syscalls, awaitables)
}

internal class AwakeableImpl<T : Any>
internal constructor(
    syscalls: Syscalls,
    deferred: Deferred<ByteBuffer>,
    serde: Serde<T>,
    override val id: String
) : SingleSerdeAwaitableImpl<T>(syscalls, deferred, serde), Awakeable<T> {}

internal class AwakeableHandleImpl(val syscalls: Syscalls, val id: String) : AwakeableHandle {
  override suspend fun <T : Any> resolve(serde: Serde<T>, payload: T) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.resolveAwakeable(
          id, serde.serializeWrappingException(syscalls, payload), completingUnitContinuation(cont))
    }
  }

  override suspend fun reject(reason: String) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.rejectAwakeable(id, reason, completingUnitContinuation(cont))
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
