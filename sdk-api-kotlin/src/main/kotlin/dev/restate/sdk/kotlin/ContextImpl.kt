// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.common.CallRequest
import dev.restate.common.Output
import dev.restate.common.SendRequest
import dev.restate.common.Slice
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.sdk.types.DurablePromiseKey
import dev.restate.sdk.types.Request
import dev.restate.sdk.types.StateKey
import dev.restate.sdk.types.TerminalException
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import dev.restate.serde.SerdeInfo
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture

internal class ContextImpl internal constructor(internal val handlerContext: HandlerContext, internal val contextSerdeFactory: SerdeFactory) :
    WorkflowContext {
  override fun key(): String {
    return this.handlerContext.objectKey()
  }

  override fun request(): Request {
    return this.handlerContext.request()
  }

  override suspend fun <T : Any> get(key: StateKey<T>): T? =
    resolveSerde<T?>(key.serdeInfo()).let { serde -> SingleAwaitableImpl(handlerContext.get(key.name()).await())
      .simpleMap { it.getOrNull()?.let {
        serde.deserialize(it)
      } }}
          .await()

  override suspend fun stateKeys(): Collection<String> =
      SingleAwaitableImpl(handlerContext.getKeys().await()).await()

  override suspend fun <T : Any> set(key: StateKey<T>, value: T) {
    handlerContext.set(key.name(), resolveAndSerialize(key.serdeInfo(), value)).await()
  }

  override suspend fun clear(key: StateKey<*>) {
    handlerContext.clear(key.name()).await()
  }

  override suspend fun clearAll() {
    handlerContext.clearAll().await()
  }

  override suspend fun timer(duration: Duration, name: String?): Awaitable<Unit> =
      SingleAwaitableImpl(handlerContext.timer(duration.toJavaDuration(), name).await()).map {}

  override suspend fun <T : Any?, R : Any?> call(
      callRequest: CallRequest<T, R>
  ): CallAwaitable<R> =
    resolveSerde<R>(callRequest.responseSerdeInfo()).let { responseSerde ->
      val callHandle =handlerContext
        .call(
          callRequest.target(),
          resolveAndSerialize<T>(callRequest.requestSerdeInfo(), callRequest.request()),
          callRequest.idempotencyKey(),
          callRequest.headers().entries
        )
        .await()

      val callAsyncResult = callHandle.callAsyncResult.map { CompletableFuture.completedFuture<R>(responseSerde.deserialize(it)) }

      return@let CallAwaitableImpl(callAsyncResult, callHandle.invocationIdAsyncResult)
    }

  override suspend fun <T : Any?> send(
      sendRequest: SendRequest<T>
  ): SendHandle = SendHandleImpl(
    handlerContext
      .send(
        sendRequest.target(),
        resolveAndSerialize<T>(sendRequest.requestSerdeInfo(), sendRequest.request()),
        sendRequest.idempotencyKey(),
        sendRequest.headers().entries,
        sendRequest.delay()
      )
      .await()
  )

  override suspend fun <T : Any?> runAsync(
      serdeInfo: SerdeInfo<T>,
      name: String,
      retryPolicy: RetryPolicy?,
      block: suspend () -> T
  ): Awaitable<T> {
    var serde: Serde<T> = resolveSerde(serdeInfo);
    var coroutineCtx = currentCoroutineContext()
    val javaRetryPolicy =
        retryPolicy?.let {
          dev.restate.sdk.types.RetryPolicy.exponential(
                  it.initialDelay.toJavaDuration(), it.exponentiationFactor)
              .setMaxAttempts(it.maxAttempts)
              .setMaxDelay(it.maxDelay?.toJavaDuration())
              .setMaxDuration(it.maxDuration?.toJavaDuration())
        }

    val scope = CoroutineScope(coroutineCtx + CoroutineName("restate-run-$name"))

    val asyncResult =
        handlerContext
            .submitRun(name) { completer ->
              scope.launch {
                val result: Slice?
                try {
                  result = serde.serialize(block())
                } catch (e: Throwable) {
                  completer.proposeFailure(e, javaRetryPolicy)
                  return@launch
                }
                completer.proposeSuccess(result)
              }
            }
            .await()
    return SingleAwaitableImpl(asyncResult).map { serde.deserialize(it) }
  }

  override suspend fun <T : Any> awakeable(serdeInfo: SerdeInfo<T>): Awakeable<T> {
    val serde: Serde<T> = resolveSerde(serdeInfo)
    val awk = handlerContext.awakeable().await()
    return AwakeableImpl(awk.asyncResult, serde, awk.id)
  }

  override fun awakeableHandle(id: String): AwakeableHandle {
    return AwakeableHandleImpl(this, id)
  }

  override fun random(): RestateRandom {
    return RestateRandom(handlerContext.request().invocationId().toRandomSeed())
  }

  override fun <T : Any> promise(key: DurablePromiseKey<T>): DurablePromise<T> {
    return DurablePromiseImpl(key)
  }

  override fun <T : Any> promiseHandle(key: DurablePromiseKey<T>): DurablePromiseHandle<T> {
    return DurablePromiseHandleImpl(key)
  }

  inner class DurablePromiseImpl<T : Any>(private val key: DurablePromiseKey<T>) :
      DurablePromise<T> {
        val serde: Serde<T> = resolveSerde(key.serdeInfo())

    override suspend fun awaitable(): Awaitable<T> =
        SingleAwaitableImpl(handlerContext.promise(key.name()).await()).simpleMap {
          serde.deserialize(it)
        }

    override suspend fun peek(): Output<T> =
        SingleAwaitableImpl(handlerContext.peekPromise(key.name()).await())
            .simpleMap { it.map { serde.deserialize(it) } }
            .await()
  }

  inner class DurablePromiseHandleImpl<T : Any>(private val key: DurablePromiseKey<T>) :
      DurablePromiseHandle<T> {
    val serde: Serde<T> = resolveSerde(key.serdeInfo())

    override suspend fun resolve(payload: T) {
      SingleAwaitableImpl(
              handlerContext
                  .resolvePromise(
                      key.name(), serde.serializeWrappingException(handlerContext, payload))
                  .await())
          .await()
    }

    override suspend fun reject(reason: String) {
      SingleAwaitableImpl(
              handlerContext.rejectPromise(key.name(), TerminalException(reason)).await())
          .await()
    }
  }

  internal fun <T : Any?>  resolveAndSerialize(serdeInfo: SerdeInfo<T>, value: T): Slice {
    return try {
      val serde = contextSerdeFactory.create<T>(serdeInfo)
      serde.serialize(value)
    } catch (e: Exception) {
      handlerContext.fail(e)
      throw CancellationException("Failed serialization", e)
    }
  }

  private fun <T : Any?>  resolveSerde(serdeInfo: SerdeInfo<T>): Serde<T> {
    return try {
      contextSerdeFactory.create<T>(serdeInfo)!!
    } catch (e: Exception) {
      handlerContext.fail(e)
      throw CancellationException("Cannot resolve serde", e)
    }
  }
}
