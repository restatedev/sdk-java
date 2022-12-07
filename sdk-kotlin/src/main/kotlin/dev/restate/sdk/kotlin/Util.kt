package dev.restate.sdk.kotlin

import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.ReadyResult
import dev.restate.sdk.core.syscalls.Syscalls
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T> resolveDeferred(
    syscalls: Syscalls,
    deferredResult: DeferredResult<T>
): ReadyResult<T> {
  return suspendCancellableCoroutine {
    syscalls.resolveDeferred(
        deferredResult,
        { value: ReadyResult<T> -> it.resume(value) },
        { ex: Throwable -> it.resumeWithException(ex) })
  }
}

internal fun <T> unpackNullableReady(readyResult: ReadyResult<T>): T? {
  if (!readyResult.isOk) {
    throw readyResult.failure!!
  }
  return readyResult.result
}
