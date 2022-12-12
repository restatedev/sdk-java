package dev.restate.sdk.kotlin

import dev.restate.sdk.core.syscalls.DeferredResultCallback
import dev.restate.sdk.core.syscalls.SyscallCallback
import java.util.*
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

internal fun <T> completingDeferred(deferred: CompletableDeferred<T>): DeferredResultCallback<T> {
  return DeferredResultCallback.ofNonEmpty(
      deferred::complete,
      deferred::completeExceptionally,
      { deferred.cancel(CancellationException("Suspended", it)) })
}

internal fun <T> completingOptionalDeferred(
    deferred: CompletableDeferred<T?>
): DeferredResultCallback<T?> {
  return DeferredResultCallback.of(
      { deferred.complete(null) },
      deferred::complete,
      deferred::completeExceptionally,
      { deferred.cancel(CancellationException("Suspended", it)) })
}

internal fun completingUnitDeferred(
    deferred: CompletableDeferred<Unit>
): DeferredResultCallback<Void> {
  return DeferredResultCallback.of(
      { deferred.complete(Unit) },
      { deferred.complete(Unit) },
      deferred::completeExceptionally,
      { deferred.cancel(CancellationException("Suspended", it)) })
}

internal fun <T> completingContinuation(cont: CancellableContinuation<T>): SyscallCallback<T> {
  return SyscallCallback.of(cont::resume, cont::cancel)
}

internal fun completingUnitContinuation(
    cont: CancellableContinuation<Unit>
): SyscallCallback<Void> {
  return SyscallCallback.of({ cont.resume(Unit) }, { cont.cancel(it) })
}
