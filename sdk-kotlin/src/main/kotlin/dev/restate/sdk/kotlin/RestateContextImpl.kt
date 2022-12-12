package dev.restate.sdk.kotlin

import com.google.protobuf.MessageLite
import dev.restate.generated.core.CallbackIdentifier
import dev.restate.sdk.core.*
import dev.restate.sdk.core.syscalls.EnterSideEffectSyscallCallback
import dev.restate.sdk.core.syscalls.ExitSideEffectSyscallCallback
import dev.restate.sdk.core.syscalls.Syscalls
import io.grpc.MethodDescriptor
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*

internal class RestateContextImpl internal constructor(private val syscalls: Syscalls) :
    RestateContext {
  override suspend fun <T : Any> get(key: StateKey<T>): T? {
    val deferred: CompletableDeferred<T?> = CompletableDeferred()

    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.get(
          key.name(),
          key.typeTag(),
          completingUnitContinuation(cont),
          completingOptionalDeferred(deferred))
    }

    return deferred.await()
  }

  override suspend fun <T> set(key: StateKey<T>, value: T) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.set(key.name(), key.typeTag(), value, completingUnitContinuation(cont))
    }
  }

  override suspend fun clear(key: StateKey<*>) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.clear(key.name(), completingUnitContinuation(cont))
    }
  }

  override suspend fun timer(duration: Duration): Deferred<Unit> {
    val deferred: CompletableDeferred<Unit> = CompletableDeferred()

    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.sleep(
          duration.toJavaDuration(),
          completingUnitContinuation(cont),
          completingUnitDeferred(deferred))
    }

    return deferred
  }

  override suspend fun <T : MessageLite, R : MessageLite> callAsync(
      methodDescriptor: MethodDescriptor<T, R>,
      parameter: T
  ): Deferred<R> {
    val deferred: CompletableDeferred<R> = CompletableDeferred()

    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.call(
          methodDescriptor,
          parameter,
          completingUnitContinuation(cont),
          completingDeferred(deferred))
    }

    return deferred
  }

  override suspend fun <T : MessageLite> backgroundCall(
      methodDescriptor: MethodDescriptor<T, MessageLite>,
      parameter: T
  ) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.backgroundCall(methodDescriptor, parameter, completingUnitContinuation(cont))
    }
  }

  override suspend fun <T> sideEffect(typeTag: TypeTag<T>, sideEffectAction: suspend () -> T?): T? {
    val exitResult =
        suspendCancellableCoroutine { cont: CancellableContinuation<CompletableDeferred<T?>> ->
          syscalls.enterSideEffectBlock(
              typeTag,
              object : EnterSideEffectSyscallCallback<T?> {
                override fun onResult(t: T?) {
                  val deferred: CompletableDeferred<T?> = CompletableDeferred()
                  deferred.complete(t)
                  cont.resume(deferred)
                }

                override fun onFailure(t: Throwable) {
                  val deferred: CompletableDeferred<T?> = CompletableDeferred()
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
      return exitResult.await()
    }

    var actionReturnValue: T? = null
    var actionFailure: Throwable? = null
    try {
      actionReturnValue = sideEffectAction()
    } catch (e: Throwable) {
      actionFailure = e
    }

    val exitCallback =
        object : ExitSideEffectSyscallCallback<T?> {
          override fun onResult(t: T?) {
            exitResult.complete(t)
          }

          override fun onFailure(t: Throwable) {
            exitResult.completeExceptionally(t)
          }

          override fun onCancel(t: Throwable?) {
            exitResult.cancel(CancellationException("Suspended", t))
          }
        }

    if (actionFailure != null) {
      syscalls.exitSideEffectBlockWithException(actionFailure, exitCallback)
    } else {
      syscalls.exitSideEffectBlock(typeTag, actionReturnValue, exitCallback)
    }

    return exitResult.await()
  }

  override suspend fun <T> callbackAsync(
      typeTag: TypeTag<T>,
      callbackAction: suspend (CallbackIdentifier) -> Unit
  ): Deferred<T> {
    val deferred: CompletableDeferred<T> = CompletableDeferred()

    val cid = suspendCancellableCoroutine { cont: CancellableContinuation<CallbackIdentifier> ->
      syscalls.callback(typeTag, completingContinuation(cont), completingDeferred(deferred))
    }

    this.sideEffect { callbackAction(cid) }

    return deferred
  }

  override suspend fun <T> completeCallback(
      id: CallbackIdentifier,
      typeTag: TypeTag<T>,
      payload: T
  ) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.completeCallback(id, typeTag, payload, completingUnitContinuation(cont))
    }
  }
}
