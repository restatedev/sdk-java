// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.function.ThrowingFunction;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.TimeoutException;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;

/**
 * Base implementation of {@link DurableFuture}.
 *
 * <p>{@link #await()} awaits the future tree with {@link HandlerContext#await}, completing the
 * {@link Single}s with the returned results, and uses {@link #tryComplete()} to complete the other
 * nodes.
 *
 * <p>The tree nodes ({@link Node}) implement the {@link HandlerContext.UnresolvedFuture} subtypes,
 * whose methods are public. To keep them out of the public API, the public subtypes of {@link
 * DurableFuture} are not nodes themselves: they wrap a node ({@link Delegating}).
 */
abstract non-sealed class DurableFutureImpl<T> implements DurableFuture<T> {

  /** Result of an aborted future, e.g. because the invocation suspended. */
  private static final Object ABORTED = new Object();

  // Either null if not completed yet, ABORTED, or HandlerContext.Result.
  // Written only by user land, and read by the HandlerContext from other threads while awaiting.
  private volatile @Nullable Object result;

  DurableFutureImpl() {}

  @Override
  public final T await() throws TerminalException {
    ContextImpl.checkNotInsideRun();
    HandlerContext handlerContext = handlerContext();
    while (true) {
      this.tryComplete();
      if (this.isDone()) {
        return this.getOrThrow();
      }

      HandlerContext.AwaitResult awaitResult =
          Util.awaitCompletableFuture(handlerContext.await(this.unresolvedFuture()));
      if (awaitResult instanceof HandlerContext.AwaitResult.AnyCompleted anyCompleted) {
        // Complete the single before running any user code: mappers might await it.
        ((Single<?, ?>) anyCompleted.single()).complete(anyCompleted.result());
      } else if (awaitResult instanceof HandlerContext.AwaitResult.Cancelled) {
        this.tryCancel();
      } else {
        this.abort();
      }
    }
  }

  @Override
  public final T await(Duration timeout) throws TerminalException {
    return this.withTimeout(timeout).await();
  }

  @Override
  public final DurableFuture<T> withTimeout(Duration timeout) {
    HandlerContext handlerContext = handlerContext();
    DurableFuture<Void> timer =
        single(handlerContext, Util.awaitCompletableFuture(handlerContext.timer(timeout, null)));
    return DurableFuture.any(this, timer)
        .map(
            i -> {
              if (i == 1) {
                throw new TimeoutException("Timed out waiting for durable future after " + timeout);
              }
              return this.await();
            });
  }

  @Override
  public final <U> DurableFuture<U> map(ThrowingFunction<T, U> mapper) {
    return new Mapped<>(this, mapper, null);
  }

  @Override
  public final <U> DurableFuture<U> map(
      ThrowingFunction<T, U> successMapper, ThrowingFunction<TerminalException, U> failureMapper) {
    return new Mapped<>(this, successMapper, failureMapper);
  }

  @Override
  public final DurableFuture<T> mapFailure(ThrowingFunction<TerminalException, T> failureMapper) {
    return new Mapped<>(this, null, failureMapper);
  }

  abstract HandlerContext handlerContext();

  /**
   * Try to complete this future with the information already available, without awaiting: run the
   * mappers whose upstream is completed, and complete the combinators whose children are completed.
   *
   * <p>Mappers can await other futures.
   */
  abstract void tryComplete();

  /** Complete this future as cancelled, as the invocation got cancelled while awaiting it. */
  void tryCancel() {
    fail(new TerminalException(TerminalException.CANCELLED_CODE));
  }

  boolean isDone() {
    return this.result != null;
  }

  /**
   * @return the node of the future tree to await.
   */
  abstract HandlerContext.UnresolvedFuture unresolvedFuture();

  /**
   * @return the result of this completed future, or throws its failure: either {@link
   *     TerminalException} or {@link AbortedExecutionException}.
   */
  @SuppressWarnings("unchecked")
  final T getOrThrow() {
    Object result = this.result;
    if (result == ABORTED) {
      AbortedExecutionException.sneakyThrow();
    }
    return ((HandlerContext.Result<T>) result).getResultOrThrow();
  }

  final boolean isFailedOrAborted() {
    Object result = this.result;
    return result == ABORTED || result instanceof HandlerContext.Result.Failure;
  }

  final void complete(@Nullable T value) {
    if (this.result == null) {
      this.result = new HandlerContext.Result.Success<>(value);
    }
  }

  final void fail(TerminalException exception) {
    if (this.result == null) {
      this.result = new HandlerContext.Result.Failure<>(exception);
    }
  }

  final void abort() {
    if (this.result == null) {
      this.result = ABORTED;
    }
  }

  /** Complete this future with the result of the given completed future. */
  final void completeFrom(DurableFutureImpl<?> other) {
    if (this.result == null) {
      this.result = other.result;
    }
  }

  /**
   * Complete this future with the result of {@code supplier}.
   *
   * <p>A {@link TerminalException} fails this future, {@link AbortedExecutionException} aborts it,
   * while any other failure fails the invocation, and aborts this future.
   */
  final void completeWith(ThrowingSupplier<T> supplier) {
    try {
      complete(supplier.get());
    } catch (Throwable e) {
      Throwable throwable = e;
      if (throwable instanceof CompletionException && throwable.getCause() != null) {
        throwable = throwable.getCause();
      }
      if (throwable instanceof TerminalException terminalException) {
        fail(terminalException);
      } else if (throwable instanceof AbortedExecutionException) {
        abort();
      } else {
        handlerContext().fail(throwable);
        abort();
      }
    }
  }

  // -- Implementations

  /** Node of the future tree, implementing one of the {@link HandlerContext.UnresolvedFuture}. */
  abstract static class Node<T> extends DurableFutureImpl<T> {
    @Override
    public final boolean isDone() {
      return super.isDone();
    }

    @Override
    final HandlerContext.UnresolvedFuture unresolvedFuture() {
      return (HandlerContext.UnresolvedFuture) this;
    }
  }

  /** Future completed by its delegate, see {@link DurableFutureImpl}. */
  abstract static class Delegating<T> extends DurableFutureImpl<T> {
    private final DurableFutureImpl<T> delegate;

    Delegating(DurableFutureImpl<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    final HandlerContext handlerContext() {
      return delegate.handlerContext();
    }

    @Override
    final HandlerContext.UnresolvedFuture unresolvedFuture() {
      return delegate.unresolvedFuture();
    }

    @Override
    final void tryComplete() {
      if (isDone()) {
        return;
      }
      delegate.tryComplete();
      if (delegate.isDone()) {
        completeFrom(delegate);
      }
    }

    @Override
    final void tryCancel() {
      delegate.tryCancel();
    }
  }

  /**
   * Single, completed by the result of the given handle, converted with {@code mapper}.
   *
   * @param <R> type of the handle result
   * @param <T> type of the converted result
   */
  static final class Single<R, T> extends Node<T>
      implements HandlerContext.UnresolvedFuture.Single {

    private final HandlerContext handlerContext;
    private final HandlerContext.Handle<R> handle;
    private final ThrowingFunction<R, T> mapper;

    Single(
        HandlerContext handlerContext,
        HandlerContext.Handle<R> handle,
        ThrowingFunction<R, T> mapper) {
      this.handlerContext = handlerContext;
      this.handle = handle;
      this.mapper = mapper;
    }

    @Override
    final HandlerContext handlerContext() {
      return this.handlerContext;
    }

    @Override
    public final HandlerContext.Handle<R> handle() {
      return this.handle;
    }

    /** Complete with the result of the handle, returned by {@link HandlerContext#await}. */
    @SuppressWarnings("unchecked")
    final void complete(HandlerContext.Result<?> handleResult) {
      if (handleResult instanceof HandlerContext.Result.Failure<?> failure) {
        fail(failure.exception());
      } else {
        R value = ((HandlerContext.Result.Success<R>) handleResult).value();
        completeWith(() -> this.mapper.apply(value));
      }
    }

    @Override
    final void tryComplete() {
      // Completed by await
    }
  }

  /** Create a single for the given handle, with no conversion of the result. */
  static <T> DurableFuture<T> single(
      HandlerContext handlerContext, HandlerContext.Handle<T> handle) {
    return new Single<>(handlerContext, handle, ThrowingFunction.identity());
  }

  /** Create a single for the given handle, converting its result with {@code mapper}. */
  static <R, T> DurableFuture<T> single(
      HandlerContext handlerContext,
      HandlerContext.Handle<R> handle,
      ThrowingFunction<R, T> mapper) {
    return new Single<>(handlerContext, handle, mapper);
  }

  /** {@link DurableFuture} is sealed, and this is its only implementation. */
  static <T> DurableFutureImpl<T> of(DurableFuture<T> durableFuture) {
    return (DurableFutureImpl<T>) durableFuture;
  }

  /** Like {@link #of}, for a list. */
  @SuppressWarnings("unchecked")
  static List<DurableFutureImpl<?>> fromList(List<? extends DurableFuture<?>> durableFutures) {
    return (List<DurableFutureImpl<?>>) durableFutures;
  }

  /**
   * Try to complete the children one by one, stopping at the first completed child, so the mappers
   * of the following children don't run.
   *
   * @return the index of the first completed child in the list, or -1 if none is completed.
   */
  static int tryCompleteFirst(List<? extends DurableFutureImpl<?>> children) {
    for (int i = 0; i < children.size(); i++) {
      DurableFutureImpl<?> child = children.get(i);
      child.tryComplete();
      if (child.isDone()) {
        // The mapper of this child might have completed a previous child by awaiting it
        for (int j = 0; j < i; j++) {
          if (children.get(j).isDone()) {
            return j;
          }
        }
        return i;
      }
    }
    return -1;
  }

  /** Maps the result of the upstream future. */
  static final class Mapped<T, U> extends Node<U>
      implements HandlerContext.UnresolvedFuture.Mapped {

    private final DurableFutureImpl<T> upstream;
    private final @Nullable ThrowingFunction<T, U> successMapper;
    private final @Nullable ThrowingFunction<TerminalException, U> failureMapper;

    Mapped(
        DurableFutureImpl<T> upstream,
        @Nullable ThrowingFunction<T, U> successMapper,
        @Nullable ThrowingFunction<TerminalException, U> failureMapper) {
      this.upstream = upstream;
      this.successMapper = successMapper;
      this.failureMapper = failureMapper;
    }

    @Override
    HandlerContext handlerContext() {
      return upstream.handlerContext();
    }

    @Override
    public HandlerContext.UnresolvedFuture upstream() {
      return upstream.unresolvedFuture();
    }

    @Override
    void tryCancel() {
      // Let the mappers handle the cancellation
      upstream.tryCancel();
    }

    @SuppressWarnings("unchecked")
    @Override
    void tryComplete() {
      if (isDone()) {
        return;
      }
      upstream.tryComplete();
      if (!upstream.isDone()) {
        return;
      }

      Object upstreamResult = upstream.result;
      if (upstreamResult instanceof HandlerContext.Result.Success<?> success) {
        T value = (T) success.value();
        if (successMapper == null) {
          complete((U) value);
        } else {
          completeWith(() -> successMapper.apply(value));
        }
      } else if (upstreamResult instanceof HandlerContext.Result.Failure<?> failure
          && failureMapper != null) {
        completeWith(() -> failureMapper.apply(failure.exception()));
      } else {
        // Failure without mapper, or aborted
        completeFrom(upstream);
      }
    }
  }

  /** Completes with the index of the first completed child in the list. */
  static final class Any extends Node<Integer> implements HandlerContext.UnresolvedFuture.AnyOf {

    private final List<? extends DurableFutureImpl<?>> children;

    Any(List<? extends DurableFutureImpl<?>> children) {
      this.children = children;
    }

    @Override
    HandlerContext handlerContext() {
      return children.get(0).handlerContext();
    }

    @Override
    public int childrenCount() {
      return children.size();
    }

    @Override
    public HandlerContext.UnresolvedFuture child(int index) {
      return children.get(index).unresolvedFuture();
    }

    @Override
    void tryComplete() {
      if (isDone()) {
        return;
      }
      int index = tryCompleteFirst(children);
      if (index >= 0) {
        complete(index);
      }
    }
  }

  /** Completes when all the children succeed, or fails with the first failed child in the list. */
  static final class All extends Node<Void> implements HandlerContext.UnresolvedFuture.AllOf {

    private final List<? extends DurableFutureImpl<?>> children;

    All(List<? extends DurableFutureImpl<?>> children) {
      this.children = children;
    }

    @Override
    HandlerContext handlerContext() {
      return children.get(0).handlerContext();
    }

    @Override
    public int childrenCount() {
      return children.size();
    }

    @Override
    public HandlerContext.UnresolvedFuture child(int index) {
      return children.get(index).unresolvedFuture();
    }

    @Override
    void tryComplete() {
      if (isDone()) {
        return;
      }
      for (DurableFutureImpl<?> child : children) {
        child.tryComplete();
        if (child.isFailedOrAborted()) {
          // No need to run the mappers of the following children
          break;
        }
      }
      // Evaluate on the final state, as mappers might have completed other children by awaiting
      // them
      boolean allDone = true;
      for (DurableFutureImpl<?> child : children) {
        if (child.isFailedOrAborted()) {
          completeFrom(child);
          return;
        }
        allDone &= child.isDone();
      }
      if (allDone) {
        complete(null);
      }
    }
  }

  /** Completes with the result of the first completed child in the list, used by {@link Select}. */
  static final class FirstCompleted<T> extends Node<T>
      implements HandlerContext.UnresolvedFuture.AnyOf {

    private final List<? extends DurableFutureImpl<T>> children;

    FirstCompleted(List<? extends DurableFutureImpl<T>> children) {
      this.children = children;
    }

    @Override
    HandlerContext handlerContext() {
      checkNonEmpty();
      return children.get(0).handlerContext();
    }

    @Override
    public int childrenCount() {
      return children.size();
    }

    @Override
    public HandlerContext.UnresolvedFuture child(int index) {
      return children.get(index).unresolvedFuture();
    }

    @Override
    void tryComplete() {
      if (isDone()) {
        return;
      }
      checkNonEmpty();
      int index = tryCompleteFirst(children);
      if (index >= 0) {
        completeFrom(children.get(index));
      }
    }

    private void checkNonEmpty() {
      if (children.isEmpty()) {
        throw new IllegalArgumentException("Select is empty");
      }
    }
  }
}
