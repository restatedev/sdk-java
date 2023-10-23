package dev.restate.sdk.kotlin

import dev.restate.sdk.core.TypeTag
import dev.restate.sdk.core.syscalls.AnyDeferredResult
import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.Syscalls
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

internal abstract class BaseAwaitableImpl<JAVA_T, KT_T>
internal constructor(
    internal val syscalls: Syscalls,
    internal val deferredResult: DeferredResult<JAVA_T>
) : Awaitable<KT_T> {

  abstract fun unpack(): KT_T

  override val onAwait: SelectClause<KT_T>
    get() = SelectClauseImpl(this)

  override suspend fun await(): KT_T {
    if (!deferredResult.isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        syscalls.resolveDeferred(deferredResult, completingUnitContinuation(cont))
      }
    }
    return unpack()
  }
}

internal open class NonNullAwaitableImpl<T>
internal constructor(syscalls: Syscalls, deferredResult: DeferredResult<T>) :
    BaseAwaitableImpl<T, T>(syscalls, deferredResult) {
  override fun unpack(): T {
    val readyResult = deferredResult.toReadyResult()!!
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    return readyResult.result!!
  }
}

internal class UnitAwaitableImpl
internal constructor(syscalls: Syscalls, deferredResult: DeferredResult<Void>) :
    BaseAwaitableImpl<Void, Unit>(syscalls, deferredResult) {
  override fun unpack() {
    val readyResult = deferredResult.toReadyResult()!!
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    return
  }
}

internal class AnyAwaitableImpl
internal constructor(syscalls: Syscalls, deferredResult: DeferredResult<Any>) :
    BaseAwaitableImpl<Any, Any>(syscalls, deferredResult), AnyAwaitable {
  override fun unpack(): Any {
    val readyResult = deferredResult.toReadyResult()!!
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    return readyResult.result!!
  }

  override suspend fun awaitIndex(): Int {
    if (!deferredResult.isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        syscalls.resolveDeferred(deferredResult, completingUnitContinuation(cont))
      }
    }

    return (deferredResult as AnyDeferredResult).completedIndex().orElseThrow {
      IllegalStateException(
          "completedIndex is empty when expecting a value. This looks like an SDK bug.")
    }
  }
}

internal fun wrapAllAwaitable(awaitables: List<Awaitable<*>>): Awaitable<Unit> {
  val syscalls = (awaitables.get(0) as BaseAwaitableImpl<*, *>).syscalls
  return UnitAwaitableImpl(
      syscalls,
      syscalls.createAllDeferred(awaitables.map { (it as BaseAwaitableImpl<*, *>).deferredResult }))
}

internal fun wrapAnyAwaitable(awaitables: List<Awaitable<*>>): AnyAwaitable {
  val syscalls = (awaitables.get(0) as BaseAwaitableImpl<*, *>).syscalls
  return AnyAwaitableImpl(
      syscalls,
      syscalls.createAnyDeferred(awaitables.map { (it as BaseAwaitableImpl<*, *>).deferredResult }))
}

internal class AwakeableImpl<T>
internal constructor(
    syscalls: Syscalls,
    deferredResult: DeferredResult<T>,
    override val id: String
) : NonNullAwaitableImpl<T>(syscalls, deferredResult), Awakeable<T>

internal class AwakeableHandleImpl(val syscalls: Syscalls, val id: String) : AwakeableHandle {
  override suspend fun <T : Any> resolve(typeTag: TypeTag<T>, payload: T) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.resolveAwakeable(id, typeTag, payload, completingUnitContinuation(cont))
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
