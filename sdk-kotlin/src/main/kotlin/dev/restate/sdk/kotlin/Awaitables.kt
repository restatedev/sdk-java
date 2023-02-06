package dev.restate.sdk.kotlin

import dev.restate.generated.core.AwakeableIdentifier
import dev.restate.sdk.core.TypeTag
import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.Syscalls
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface Awaitable<T> {
  suspend fun await(): T
}

sealed interface Awakeable<T> : Awaitable<T> {
  val id: AwakeableIdentifier
}

internal abstract class BaseAwaitableImpl<JAVA_T, KT_T>
internal constructor(
    private val syscalls: Syscalls,
    protected val deferredResult: DeferredResult<JAVA_T>
) : Awaitable<KT_T> {

  abstract fun unpack(): KT_T

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

internal class AwakeableImpl<T>
internal constructor(
    syscalls: Syscalls,
    deferredResult: DeferredResult<T>,
    override val id: AwakeableIdentifier
) : NonNullAwaitableImpl<T>(syscalls, deferredResult), Awakeable<T>

internal class AwakeableHandleImpl(val syscalls: Syscalls, val id: AwakeableIdentifier) :
    AwakeableHandle {
  override suspend fun <T : Any> complete(typeTag: TypeTag<T>, payload: T) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.completeAwakeable(id, typeTag, payload, completingUnitContinuation(cont))
    }
  }
}
