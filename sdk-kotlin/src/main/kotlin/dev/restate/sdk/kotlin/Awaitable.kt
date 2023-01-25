package dev.restate.sdk.kotlin

import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.Syscalls
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface Awaitable<T> {
  suspend fun await(): T
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

internal class NonNullAwaitableImpl<T>
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
