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
import dev.restate.sdk.common.syscalls.SyscallCallback
import dev.restate.sdk.common.syscalls.Syscalls
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException

internal fun <T> completingContinuation(cont: CancellableContinuation<T>): SyscallCallback<T> {
  return SyscallCallback.of(cont::resume) {
    cont.cancel(CancellationException("Restate internal error", it))
  }
}

internal fun completingUnitContinuation(
    cont: CancellableContinuation<Unit>
): SyscallCallback<Void> {
  return SyscallCallback.of(
      { cont.resume(Unit) }, { cont.cancel(CancellationException("Restate internal error", it)) })
}

internal fun <T : Any?> Serde<T>.serializeWrappingException(
    syscalls: Syscalls,
    value: T?
): ByteBuffer {
  return try {
    this.serializeToByteBuffer(value)
  } catch (e: Exception) {
    syscalls.fail(e)
    throw CancellationException("Failed serialization", e)
  }
}

internal fun <T : Any?> Serde<T>.deserializeWrappingException(
    syscalls: Syscalls,
    ByteBuffer: ByteBuffer
): T {
  return try {
    this.deserialize(ByteBuffer)
  } catch (e: Exception) {
    syscalls.fail(e)
    throw CancellationException("Failed deserialization", e)
  }
}
