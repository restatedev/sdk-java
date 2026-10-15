// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
@file:Suppress("DEPRECATION")

package dev.restate.sdk.kotlin

import dev.restate.common.Output
import dev.restate.common.Request
import dev.restate.common.Slice
import dev.restate.sdk.common.DurablePromiseKey
import dev.restate.sdk.common.HandlerRequest
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.sdk.kotlin.interceptor.RunInterceptor
import dev.restate.sdk.kotlin.internal.InsideRunElement
import dev.restate.sdk.kotlin.internal.InsideRunElement.Key.checkNotInsideRun
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import dev.restate.serde.SerdePipeline
import dev.restate.serde.TypeTag
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*

/** @param request the request of the invocation, with the decoded body. */
internal class ContextImpl
internal constructor(
    internal val handlerContext: HandlerContext,
    private val request: HandlerRequest,
    internal val contextSerdeFactory: SerdeFactory,
    internal val runInterceptor: RunInterceptor = RunInterceptor { _, next -> next() },
) : WorkflowContext {

  private val serdePipeline: SerdePipeline =
      SerdePipeline(contextSerdeFactory, handlerContext.payloadCodec())

  internal val random: RestateRandom =
      RestateRandom(handlerContext.request().invocationId().toRandomSeed())

  override fun key(): String {
    return this.handlerContext.objectKey()
  }

  override fun request(): HandlerRequest {
    return this.request
  }

  override suspend fun <T : Any> get(key: StateKey<T>): T? {
    checkNotInsideRun()
    return resolveSerde<T?>(key.serdeInfo())
        .let { serde ->
          SingleDurableFutureImpl(handlerContext, handlerContext.get(key.name()).await()) {
            it.getOrNull()?.let { decode(serde, it) }
          }
        }
        .await()
  }

  override suspend fun stateKeys(): Collection<String> {
    checkNotInsideRun()
    return SingleDurableFutureImpl(handlerContext, handlerContext.getKeys().await()) { it }.await()
  }

  override suspend fun <T : Any> set(key: StateKey<T>, value: T) {
    checkNotInsideRun()
    handlerContext.set(key.name(), resolveAndSerialize(key.serdeInfo(), value)).await()
  }

  override suspend fun clear(key: StateKey<*>) {
    checkNotInsideRun()
    handlerContext.clear(key.name()).await()
  }

  override suspend fun clearAll() {
    checkNotInsideRun()
    handlerContext.clearAll().await()
  }

  override suspend fun timer(duration: Duration, name: String?): DurableFuture<Unit> {
    checkNotInsideRun()
    return SingleDurableFutureImpl(
        handlerContext,
        handlerContext.timer(duration.toJavaDuration(), name).await(),
    ) {}
  }

  override suspend fun <Req : Any?, Res : Any?> call(
      request: Request<Req, Res>
  ): CallDurableFuture<Res> {
    checkNotInsideRun()
    return resolveSerde<Res>(request.getResponseTypeTag()).let { responseSerde ->
      val callHandles =
          handlerContext
              .call(
                  request.getTarget(),
                  resolveAndSerialize<Req>(request.getRequestTypeTag(), request.getRequest()),
                  request.getIdempotencyKey(),
                  request.getLimitKey(),
                  request.getHeaders()?.entries,
              )
              .await()

      return@let CallDurableFutureImpl(handlerContext, callHandles) { decode(responseSerde, it) }
    }
  }

  override suspend fun <Req : Any?, Res : Any?> send(
      request: Request<Req, Res>,
      delay: Duration?,
  ): InvocationHandle<Res> {
    checkNotInsideRun()
    return resolveSerde<Res>(request.getResponseTypeTag()).let { responseSerde ->
      val invocationIdHandle =
          handlerContext
              .send(
                  request.getTarget(),
                  resolveAndSerialize<Req>(request.getRequestTypeTag(), request.getRequest()),
                  request.getIdempotencyKey(),
                  request.getLimitKey(),
                  request.getHeaders()?.entries,
                  delay?.toJavaDuration(),
              )
              .await()

      val invocationIdFuture = SingleDurableFutureImpl(handlerContext, invocationIdHandle) { it }

      object : BaseInvocationHandle<Res>(this, responseSerde) {
        override suspend fun invocationId(): String = invocationIdFuture.await()
      }
    }
  }

  override fun <Res> invocationHandle(
      invocationId: String,
      responseTypeTag: TypeTag<Res>,
  ): InvocationHandle<Res> =
      resolveSerde<Res>(responseTypeTag).let { responseSerde ->
        object : BaseInvocationHandle<Res>(this, responseSerde) {
          override suspend fun invocationId(): String = invocationId
        }
      }

  override suspend fun <T : Any?> runAsync(
      typeTag: TypeTag<T>,
      name: String,
      retryPolicy: RetryPolicy?,
      block: suspend () -> T,
  ): DurableFuture<T> {
    checkNotInsideRun()
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

    val scope =
        CoroutineScope(
            coroutineCtx + CoroutineName("restate-run-$name") + InsideRunElement.INSTANCE
        )

    val runHandle =
        handlerContext
            .submitRun(name) { completer ->
              scope.launch {
                val resultHolder = java.util.concurrent.atomic.AtomicReference<Slice>()

                val closure: suspend () -> Unit = { resultHolder.set(serde.serialize(block())) }

                try {
                  runInterceptor.aroundRun(RunInterceptor.Context(request, name), closure)
                } catch (t: Throwable) {
                  completer.proposeFailure(t, javaRetryPolicy)
                  return@launch
                }

                // Encoding failures are not run failures, but fail the invocation, which is then
                // retried.
                val result =
                    try {
                      serdePipeline.payloadCodec().encode(resultHolder.get()).await()
                    } catch (t: Throwable) {
                      handlerContext.fail(t)
                      return@launch
                    }
                completer.proposeSuccess(result)
              }
            }
            .await()
    return SingleDurableFutureImpl(handlerContext, runHandle) { decode(serde, it) }
  }

  override suspend fun <T : Any> awakeable(typeTag: TypeTag<T>): Awakeable<T> {
    checkNotInsideRun()
    val serde: Serde<T> = resolveSerde(typeTag)
    val awk = handlerContext.awakeable().await()
    return AwakeableImpl(handlerContext, awk.handle, { decode(serde, it) }, awk.id)
  }

  override fun awakeableHandle(id: String): AwakeableHandle {
    return AwakeableHandleImpl(this, id)
  }

  override suspend fun <T : Any> signal(name: String, typeTag: TypeTag<T>): DurableFuture<T> {
    checkNotInsideRun()
    val serde: Serde<T> = resolveSerde(typeTag)
    return SingleDurableFutureImpl(handlerContext, handlerContext.signal(name).await()) {
      decode(serde, it)
    }
  }

  override fun random(): RestateRandom {
    return this.random
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

    override suspend fun future(): DurableFuture<T> {
      checkNotInsideRun()
      return SingleDurableFutureImpl(handlerContext, handlerContext.promise(key.name()).await()) {
        decode(serde, it)
      }
    }

    override suspend fun peek(): Output<T> {
      checkNotInsideRun()
      return SingleDurableFutureImpl(
              handlerContext,
              handlerContext.peekPromise(key.name()).await(),
          ) {
            decodeOutput(serde, it)
          }
          .await()
    }
  }

  inner class DurablePromiseHandleImpl<T : Any>(private val key: DurablePromiseKey<T>) :
      DurablePromiseHandle<T> {
    val serde: Serde<T> = resolveSerde(key.serdeInfo())

    override suspend fun resolve(payload: T) {
      checkNotInsideRun()
      SingleDurableFutureImpl(
              handlerContext,
              handlerContext
                  .resolvePromise(
                      key.name(),
                      resolveAndSerialize(serde, payload),
                  )
                  .await(),
          ) {}
          .await()
    }

    override suspend fun reject(reason: String) {
      checkNotInsideRun()
      SingleDurableFutureImpl(
              handlerContext,
              handlerContext.rejectPromise(key.name(), TerminalException(reason)).await(),
          ) {}
          .await()
    }
  }

  /** Serialize and encode the value. Failures fail the invocation. */
  internal suspend fun <T : Any?> resolveAndSerialize(typeTag: TypeTag<T>, value: T): Slice {
    return try {
      serdePipeline.encode(typeTag, value).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handlerContext.fail(e)
      throw CancellationException("Failed serialization", e)
    }
  }

  /**
   * Decode and deserialize the value. This is used as mapper of the [SingleDurableFutureImpl]
   * nodes, so it runs when the node is completed, before evaluating the rest of the future tree.
   */
  internal suspend fun <T : Any?> decode(typeTag: TypeTag<T>, value: Slice): T {
    return serdePipeline.decode(typeTag, value).await()
  }

  internal suspend fun <T : Any?> decodeOutput(
      typeTag: TypeTag<T>,
      output: Output<Slice>,
  ): Output<T> {
    return if (output.isReady) Output.ready(decode(typeTag, output.value)) else Output.notReady()
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
