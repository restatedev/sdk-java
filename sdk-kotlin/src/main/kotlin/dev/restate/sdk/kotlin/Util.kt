package dev.restate.sdk.kotlin

import dev.restate.sdk.core.syscalls.SyscallCallback
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation

internal fun <T> completingContinuation(cont: CancellableContinuation<T>): SyscallCallback<T> {
  return SyscallCallback.of(cont::resume, cont::cancel)
}

internal fun completingUnitContinuation(
    cont: CancellableContinuation<Unit>
): SyscallCallback<Void> {
  return SyscallCallback.of({ cont.resume(Unit) }, { cont.cancel(it) })
}
