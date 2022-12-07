package dev.restate.sdk.kotlin

import com.google.protobuf.MessageLite
import dev.restate.generated.core.CallbackIdentifier
import dev.restate.sdk.core.*
import dev.restate.sdk.core.syscalls.DeferredResult
import dev.restate.sdk.core.syscalls.ReadyResult
import dev.restate.sdk.core.syscalls.Syscalls
import dev.restate.sdk.core.syscalls.Syscalls.CallbackClosure
import dev.restate.sdk.core.syscalls.Syscalls.SideEffectClosure
import io.grpc.MethodDescriptor
import java.util.function.Consumer
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
      syscalls.set(key.name(), value, { cont.resume(Unit) }, { cont.resumeWithException(it) })
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

  override suspend fun <T> sideEffect(typeTag: TypeTag<T>, sideEffectAction: suspend () -> T): T {
    val scope = CoroutineScope(Dispatchers.Unconfined) // Capture the scope

    return suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
      syscalls.sideEffect(
          typeTag,
          SideEffectClosureBridge(scope, sideEffectAction),
          { cont.resume(it) },
          { cont.resumeWithException(it) },
          { cont.cancel(it) })
    }
  }

  override suspend fun sideEffect(sideEffectAction: suspend () -> Unit) {
    val scope = CoroutineScope(Dispatchers.Unconfined) // Capture the scope

    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.sideEffect(
          TypeTag.ofClass(Unit.javaClass),
          SideEffectClosureBridge(scope, sideEffectAction),
          { cont.resume(Unit) },
          { cont.resumeWithException(it) },
          { cont.cancel(it) })
    }
  }

  override suspend fun <T> callbackAsync(
      typeTag: TypeTag<T>,
      callbackAction: suspend (CallbackIdentifier) -> Unit
  ): Awaitable<T> {
    val scope = CoroutineScope(Dispatchers.Unconfined) // Capture the scope

    val deferredResult: DeferredResult<T> =
        suspendCancellableCoroutine { cont: CancellableContinuation<DeferredResult<T>> ->
          syscalls.callback(
              typeTag,
              CallbackClosureBridge(scope, callbackAction),
              { cont.resume(it) },
              { cont.resumeWithException(it) })
        }

    return NonNullAwaitableImpl(syscalls, deferredResult)
  }

  override suspend fun completeCallback(id: CallbackIdentifier, payload: Any) {
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      syscalls.completeCallback(
          id, payload, { cont.resume(Unit) }, { cont.resumeWithException(it) })
    }
  }

  private class SideEffectClosureBridge<T>(
      val scope: CoroutineScope,
      val sideEffectAction: suspend () -> T
  ) : SideEffectClosure<T> {

    // TODO we need a test here to check that we correctly use the scope, and we get the context
    // propagated
    //  We might not need this though by splitting the side effect syscall
    // https://github.com/vert-x3/vertx-lang-kotlin/blob/master/vertx-lang-kotlin-coroutines/src/test/kotlin/io/vertx/kotlin/coroutines/CoroutineContextTest.kt#L104

    override fun execute(resultCallback: Consumer<T>, errorCallback: Consumer<Throwable>) {
      scope.launch {
        try {
          resultCallback.accept(sideEffectAction())
        } catch (e: Throwable) {
          errorCallback.accept(e)
        }
      }
    }
  }

  private class CallbackClosureBridge(
      val scope: CoroutineScope,
      val callbackAction: suspend (CallbackIdentifier) -> Unit
  ) : CallbackClosure {
    override fun execute(
        identifier: CallbackIdentifier,
        okCallback: java.lang.Runnable,
        errorCallback: Consumer<Throwable>
    ) {
      scope.launch {
        try {
          callbackAction(identifier)
          okCallback.run()
        } catch (e: Throwable) {
          errorCallback.accept(e)
        }
      }
    }
  }
}
