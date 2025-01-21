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
import dev.restate.sdk.types.Target
import dev.restate.sdk.endpoint.AsyncResult
import dev.restate.sdk.common.syscalls.EnterSideEffectSyscallCallback
import dev.restate.sdk.common.syscalls.ExitSideEffectSyscallCallback
import dev.restate.sdk.endpoint.HandlerContext
import dev.restate.sdk.serde.Serde
import dev.restate.sdk.types.DurablePromiseKey
import dev.restate.sdk.types.Output
import dev.restate.sdk.types.Request
import dev.restate.sdk.types.StateKey
import dev.restate.sdk.types.TerminalException
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

internal class ContextImpl internal constructor(internal val handlerContext: HandlerContext) : WorkflowContext {
  override fun key(): String {
    return this.handlerContext.objectKey()
  }

  override fun request(): Request {
    return this.handlerContext.request()
  }

  override suspend fun <T : Any> get(key: StateKey<T>): T? {
    val asyncResult: AsyncResult<ByteBuffer> =
        suspendCancellableCoroutine { cont: CancellableContinuation<AsyncResult<ByteBuffer>> ->
          handlerContext.get(key.name(), completingContinuation(cont))
        }

    if (!asyncResult.isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        handlerContext.resolveDeferred(asyncResult, completingUnitContinuation(cont))
      }
    }

