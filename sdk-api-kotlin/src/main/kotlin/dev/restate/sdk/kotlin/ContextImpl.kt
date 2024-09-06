// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.*
import dev.restate.sdk.common.Target
import dev.restate.sdk.common.syscalls.Deferred
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback
import dev.restate.sdk.common.syscalls.Syscalls
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

internal class ContextImpl internal constructor(private val syscalls: Syscalls) : WorkflowContext {
  override fun key(): String {
    return this.syscalls.objectKey()
  }

  override fun request(): Request {
    return this.syscalls.request()
  }

  override suspend fun <T : Any> get(key: StateKey<T>): T? {
    val deferred: Deferred<ByteBuffer> =
        suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<ByteBuffer>> ->
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

  override suspend fun stateKeys(): Collection<String> {
    val deferred: Deferred<Collection<String>> =
        suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<Collection<String>>> ->
          syscalls.getKeys(completingContinuation(cont))
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
    return readyResult.value!!
  }

  override suspend fun <T : Any> set(key: StateKey<T>, value: T) {
    val serializedValue = key.serde().serializeWrappingException(syscalls, value)
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

  override suspend fun <T : Any?, R : Any?> callAsync(
      target: Target,
      inputSerde: Serde<T>,
      outputSerde: Serde<R>,
      parameter: T
  ): Awaitable<R> {
    val input = inputSerde.serializeWrappingException(syscalls, parameter)

    val deferred: Deferred<ByteBuffer> =
        suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<ByteBuffer>> ->
          syscalls.call(target, input, completingContinuation(cont))
        }

    return SingleSerdeAwaitableImpl(syscalls, deferred, outputSerde)
  }

  override suspend fun <T : Any?> send(
      target: Target,
      inputSerde: Serde<T>,
      parameter: T,
      delay: Duration
  ) {
    val input = inputSerde.serializeWrappingException(syscalls, parameter)

    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.send(target, input, delay.toJavaDuration(), completingUnitContinuation(cont))
    }
  }

  override suspend fun <T : Any?> runBlock(
      serde: Serde<T>,
      name: String,
      block: suspend () -> T
  ): T {
    val exitResult =
        suspendCancellableCoroutine { cont: CancellableContinuation<CompletableDeferred<ByteBuffer>>
          ->
          syscalls.enterSideEffectBlock(
              name,
              object : EnterSideEffectSyscallCallback {
                override fun onSuccess(t: ByteBuffer?) {
                  val deferred: CompletableDeferred<ByteBuffer> = CompletableDeferred()
                  deferred.complete(t!!)
                  cont.resume(deferred)
                }

                override fun onFailure(t: TerminalException) {
                  val deferred: CompletableDeferred<ByteBuffer> = CompletableDeferred()
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
      actionReturnValue = block()
    } catch (e: TerminalException) {
      actionFailure = e
    } catch (t: Throwable) {
      syscalls.fail(t)
      throw CancellationException("Side effect failure", t)
    }

    val exitCallback =
        object : ExitSideEffectSyscallCallback {
          override fun onSuccess(t: ByteBuffer?) {
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
            cont: CancellableContinuation<Map.Entry<String, Deferred<ByteBuffer>>> ->
          syscalls.awakeable(completingContinuation(cont))
        }

    return AwakeableImpl(syscalls, deferredResult, serde, aid)
  }

  override fun awakeableHandle(id: String): AwakeableHandle {
    return AwakeableHandleImpl(syscalls, id)
  }

  override fun random(): RestateRandom {
    return RestateRandom(syscalls.request().invocationId().toRandomSeed(), syscalls)
  }

  override fun <T : Any> promise(key: DurablePromiseKey<T>): DurablePromise<T> {
    return DurablePromiseImpl(key)
  }

  override fun <T : Any> promiseHandle(key: DurablePromiseKey<T>): DurablePromiseHandle<T> {
    return DurablePromiseHandleImpl(key)
  }

  inner class DurablePromiseImpl<T : Any>(private val key: DurablePromiseKey<T>) :
      DurablePromise<T> {
    override suspend fun awaitable(): Awaitable<T> {
      val deferred: Deferred<ByteBuffer> =
          suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<ByteBuffer>> ->
            syscalls.promise(key.name(), completingContinuation(cont))
          }

      return SingleSerdeAwaitableImpl(syscalls, deferred, key.serde())
    }

    override suspend fun peek(): Output<T> {
      val deferred: Deferred<ByteBuffer> =
          suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<ByteBuffer>> ->
            syscalls.peekPromise(key.name(), completingContinuation(cont))
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
        return Output.notReady()
      }
      return Output.ready(key.serde().deserializeWrappingException(syscalls, readyResult.value!!))
    }
  }

  inner class DurablePromiseHandleImpl<T : Any>(private val key: DurablePromiseKey<T>) :
      DurablePromiseHandle<T> {
    override suspend fun resolve(payload: T) {
      val input = key.serde().serializeWrappingException(syscalls, payload)

      val deferred: Deferred<Void> =
          suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<Void>> ->
            syscalls.resolvePromise(key.name(), input, completingContinuation(cont))
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
    }

    override suspend fun reject(reason: String) {
      val deferred: Deferred<Void> =
          suspendCancellableCoroutine { cont: CancellableContinuation<Deferred<Void>> ->
            syscalls.rejectPromise(key.name(), reason, completingContinuation(cont))
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
    }
  }
}
