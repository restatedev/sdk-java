package dev.restate.sdk.kotlin

import com.google.protobuf.MessageLite
import dev.restate.generated.core.CallbackIdentifier
import dev.restate.sdk.core.*
import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.ReadyResult
import dev.restate.sdk.core.syscalls.Syscalls
import io.grpc.MethodDescriptor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*

internal class RestateContextImpl internal constructor(private val syscalls: Syscalls) :
    RestateContext {
  override suspend fun <T : Any> get(key: StateKey<T>): T? {
    val deferredResult: DeferredResult<T> =
        suspendCancellableCoroutine { cont: CancellableContinuation<DeferredResult<T>> ->
          syscalls.get(key.name(), key.typeTag(), { cont.resume(it) }, { cont.cancel(it) })
        }

    val readyResult: ReadyResult<T> = resolveDeferred(syscalls, deferredResult)
    return readyResult.result
  }

  override suspend fun <T> set(key: StateKey<T>, value: T) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.set(
          key.name(), key.typeTag(), value, { cont.resume(Unit) }, { cont.resumeWithException(it) })
    }
  }

  override suspend fun clear(key: StateKey<*>) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.clear(key.name(), { cont.resume(Unit) }, { cont.resumeWithException(it) })
    }
  }

  override suspend fun timer(duration: Duration): Awaitable<Unit> {
    val deferredResult: DeferredResult<Void> =
        suspendCancellableCoroutine { cont: CancellableContinuation<DeferredResult<Void>> ->
          syscalls.sleep(duration.toJavaDuration(), { cont.resume(it) }, { cont.cancel(it) })
        }

    return UnitAwaitableImpl(syscalls, deferredResult)
  }

  override suspend fun <T : MessageLite, R : MessageLite> callAsync(
      methodDescriptor: MethodDescriptor<T, R>,
      parameter: T
  ): Awaitable<R> {
    val deferredResult: DeferredResult<R> =
        suspendCancellableCoroutine { cont: CancellableContinuation<DeferredResult<R>> ->
          syscalls.call(methodDescriptor, parameter, { cont.resume(it) }, { cont.cancel(it) })
        }

    return NonNullAwaitableImpl(syscalls, deferredResult)
  }

  override suspend fun <T : MessageLite> backgroundCall(
      methodDescriptor: MethodDescriptor<T, MessageLite>,
      parameter: T
  ) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.backgroundCall(
          methodDescriptor, parameter, { cont.resume(Unit) }, { cont.resumeWithException(it) })
    }
  }

  override suspend fun <T> sideEffect(typeTag: TypeTag<T>, sideEffectAction: suspend () -> T?): T? {
    val enterResult =
        suspendCancellableCoroutine { cont: CancellableContinuation<ReadyResult<T>?> ->
          syscalls.enterSideEffectBlock(
              typeTag, { cont.resume(null) }, { cont.resume(it) }, { cont.cancel(it) })
        }

    if (enterResult != null) {
      return unpackNullableReady(enterResult)
    }

    var actionReturnValue: T? = null
    var actionFailure: Throwable? = null
    try {
      actionReturnValue = sideEffectAction()
    } catch (e: Throwable) {
      actionFailure = e
    }

    val exitResult =
        if (actionFailure != null) {
          suspendCancellableCoroutine { cont: CancellableContinuation<ReadyResult<T>> ->
            syscalls.exitSideEffectBlockWithException(
                actionFailure, { cont.resumeWithException(it) }, { cont.resumeWithException(it) })
          }
        } else {
          suspendCancellableCoroutine { cont: CancellableContinuation<ReadyResult<T>> ->
            syscalls.exitSideEffectBlock(
                typeTag, actionReturnValue, { cont.resume(it) }, { cont.resumeWithException(it) })
          }
        }

    return unpackNullableReady(exitResult)
  }

  override suspend fun <T> callbackAsync(
      typeTag: TypeTag<T>,
      callbackAction: suspend (CallbackIdentifier) -> Unit
  ): Awaitable<T> {
    val (cid, deferredResult) =
        suspendCancellableCoroutine {
            cont: CancellableContinuation<Pair<CallbackIdentifier, DeferredResult<T>>> ->
          syscalls.callback(
              typeTag,
              { cid, deferredResult -> cont.resume(cid to deferredResult) },
              { cont.resumeWithException(it) })
        }

    this.sideEffect { callbackAction(cid) }

    return NonNullAwaitableImpl(syscalls, deferredResult)
  }

  override suspend fun <T> completeCallback(
      id: CallbackIdentifier,
      typeTag: TypeTag<T>,
      payload: T
  ) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.completeCallback(
          id, typeTag, payload, { cont.resume(Unit) }, { cont.resumeWithException(it) })
    }
  }
}
