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
import dev.restate.sdk.common.InvocationId
import dev.restate.sdk.common.Serde
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.common.syscalls.Deferred
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback
import dev.restate.sdk.common.syscalls.Syscalls
import io.grpc.MethodDescriptor
import java.lang.Error
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*

internal class ContextImpl internal constructor(private val syscalls: Syscalls) : KeyedContext {
  override suspend fun <T : Any> get(key: StateKey<T>): T? {
    val deferred: Deferred<ByteString> =
        suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<ByteString>> ->
          syscalls.get(key.name(), completingContinuation(cont))
        }

    if (!deferred.isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        syscalls.resolveDeferred(deferred, completingUnitContinuation(cont))
      }
    }

    val readyResult = deferred.toResult()!!
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    if (readyResult.isEmpty) {
      return null
    }
    return key.serde().deserializeWrappingException(syscalls, readyResult.value!!)!!
  }

  override suspend fun <T : Any> set(key: StateKey<T>, value: T) {
    val serializedValue = key.serde().serializeWrappingException(syscalls, value)!!
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.set(key.name(), serializedValue, completingUnitContinuation(cont))
    }
  }

  override suspend fun clear(key: StateKey<*>) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.clear(key.name(), completingUnitContinuation(cont))
    }
  }

  override suspend fun clearAll() {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.clearAll(completingUnitContinuation(cont))
    }
  }

  override suspend fun timer(duration: Duration): Awaitable<Unit> {
    val deferred: Deferred<Void> =
        suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<Void>> ->
          syscalls.sleep(duration.toJavaDuration(), completingContinuation(cont))
        }

    return UnitAwakeableImpl(syscalls, deferred)
  }

  override suspend fun <T, R> callAsync(
      methodDescriptor: MethodDescriptor<T, R>,
      parameter: T
  ): Awaitable<R> {
    val deferred: Deferred<R> =
        suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<R>> ->
          syscalls.call(methodDescriptor, parameter, completingContinuation(cont))
        }

    return SingleAwaitableImpl(syscalls, deferred)
  }

  override suspend fun <T, R> oneWayCall(methodDescriptor: MethodDescriptor<T, R>, parameter: T) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.backgroundCall(methodDescriptor, parameter, null, completingUnitContinuation(cont))
    }
  }

  override suspend fun <T, R> delayedCall(
      methodDescriptor: MethodDescriptor<T, R>,
      parameter: T,
      delay: Duration
  ) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.backgroundCall(
          methodDescriptor, parameter, delay.toJavaDuration(), completingUnitContinuation(cont))
    }
  }

  override suspend fun <T : Any?> sideEffect(
      serde: Serde<T>,
      sideEffectAction: suspend () -> T
  ): T {
    val exitResult =
        suspendCancellableCoroutine { cont: CancellableContinuation<CompletableDeferred<ByteString>>
          ->
          syscalls.enterSideEffectBlock(
              object : EnterSideEffectSyscallCallback {
                override fun onSuccess(t: ByteString?) {
                  val deferred: CompletableDeferred<ByteString> = CompletableDeferred()
                  deferred.complete(t!!)
                  cont.resume(deferred)
                }

                override fun onFailure(t: TerminalException) {
                  val deferred: CompletableDeferred<ByteString> = CompletableDeferred()
                  deferred.completeExceptionally(t)
                  cont.resume(deferred)
                }

                override fun onCancel(t: Throwable?) {
                  cont.cancel(t)
                }

                override fun onNotExecuted() {
                  cont.resume(CompletableDeferred())
                }
              })
        }

    if (exitResult.isCompleted) {
      return serde.deserializeWrappingException(syscalls, exitResult.await())!!
    }

    var actionReturnValue: T? = null
    var actionFailure: TerminalException? = null
    try {
      actionReturnValue = sideEffectAction()
    } catch (e: TerminalException) {
      actionFailure = e
    } catch (e: Error) {
      throw e
    } catch (t: Throwable) {
      syscalls.fail(t)
      throw CancellationException("Side effect failure", t)
    }

    val exitCallback =
        object : ExitSideEffectSyscallCallback {
          override fun onSuccess(t: ByteString?) {
            exitResult.complete(t!!)
          }

          override fun onFailure(t: TerminalException) {
            exitResult.completeExceptionally(t)
          }

          override fun onCancel(t: Throwable?) {
            exitResult.cancel(CancellationException("Suspended", t))
          }
        }

    if (actionFailure != null) {
      syscalls.exitSideEffectBlockWithTerminalException(actionFailure, exitCallback)
    } else {
      syscalls.exitSideEffectBlock(
          serde.serializeWrappingException(syscalls, actionReturnValue), exitCallback)
    }

    return serde.deserializeWrappingException(syscalls, exitResult.await())
  }

  override suspend fun <T : Any> awakeable(serde: Serde<T>): Awakeable<T> {
    val (aid, deferredResult) =
        suspendCancellableCoroutine {
            cont: CancellableContinuation<Map.Entry<String, Deferred<ByteString>>> ->
          syscalls.awakeable(completingContinuation(cont))
        }

    return AwakeableImpl(syscalls, deferredResult, serde, aid)
  }

  override fun awakeableHandle(id: String): AwakeableHandle {
    return AwakeableHandleImpl(syscalls, id)
  }

  override fun random(): RestateRandom {
    return RestateRandom(InvocationId.current().toRandomSeed(), syscalls)
  }
}
