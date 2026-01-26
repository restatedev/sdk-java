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
import dev.restate.common.Request
import dev.restate.common.Slice
import dev.restate.sdk.common.DurablePromiseKey
import dev.restate.sdk.common.HandlerRequest
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import dev.restate.serde.TypeTag
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await

internal class ContextImpl
internal constructor(
    internal val handlerContext: HandlerContext,
    internal val contextSerdeFactory: SerdeFactory,
) : WorkflowContext {
  override fun key(): String {
    return this.handlerContext.objectKey()
  }

  override fun request(): HandlerRequest {
    return this.handlerContext.request()
  }

  override suspend fun <T : Any> get(key: StateKey<T>): T? =
      resolveSerde<T?>(key.serdeInfo())
          .let { serde ->
            SingleDurableFutureImpl(handlerContext.get(key.name()).await()).simpleMap {
              it.getOrNull()?.let { serde.deserialize(it) }
            }
          }
          .await()

  override suspend fun stateKeys(): Collection<String> =
      SingleDurableFutureImpl(handlerContext.getKeys().await()).await()

  override suspend fun <T : Any> set(key: StateKey<T>, value: T) {
    handlerContext.set(key.name(), resolveAndSerialize(key.serdeInfo(), value)).await()
  }

  override suspend fun clear(key: StateKey<*>) {
    handlerContext.clear(key.name()).await()
  }

  override suspend fun clearAll() {
    handlerContext.clearAll().await()
  }

  override suspend fun timer(duration: Duration, name: String?): DurableFuture<Unit> =
      SingleDurableFutureImpl(handlerContext.timer(duration.toJavaDuration(), name).await()).map {}

  override suspend fun <Req : Any?, Res : Any?> call(
      request: Request<Req, Res>
  ): CallDurableFuture<Res> =
      resolveSerde<Res>(request.getResponseTypeTag()).let { responseSerde ->
        val callHandle =
            handlerContext
                .call(
                    request.getTarget(),
                    resolveAndSerialize<Req>(request.getRequestTypeTag(), request.getRequest()),
                    request.getIdempotencyKey(),
                    request.getHeaders()?.entries,
                )
                .await()

        val callAsyncResult =
            callHandle.callAsyncResult.map {
              CompletableFuture.completedFuture<Res>(responseSerde.deserialize(it))
            }

        return@let CallDurableFutureImpl(callAsyncResult, callHandle.invocationIdAsyncResult)
      }

  override suspend fun <Req : Any?, Res : Any?> send(
      request: Request<Req, Res>,
      delay: Duration?,
  ): InvocationHandle<Res> =
      resolveSerde<Res>(request.getResponseTypeTag()).let { responseSerde ->
        val invocationIdAsyncResult =
            handlerContext
                .send(
                    request.getTarget(),
                    resolveAndSerialize<Req>(request.getRequestTypeTag(), request.getRequest()),
                    request.getIdempotencyKey(),
                    request.getHeaders()?.entries,
                    delay?.toJavaDuration(),
                )
                .await()

        object : BaseInvocationHandle<Res>(handlerContext, responseSerde) {
          override suspend fun invocationId(): String = invocationIdAsyncResult.poll().await()
        }
      }

  override fun <Res> invocationHandle(
      invocationId: String,
      responseTypeTag: TypeTag<Res>,
  ): InvocationHandle<Res> =
      resolveSerde<Res>(responseTypeTag).let { responseSerde ->
        object : BaseInvocationHandle<Res>(handlerContext, responseSerde) {
          override suspend fun invocationId(): String = invocationId
        }
      }

  override suspend fun <T : Any?> runAsync(
      typeTag: TypeTag<T>,
      name: String,
      retryPolicy: RetryPolicy?,
      block: suspend () -> T,
  ): DurableFuture<T> {
    val serde: Serde<T> = resolveSerde(typeTag)
    val coroutineCtx = currentCoroutineContext()
    val javaRetryPolicy =
        retryPolicy?.let {
          dev.restate.sdk.common.RetryPolicy.exponential(
                  it.initialDelay.toJavaDuration(),
                  it.exponentiationFactor,
              )
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
    return SingleDurableFutureImpl(asyncResult).map { serde.deserialize(it) }
  }

  override suspend fun <T : Any> awakeable(typeTag: TypeTag<T>): Awakeable<T> {
    val serde: Serde<T> = resolveSerde(typeTag)
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

    override suspend fun future(): DurableFuture<T> =
        SingleDurableFutureImpl(handlerContext.promise(key.name()).await()).simpleMap {
          serde.deserialize(it)
        }

    override suspend fun peek(): Output<T> =
        SingleDurableFutureImpl(handlerContext.peekPromise(key.name()).await())
            .simpleMap { it.map { serde.deserialize(it) } }
            .await()
  }

  inner class DurablePromiseHandleImpl<T : Any>(private val key: DurablePromiseKey<T>) :
      DurablePromiseHandle<T> {
    val serde: Serde<T> = resolveSerde(key.serdeInfo())

    override suspend fun resolve(payload: T) {
      SingleDurableFutureImpl(
              handlerContext
                  .resolvePromise(
                      key.name(),
                      serde.serializeWrappingException(handlerContext, payload),
                  )
                  .await()
          )
          .await()
    }

    override suspend fun reject(reason: String) {
      SingleDurableFutureImpl(
              handlerContext.rejectPromise(key.name(), TerminalException(reason)).await()
          )
          .await()
    }
  }

  internal fun <T : Any?> resolveAndSerialize(typeTag: TypeTag<T>, value: T): Slice {
    return try {
      val serde = contextSerdeFactory.create<T>(typeTag)
      serde.serialize(value)
    } catch (e: Exception) {
      handlerContext.fail(e)
      throw CancellationException("Failed serialization", e)
    }
  }

  private fun <T : Any?> resolveSerde(typeTag: TypeTag<T>): Serde<T> {
    return try {
      contextSerdeFactory.create<T>(typeTag)!!
    } catch (e: Exception) {
      handlerContext.fail(e)
      throw CancellationException("Cannot resolve serde", e)
    }
  }
}
