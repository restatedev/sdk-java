// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import com.google.protobuf.ByteString
import dev.restate.sdk.core.Serde
import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.ReadyResultHolder
import dev.restate.sdk.core.syscalls.Syscalls
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

internal abstract class BaseAwaitableImpl<JAVA_T, KT_T>
internal constructor(
    internal val syscalls: Syscalls,
    internal val resultHolder: ReadyResultHolder<JAVA_T>
) : Awaitable<KT_T> {

  abstract fun unpack(): KT_T

  override val onAwait: SelectClause<KT_T>
    get() = SelectClauseImpl(this)

  override suspend fun await(): KT_T {
    if (!resultHolder.isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        syscalls.resolveDeferred(resultHolder.deferredResult, completingUnitContinuation(cont))
      }
    }
    return unpack()
  }
}

internal open class NonNullAwaitableImpl<T>
internal constructor(syscalls: Syscalls, resultHolder: ReadyResultHolder<T>) :
    BaseAwaitableImpl<T, T>(syscalls, resultHolder) {

  companion object {
    fun <T> of(syscalls: Syscalls, deferredResult: DeferredResult<T>): NonNullAwaitableImpl<T> {
      return NonNullAwaitableImpl(syscalls, ReadyResultHolder(deferredResult))
    }
  }

  override fun unpack(): T {
    val readyResult = resultHolder.readyResult
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    return readyResult.result!!
  }
}

internal class UnitAwaitableImpl
internal constructor(syscalls: Syscalls, deferredResult: DeferredResult<Void>) :
    BaseAwaitableImpl<Void, Unit>(syscalls, ReadyResultHolder(deferredResult)) {
  override fun unpack() {
    val readyResult = resultHolder.readyResult
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    return
  }
}

internal class AnyAwaitableImpl
internal constructor(syscalls: Syscalls, awaitables: List<Awaitable<*>>) :
    BaseAwaitableImpl<Any, Any>(
        syscalls,
        ReadyResultHolder(
            syscalls.createAnyDeferred(
                awaitables.map { (it as BaseAwaitableImpl<*, *>).resultHolder.deferredResult })) {
              (awaitables[it] as BaseAwaitableImpl<*, *>).unpack()
            },
    ),
    AnyAwaitable {
  override fun unpack(): Any {
    val readyResult = resultHolder.readyResult
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    return readyResult.result!!
  }

  override suspend fun awaitIndex(): Int {
    if (!resultHolder.isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        syscalls.resolveDeferred(resultHolder.deferredResult, completingUnitContinuation(cont))
      }
    }

    return resultHolder.deferredResult.toReadyResult()!!.result as Int
  }
}

internal fun wrapAllAwaitable(awaitables: List<Awaitable<*>>): Awaitable<Unit> {
  val syscalls = (awaitables.get(0) as BaseAwaitableImpl<*, *>).syscalls
  return UnitAwaitableImpl(
      syscalls,
      syscalls.createAllDeferred(
          awaitables.map { (it as BaseAwaitableImpl<*, *>).resultHolder.deferredResult }))
}

internal fun wrapAnyAwaitable(awaitables: List<Awaitable<*>>): AnyAwaitable {
  val syscalls = (awaitables.get(0) as BaseAwaitableImpl<*, *>).syscalls
  return AnyAwaitableImpl(syscalls, awaitables)
}

internal class AwakeableImpl<T>
internal constructor(
    syscalls: Syscalls,
    deferredResult: DeferredResult<ByteString>,
    serde: Serde<T>,
    override val id: String
) :
    NonNullAwaitableImpl<T>(
        syscalls,
        ReadyResultHolder(deferredResult) { serde.deserializeWrappingException(syscalls, it) }),
    Awakeable<T>

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
