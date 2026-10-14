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
import dev.restate.sdk.common.AbortedExecutionException
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.common.TimeoutException
import dev.restate.sdk.endpoint.definition.HandlerContext
import dev.restate.sdk.kotlin.internal.InsideRunElement.Key.checkNotInsideRun
import dev.restate.serde.Serde
import dev.restate.serde.TypeTag
import java.util.concurrent.CompletionException
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CancellationException

/** Result of an aborted future, e.g. because the invocation suspended. */
private object Aborted

/**
 * Node of the future tree: every implementation implements one of the
 * [HandlerContext.UnresolvedFuture] subtypes. The result is completed by [await], which awaits the
 * tree with [HandlerContext.await] and then [tryComplete]s it.
 */
internal abstract class BaseDurableFutureImpl<T : Any?> : DurableFuture<T> {

  // Either null if not completed yet, Aborted, or HandlerContext.Result.
  // Written only by user land, and read by the HandlerContext from other threads while awaiting.
  @Volatile
  internal var result: Any? = null
    private set

  abstract val handlerContext: HandlerContext

  /**
   * Try to complete this future with the information already available, without awaiting: run the
   * mappers whose upstream is completed, and complete the combinators whose children are completed.
   *
   * Mappers can await other futures.
   */
  internal abstract suspend fun tryComplete()

  /** Complete this future as cancelled, as the invocation got cancelled while awaiting it. */
  internal open fun tryCancel() {
    fail(TerminalException(TerminalException.CANCELLED_CODE))
  }

  fun isDone(): Boolean = result != null

  /**
   * This future as a node of the future tree. Every implementation implements one of the
   * [HandlerContext.UnresolvedFuture] subtypes.
   */
  internal fun unresolvedFuture(): HandlerContext.UnresolvedFuture =
      this as HandlerContext.UnresolvedFuture

  override val onAwait: SelectClause<T>
    get() = SelectClauseImpl(this)

  override suspend fun await(): T {
    checkNotInsideRun()
    while (true) {
      tryComplete()
      if (isDone()) {
        return getOrThrow()
      }

      when (val awaitResult = handlerContext.await(unresolvedFuture()).await()) {
        // Complete the single before running any user code: mappers might await it.
        is HandlerContext.AwaitResult.AnyCompleted ->
            (awaitResult.single() as SingleDurableFutureImpl<*, *>).complete(awaitResult.result())
        is HandlerContext.AwaitResult.Cancelled -> tryCancel()
        else -> abort()
      }
    }
  }

  override suspend fun await(duration: Duration): T {
    return withTimeout(duration).await()
  }

  override suspend fun withTimeout(duration: Duration): DurableFuture<T> {
    val timer =
        SingleDurableFutureImpl(
            handlerContext,
            handlerContext.timer(duration.toJavaDuration(), null).await(),
        ) {}
    return AnyDurableFutureImpl(listOf(this, timer)).map {
      if (it == 1) {
        throw TimeoutException("Timed out waiting for durable future after $duration")
      }
      this.await()
    }
  }

  override suspend fun <R> map(transform: suspend (T) -> R): DurableFuture<R> {
    return MappedDurableFutureImpl(this, transform, null)
  }

  override suspend fun <R> map(
      transformSuccess: suspend (T) -> R,
      transformFailure: suspend (TerminalException) -> R,
  ): DurableFuture<R> {
    return MappedDurableFutureImpl(this, transformSuccess, transformFailure)
  }

  override suspend fun mapFailure(transform: suspend (TerminalException) -> T): DurableFuture<T> {
    return MappedDurableFutureImpl(this, null, transform)
  }

  // -- Result

  /**
   * @return the result of this completed future, or throws its failure: either [TerminalException],
   *   or [CancellationException] if aborted.
   */
  @Suppress("UNCHECKED_CAST")
  internal fun getOrThrow(): T {
    val result = this.result
    if (result === Aborted) {
      throw CancellationException("Restate invocation suspended or closed").apply {
        initCause(AbortedExecutionException.INSTANCE)
      }
    }
    return (result as HandlerContext.Result<T>).getResultOrThrow()
  }

  internal fun isFailedOrAborted(): Boolean {
    val result = this.result
    return result === Aborted || result is HandlerContext.Result.Failure<*>
  }

  internal fun complete(value: T) {
    if (result == null) {
      result = HandlerContext.Result.Success(value)
    }
  }

  internal fun fail(exception: TerminalException) {
    if (result == null) {
      result = HandlerContext.Result.Failure<T>(exception)
    }
  }

  internal fun abort() {
    if (result == null) {
      result = Aborted
    }
  }

  /** Complete this future with the result of the given completed future. */
  internal fun completeFrom(other: BaseDurableFutureImpl<*>) {
    if (result == null) {
      result = other.result
    }
  }