    val readyResult = asyncResult.toResult()!!
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    if (readyResult.isEmpty) {
      return null
    }
    return key.serde().deserializeWrappingException(handlerContext, readyResult.value!!)!!
  }

  override suspend fun stateKeys(): Collection<String> {
    val asyncResult: AsyncResult<Collection<String>> =
        suspendCancellableCoroutine { cont: CancellableContinuation<AsyncResult<Collection<String>>> ->
          handlerContext.getKeys(completingContinuation(cont))
        }

    if (!asyncResult.isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        handlerContext.resolveDeferred(asyncResult, completingUnitContinuation(cont))
      }
    }

    val readyResult = asyncResult.toResult()!!
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    return readyResult.value!!
  }

  override suspend fun <T : Any> set(key: StateKey<T>, value: T) {
    val serializedValue = key.serde().serializeWrappingException(handlerContext, value)
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      handlerContext.set(key.name(), serializedValue, completingUnitContinuation(cont))
    }
  }

  override suspend fun clear(key: StateKey<*>) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      handlerContext.clear(key.name(), completingUnitContinuation(cont))
    }
  }

  override suspend fun clearAll() {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      handlerContext.clearAll(completingUnitContinuation(cont))
    }
  }

  override suspend fun timer(duration: Duration): Awaitable<Unit> {
    val asyncResult: AsyncResult<Void> =
        suspendCancellableCoroutine { cont: CancellableContinuation<AsyncResult<Void>> ->
          handlerContext.sleep(duration.toJavaDuration(), completingContinuation(cont))
        }

    return UnitAwakeableImpl(handlerContext, asyncResult)
  }

  override suspend fun <T : Any?, R : Any?> callAsync(
    target: Target,
    inputSerde: Serde<T>,
    outputSerde: Serde<R>,
    parameter: T
  ): Awaitable<R> {
    val input = inputSerde.serializeWrappingException(handlerContext, parameter)

    val asyncResult: AsyncResult<ByteBuffer> =
        suspendCancellableCoroutine { cont: CancellableContinuation<AsyncResult<ByteBuffer>> ->
          handlerContext.call(target, input, completingContinuation(cont))
        }

    return SingleSerdeAwaitableImpl(handlerContext, asyncResult, outputSerde)
  }

  override suspend fun <T : Any?> send(
    target: dev.restate.sdk.types.Target,
    inputSerde: Serde<T>,
    parameter: T,
    delay: Duration
  ) {
    val input = inputSerde.serializeWrappingException(handlerContext, parameter)

    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      handlerContext.send(target, input, delay.toJavaDuration(), completingUnitContinuation(cont))
    }
  }

  override suspend fun <T : Any?> runBlock(
      serde: Serde<T>,
      name: String,
      retryPolicy: RetryPolicy?,
      block: suspend () -> T
  ): T {
    val exitResult =
        suspendCancellableCoroutine { cont: CancellableContinuation<CompletableDeferred<ByteBuffer>>
          ->
          handlerContext.enterSideEffectBlock(
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
      return serde.deserializeWrappingException(handlerContext, exitResult.await())!!
    }

    var actionReturnValue: T? = null
    var actionFailure: Throwable? = null
    try {
      actionReturnValue = block()
    } catch (t: Throwable) {
      actionFailure = t
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
            exitResult.cancel(CancellationException(message = null, cause = t))
          }
        }

    if (actionFailure != null) {
      val javaRetryPolicy =
          retryPolicy?.let {
            dev.restate.sdk.types.RetryPolicy.exponential(
                    it.initialDelay.toJavaDuration(), it.exponentiationFactor)
                .setMaxAttempts(it.maxAttempts)
                .setMaxDelay(it.maxDelay?.toJavaDuration())
                .setMaxDuration(it.maxDuration?.toJavaDuration())
          }
      handlerContext.exitSideEffectBlockWithException(actionFailure, javaRetryPolicy, exitCallback)
    } else {
      handlerContext.exitSideEffectBlock(
          serde.serializeWrappingException(handlerContext, actionReturnValue), exitCallback)
    }

    return serde.deserializeWrappingException(handlerContext, exitResult.await())
  }

  override suspend fun <T : Any> awakeable(serde: Serde<T>): Awakeable<T> {
    val (aid, deferredResult) =
        suspendCancellableCoroutine {
            cont: CancellableContinuation<Map.Entry<String, AsyncResult<ByteBuffer>>> ->
          handlerContext.awakeable(completingContinuation(cont))
        }

    return AwakeableImpl(handlerContext, deferredResult, serde, aid)
  }

  override fun awakeableHandle(id: String): AwakeableHandle {
    return AwakeableHandleImpl(handlerContext, id)
  }

  override fun random(): RestateRandom {
    return RestateRandom(handlerContext.request().invocationId().toRandomSeed(), handlerContext)
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
      val asyncResult: AsyncResult<ByteBuffer> =
          suspendCancellableCoroutine { cont: CancellableContinuation<AsyncResult<ByteBuffer>> ->
            handlerContext.promise(key.name(), completingContinuation(cont))
          }

      return SingleSerdeAwaitableImpl(handlerContext, asyncResult, key.serde())
    }

    override suspend fun peek(): Output<T> {
      val asyncResult: AsyncResult<ByteBuffer> =
          suspendCancellableCoroutine { cont: CancellableContinuation<AsyncResult<ByteBuffer>> ->
            handlerContext.peekPromise(key.name(), completingContinuation(cont))
          }

      if (!asyncResult.isCompleted) {
        suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
          handlerContext.resolveDeferred(asyncResult, completingUnitContinuation(cont))
        }
      }

      val readyResult = asyncResult.toResult()!!
      if (!readyResult.isSuccess) {
        throw readyResult.failure!!
      }
      if (readyResult.isEmpty) {
        return Output.notReady()
      }
      return Output.ready(key.serde().deserializeWrappingException(handlerContext, readyResult.value!!))
    }
  }

  inner class DurablePromiseHandleImpl<T : Any>(private val key: DurablePromiseKey<T>) :
      DurablePromiseHandle<T> {
    override suspend fun resolve(payload: T) {
      val input = key.serde().serializeWrappingException(handlerContext, payload)

      val asyncResult: AsyncResult<Void> =
          suspendCancellableCoroutine { cont: CancellableContinuation<AsyncResult<Void>> ->
            handlerContext.resolvePromise(key.name(), input, completingContinuation(cont))
          }

      if (!asyncResult.isCompleted) {
        suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
          handlerContext.resolveDeferred(asyncResult, completingUnitContinuation(cont))
        }
      }

      val readyResult = asyncResult.toResult()!!
      if (!readyResult.isSuccess) {
        throw readyResult.failure!!
      }
    }

    override suspend fun reject(reason: String) {
      val asyncResult: AsyncResult<Void> =
          suspendCancellableCoroutine { cont: CancellableContinuation<AsyncResult<Void>> ->
            handlerContext.rejectPromise(key.name(), reason, completingContinuation(cont))
          }

      if (!asyncResult.isCompleted) {
        suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
          handlerContext.resolveDeferred(asyncResult, completingUnitContinuation(cont))
        }
      }

      val readyResult = asyncResult.toResult()!!
      if (!readyResult.isSuccess) {
        throw readyResult.failure!!
      }
    }
  }
}
