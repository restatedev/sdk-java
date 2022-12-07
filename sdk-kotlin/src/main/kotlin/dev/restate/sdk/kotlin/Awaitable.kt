package dev.restate.sdk.kotlin

import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.ReadyResult
import dev.restate.sdk.core.syscalls.Syscalls

sealed interface Awaitable<T> {
  suspend fun await(): T
}

internal abstract class BaseAwaitableImpl<JT, RT>
internal constructor(
    private val syscalls: Syscalls,
    private var deferredResult: DeferredResult<JT>
) : Awaitable<RT> {

  abstract fun unpackReady(readyResult: ReadyResult<JT>): RT

  override suspend fun await(): RT {
    val readyResult: ReadyResult<JT> =
        if (deferredResult is ReadyResult<*>) deferredResult as ReadyResult<JT>
        else resolveDeferred(syscalls, deferredResult)

    // Make sure we store it if the user accesses it again
    deferredResult = readyResult

    return unpackReady(readyResult)
  }
}

internal class NonNullAwaitableImpl<T>
internal constructor(syscalls: Syscalls, deferredResult: DeferredResult<T>) :
    BaseAwaitableImpl<T, T>(syscalls, deferredResult) {
  override fun unpackReady(readyResult: ReadyResult<T>): T {
    if (!readyResult.isOk) {
      throw readyResult.failure!!
    }
    return readyResult.result!!
  }
}

internal class UnitAwaitableImpl
internal constructor(syscalls: Syscalls, deferredResult: DeferredResult<Void>) :
    BaseAwaitableImpl<Void, Unit>(syscalls, deferredResult) {
  override fun unpackReady(readyResult: ReadyResult<Void>) {
    if (!readyResult.isOk) {
      throw readyResult.failure!!
    }
    return
  }
}
