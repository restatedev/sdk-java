package dev.restate.sdk.kotlin

import com.google.protobuf.MessageLite
import dev.restate.generated.core.AwakeableIdentifier
import dev.restate.sdk.core.*
import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.EnterSideEffectSyscallCallback
import dev.restate.sdk.core.syscalls.ExitSideEffectSyscallCallback
import dev.restate.sdk.core.syscalls.Syscalls
import io.grpc.MethodDescriptor
import io.grpc.StatusRuntimeException
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*

internal class RestateContextImpl internal constructor(private val syscalls: Syscalls) :
    RestateContext {
  override fun invocationId(): String {
    return syscalls.invocationId()
  }

  override suspend fun <T : Any> get(key: StateKey<T>): T? {
    val deferredResult: DeferredResult<T> =
        suspendCancellableCoroutine { cont: CancellableContinuation<DeferredResult<T>> ->
          syscalls.get(key.name(), key.typeTag(), completingContinuation(cont))
        }

    if (!deferredResult.isCompleted) {
      suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        syscalls.resolveDeferred(deferredResult, completingUnitContinuation(cont))
      }
    }

    val readyResult = deferredResult.toReadyResult()!!
    if (!readyResult.isSuccess) {
      throw readyResult.failure!!
    }
    if (readyResult.isEmpty) {
      return null
    }
    return readyResult.result!!
  }

  override suspend fun <T : Any> set(key: StateKey<T>, value: T) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.set(key.name(), key.typeTag(), value, completingUnitContinuation(cont))
    }
  }

  override suspend fun clear(key: StateKey<*>) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.clear(key.name(), completingUnitContinuation(cont))
    }
  }

  override suspend fun timer(duration: Duration): Awaitable<Unit> {
    val deferredResult: DeferredResult<Void> =
        suspendCancellableCoroutine { cont: CancellableContinuation<DeferredResult<Void>> ->
          syscalls.sleep(duration.toJavaDuration(), completingContinuation(cont))
        }

    return UnitAwaitableImpl(syscalls, deferredResult)
  }

  override suspend fun <T : MessageLite, R : MessageLite> callAsync(
      methodDescriptor: MethodDescriptor<T, R>,
      parameter: T
  ): Awaitable<R> {
    val deferredResult: DeferredResult<R> =
        suspendCancellableCoroutine { cont: CancellableContinuation<DeferredResult<R>> ->
          syscalls.call(methodDescriptor, parameter, completingContinuation(cont))
        }

    return NonNullAwaitableImpl(syscalls, deferredResult)
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

                override fun onFailure(t: StatusRuntimeException) {
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

          override fun onFailure(t: StatusRuntimeException) {
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

  override suspend fun <T> awakeable(typeTag: TypeTag<T>): Awakeable<T> {
    val (aid, deferredResult) =
        suspendCancellableCoroutine {
            cont: CancellableContinuation<Map.Entry<AwakeableIdentifier, DeferredResult<T>>> ->
          syscalls.awakeable(typeTag, completingContinuation(cont))
        }

    return AwakeableImpl(syscalls, deferredResult, aid)
  }

  override fun awakeableHandle(id: AwakeableIdentifier): AwakeableHandle {
    return AwakeableHandleImpl(syscalls, id)
  }
}
