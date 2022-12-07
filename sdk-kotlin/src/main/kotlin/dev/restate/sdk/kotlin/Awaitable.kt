package dev.restate.sdk.kotlin

import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.ReadyResult
import dev.restate.sdk.core.syscalls.Syscalls

sealed interface Awaitable<T> {
  suspend fun await(): T
}

internal abstract class BaseAwaitableImpl<JAVA_T, KT_T>
internal constructor(
    private val syscalls: Syscalls,
    private var deferredResult: DeferredResult<JAVA_T>
) : Awaitable<KT_T> {

  abstract fun unpackReady(readyResult: ReadyResult<JAVA_T>): KT_T

  override suspend fun await(): KT_T {
    val readyResult: ReadyResult<JAVA_T> =
        if (deferredResult is ReadyResult<*>) deferredResult as ReadyResult<JAVA_T>
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
