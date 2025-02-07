// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.common.Output
import dev.restate.common.Slice
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.sdk.types.DurablePromiseKey
import dev.restate.sdk.types.Request
import dev.restate.sdk.types.StateKey
import dev.restate.sdk.types.TerminalException
import dev.restate.serde.Serde
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await

internal class ContextImpl internal constructor(internal val handlerContext: HandlerContext) :
    WorkflowContext {
  override fun key(): String {
    return this.handlerContext.objectKey()
  }

  override fun request(): Request {
    return this.handlerContext.request()
  }

  override suspend fun <T : Any> get(key: StateKey<T>): T? =
      SingleAwaitableImpl(handlerContext.get(key.name()).await())
          .map { it.getOrNull()?.let { key.serde().deserialize(it) } }
          .await()

  override suspend fun stateKeys(): Collection<String> =
      SingleAwaitableImpl(handlerContext.getKeys().await()).await()

  override suspend fun <T : Any> set(key: StateKey<T>, value: T) {
    val serializedValue = key.serde().serializeWrappingException(handlerContext, value)
    handlerContext.set(key.name(), serializedValue).await()
  }

  override suspend fun clear(key: StateKey<*>) {
    handlerContext.clear(key.name()).await()
  }

  override suspend fun clearAll() {
    handlerContext.clearAll().await()
  }

  override suspend fun timer(duration: Duration): Awaitable<Unit> =
      SingleAwaitableImpl(handlerContext.sleep(duration.toJavaDuration()).await()).map {}

  override suspend fun <T : Any?, R : Any?> callAsync(
      target: dev.restate.common.Target,
      inputSerde: Serde<T>,
      outputSerde: Serde<R>,
      parameter: T
  ): Awaitable<R> =
      SingleAwaitableImpl(
              handlerContext
                  .call(
                      target,
                      inputSerde.serializeWrappingException(handlerContext, parameter),
                      null,
                      null)
                  .await()
                  .callAsyncResult)
          .map { outputSerde.deserialize(it) }

  override suspend fun <T : Any?> send(
      target: dev.restate.common.Target,
      inputSerde: Serde<T>,
      parameter: T,
      delay: Duration
  ) {
    handlerContext
        .send(
            target,
            inputSerde.serializeWrappingException(handlerContext, parameter),
            null,
            null,
            delay.toJavaDuration())
        .await()
  }

  override suspend fun <T : Any?> runAsync(
      serde: Serde<T>,
      name: String,
      retryPolicy: RetryPolicy?,
      block: suspend () -> T
  ): Awaitable<T> {
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

  override suspend fun <T : Any> awakeable(serde: Serde<T>): Awakeable<T> {
    val awk = handlerContext.awakeable().await()

    return AwakeableImpl(awk.asyncResult, serde, awk.id)
  }

  override fun awakeableHandle(id: String): AwakeableHandle {
    return AwakeableHandleImpl(handlerContext, id)
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
    override suspend fun awaitable(): Awaitable<T> =
        SingleAwaitableImpl(handlerContext.promise(key.name()).await()).map {
          key.serde().deserialize(it)
        }

    override suspend fun peek(): Output<T> =
        SingleAwaitableImpl(handlerContext.peekPromise(key.name()).await())
            .map { it.map { key.serde().deserialize(it) } }
            .await()
  }

  inner class DurablePromiseHandleImpl<T : Any>(private val key: DurablePromiseKey<T>) :
      DurablePromiseHandle<T> {
    override suspend fun resolve(payload: T) {
      handlerContext.resolvePromise(
          key.name(), key.serde().serializeWrappingException(handlerContext, payload))
    }

    override suspend fun reject(reason: String) {
      handlerContext.rejectPromise(key.name(), TerminalException(reason))
    }
  }
}