  /**
   * Complete this future with the result of [block].
   *
   * A [TerminalException] fails this future, an abort ([AbortedExecutionException], or a
   * [CancellationException] caused by it) aborts it, while any other failure fails the invocation,
   * and aborts this future.
   */
  internal inline fun completeWith(block: () -> T) {
    try {
      complete(block())
    } catch (t: Throwable) {
      val e = if (t is CompletionException && t.cause != null) t.cause!! else t
      when {
        e is TerminalException -> fail(e)
        e is AbortedExecutionException ||
            (e is CancellationException && e.cause is AbortedExecutionException) -> abort()
        else -> {
          handlerContext.fail(e)
          abort()
        }
      }
    }
  }
}

/**
 * Single, completed by the result of the given handle, converted with [mapper].
 *
 * @param R type of the handle result
 * @param T type of the converted result
 */
internal open class SingleDurableFutureImpl<R : Any?, T : Any?>(
    final override val handlerContext: HandlerContext,
    private val handle: HandlerContext.Handle<R>,
    private val mapper: (R) -> T,
) : BaseDurableFutureImpl<T>(), HandlerContext.UnresolvedFuture.Single {

  final override fun handle(): HandlerContext.Handle<R> = handle

  /** Complete with the result of the handle, returned by [HandlerContext.await]. */
  @Suppress("UNCHECKED_CAST")
  internal fun complete(handleResult: HandlerContext.Result<*>) {
    when (handleResult) {
      is HandlerContext.Result.Failure<*> -> fail(handleResult.exception())
      is HandlerContext.Result.Success<*> -> completeWith { mapper(handleResult.value() as R) }
    }
  }

  final override suspend fun tryComplete() {
    // Completed by await
  }
}

/** Maps the result of the [upstream] future. */
internal class MappedDurableFutureImpl<T : Any?, R : Any?>(
    private val upstream: BaseDurableFutureImpl<T>,
    private val successMapper: (suspend (T) -> R)?,
    private val failureMapper: (suspend (TerminalException) -> R)?,
) : BaseDurableFutureImpl<R>(), HandlerContext.UnresolvedFuture.Mapped {

  override val handlerContext: HandlerContext
    get() = upstream.handlerContext

  override fun upstream(): HandlerContext.UnresolvedFuture = upstream.unresolvedFuture()

  override fun tryCancel() {
    // Let the mappers handle the cancellation
    upstream.tryCancel()
  }

  @Suppress("UNCHECKED_CAST")
  override suspend fun tryComplete() {
    if (isDone()) {
      return
    }
    upstream.tryComplete()
    if (!upstream.isDone()) {
      return
    }

    val upstreamResult = upstream.result
    if (upstreamResult is HandlerContext.Result.Success<*>) {
      val value = upstreamResult.value() as T
      if (successMapper == null) {
        complete(value as R)
      } else {
        completeWith { successMapper.invoke(value) }
      }
    } else if (upstreamResult is HandlerContext.Result.Failure<*> && failureMapper != null) {
      completeWith { failureMapper.invoke(upstreamResult.exception()) }
    } else {
      // Failure without mapper, or aborted
      completeFrom(upstream)
    }
  }
}

/** Completes with the index of the first completed child in the list. */
internal class AnyDurableFutureImpl(private val children: List<BaseDurableFutureImpl<*>>) :
    BaseDurableFutureImpl<Int>(), HandlerContext.UnresolvedFuture.AnyOf {

  override val handlerContext: HandlerContext
    get() = children[0].handlerContext

  override fun childrenCount(): Int = children.size

  override fun child(index: Int): HandlerContext.UnresolvedFuture =
      children[index].unresolvedFuture()

  override suspend fun tryComplete() {
    if (isDone()) {
      return
    }
    val index = tryCompleteFirst(children)
    if (index >= 0) {
      complete(index)
    }
  }
}

/** Completes when all the children succeed, or fails with the first failed child in the list. */
internal class AllDurableFutureImpl(private val children: List<BaseDurableFutureImpl<*>>) :
    BaseDurableFutureImpl<Unit>(), HandlerContext.UnresolvedFuture.AllOf {

  override val handlerContext: HandlerContext
    get() = children[0].handlerContext

  override fun childrenCount(): Int = children.size

  override fun child(index: Int): HandlerContext.UnresolvedFuture =
      children[index].unresolvedFuture()

  override suspend fun tryComplete() {
    if (isDone()) {
      return
    }
    for (child in children) {
      child.tryComplete()
      if (child.isFailedOrAborted()) {
        // No need to run the mappers of the following children
        break
      }
    }
    // Evaluate on the final state, as mappers might have completed other children by awaiting them
    var allDone = true
    for (child in children) {
      if (child.isFailedOrAborted()) {
        completeFrom(child)
        return
      }
      allDone = allDone && child.isDone()
    }
    if (allDone) {
      complete(Unit)
    }
  }
}

/**
 * Try to complete the children one by one, stopping at the first completed child, so the mappers of
 * the following children don't run.
 *
 * @return the index of the first completed child in the list, or -1 if none is completed.
 */
internal suspend fun tryCompleteFirst(children: List<BaseDurableFutureImpl<*>>): Int {
  for (i in children.indices) {
    val child = children[i]
    child.tryComplete()
    if (child.isDone()) {
      // The mapper of this child might have completed a previous child by awaiting it
      for (j in 0 until i) {
        if (children[j].isDone()) {
          return j
        }
      }
      return i
    }
  }
  return -1
}

internal fun wrapAllDurableFuture(durableFutures: List<DurableFuture<*>>): DurableFuture<Unit> {
  check(durableFutures.isNotEmpty()) { "The durable futures list should be non empty" }
  // DurableFuture is sealed, BaseDurableFutureImpl is its only implementation
  @Suppress("UNCHECKED_CAST")
  return AllDurableFutureImpl(durableFutures as List<BaseDurableFutureImpl<*>>)
}

internal fun wrapAnyDurableFuture(
    durableFutures: List<DurableFuture<*>>
): BaseDurableFutureImpl<Int> {
  check(durableFutures.isNotEmpty()) { "The durable futures list should be non empty" }
  // DurableFuture is sealed, BaseDurableFutureImpl is its only implementation
  @Suppress("UNCHECKED_CAST")
  return AnyDurableFutureImpl(durableFutures as List<BaseDurableFutureImpl<*>>)
}

internal class CallDurableFutureImpl<T : Any?>
internal constructor(
    handlerContext: HandlerContext,
    callHandles: HandlerContext.CallHandles,
    responseDeserializer: (Slice) -> T,
) :
    SingleDurableFutureImpl<Slice, T>(
        handlerContext,
        callHandles.resultHandle(),
        responseDeserializer,
    ),
    CallDurableFuture<T> {

  private val invocationIdFuture =
      SingleDurableFutureImpl(handlerContext, callHandles.invocationIdHandle()) { it }

  override suspend fun invocationId(): String {
    return invocationIdFuture.await()
  }
}

internal abstract class BaseInvocationHandle<Res>
internal constructor(
    private val contextImpl: ContextImpl,
    private val responseSerde: Serde<Res>,
) : InvocationHandle<Res> {
  private val handlerContext: HandlerContext
    get() = contextImpl.handlerContext

  override suspend fun cancel() {
    checkNotInsideRun()
    val ignored = handlerContext.cancelInvocation(invocationId()).await()
  }

  override suspend fun attach(): DurableFuture<Res> {
    checkNotInsideRun()
    return SingleDurableFutureImpl(
        handlerContext,
        handlerContext.attachInvocation(invocationId()).await(),
    ) {
      responseSerde.deserialize(it)
    }
  }

  override suspend fun output(): Output<Res> {
    checkNotInsideRun()
    return SingleDurableFutureImpl(
            handlerContext,
            handlerContext.getInvocationOutput(invocationId()).await(),
        ) {
          it.map { responseSerde.deserialize(it) }
        }
        .await()
  }

  override suspend fun signal(name: String): SignalHandle {
    val resolvedId = invocationId()
    return SignalHandleImpl(contextImpl, resolvedId, name)
  }
}

internal class AwakeableImpl<T : Any?>
internal constructor(
    handlerContext: HandlerContext,
    handle: HandlerContext.Handle<Slice>,
    serde: Serde<T>,
    override val id: String,
) :
    SingleDurableFutureImpl<Slice, T>(handlerContext, handle, { serde.deserialize(it) }),
    Awakeable<T>

internal class AwakeableHandleImpl(val contextImpl: ContextImpl, val id: String) : AwakeableHandle {
  override suspend fun <T : Any> resolve(typeTag: TypeTag<T>, payload: T) {
    checkNotInsideRun()
    contextImpl.handlerContext
        .resolveAwakeable(id, contextImpl.resolveAndSerialize(typeTag, payload))
        .await()
  }

  override suspend fun reject(reason: String) {
    checkNotInsideRun()
    contextImpl.handlerContext.rejectAwakeable(id, TerminalException(reason)).await()
  }
}

internal class SignalHandleImpl(
    val contextImpl: ContextImpl,
    val invocationId: String,
    val name: String,
) : SignalHandle {
  override suspend fun <T : Any> resolve(typeTag: TypeTag<T>, payload: T) {
    checkNotInsideRun()
    contextImpl.handlerContext
        .resolveSignal(invocationId, name, contextImpl.resolveAndSerialize(typeTag, payload))
        .await()
  }

  override suspend fun reject(reason: String) {
    checkNotInsideRun()
    contextImpl.handlerContext.rejectSignal(invocationId, name, TerminalException(reason)).await()
  }
}

internal class SelectClauseImpl<T>(override val durableFuture: DurableFuture<T>) : SelectClause<T>

@PublishedApi
internal class SelectImplementation<R> : SelectBuilder<R> {

  private val clauses: MutableList<Pair<BaseDurableFutureImpl<*>, suspend (Any?) -> R>> =
      mutableListOf()

  @Suppress("UNCHECKED_CAST")
  override fun <T> SelectClause<T>.invoke(block: suspend (T) -> R) {
    clauses.add(this.durableFuture as BaseDurableFutureImpl<*> to block as suspend (Any?) -> R)
  }

  suspend fun build(): DurableFuture<R> {
    return wrapAnyDurableFuture(clauses.map { it.first }).map { index ->
      clauses[index].let { resolved -> resolved.first.await().let { resolved.second(it) } }
    }
  }
}
