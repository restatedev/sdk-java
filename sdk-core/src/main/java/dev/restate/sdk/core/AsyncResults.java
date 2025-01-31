// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.core.statemachine.NotificationValue;
import dev.restate.sdk.core.statemachine.StateMachine;
import dev.restate.sdk.definition.AsyncResult;
import dev.restate.sdk.types.AbortedExecutionException;
import dev.restate.sdk.types.TerminalException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

abstract class AsyncResults {

  @FunctionalInterface
  interface Completer<T> {
    void complete(NotificationValue value, CompletableFuture<T> future);
  }

  private AsyncResults() {}

  static <T> AsyncResultInternal<T> single(
      HandlerContextInternal contextInternal, int handle, Completer<T> completer) {
    return new SingleAsyncResultInternal<>(handle, completer, contextInternal);
  }

  static AsyncResultInternal<Integer> any(List<AsyncResultInternal<?>> any) {
    //    return new AnyAsyncResult(any);
    return null;
  }

  static AsyncResultInternal<Void> all(List<AsyncResultInternal<?>> all) {
    //    return new AllAsyncResult(all);
    return null;
  }

  interface AsyncResultInternal<T> extends AsyncResult<T> {
    boolean isDone();

    void tryCancel();

    void tryComplete(StateMachine stateMachine);

    Stream<Integer> uncompletedLeaves();
  }

  static class SingleAsyncResultInternal<T> implements AsyncResultInternal<T> {

    private final int handle;
    private final Completer<T> completer;
    private final HandlerContextInternal contextInternal;
    private final CompletableFuture<T> publicFuture;

    private SingleAsyncResultInternal(
        int handle, Completer<T> completer, HandlerContextInternal contextInternal) {
      this.handle = handle;
      this.completer = completer;
      this.contextInternal = contextInternal;
      this.publicFuture = new CompletableFuture<>();
    }

    @Override
    public boolean isDone() {
      return this.publicFuture.isDone();
    }

    @Override
    public void tryCancel() {
      publicFuture.completeExceptionally(new TerminalException(TerminalException.CANCELLED_CODE));
    }

    @Override
    public void tryComplete(StateMachine stateMachine) {
      stateMachine
          .takeNotification(handle)
          .ifPresent(
              value -> {
                try {
                  completer.complete(value, publicFuture);
                } catch (Throwable e) {
                  contextInternal.fail(e);
                  publicFuture.completeExceptionally(AbortedExecutionException.INSTANCE);
                }
              });
    }

    @Override
    public Stream<Integer> uncompletedLeaves() {
      if (publicFuture.isDone()) {
        return Stream.empty();
      }
      return Stream.of(handle);
    }

    @Override
    public CompletableFuture<T> poll() {
      if (!publicFuture.isDone()) {
        contextInternal.pollAsyncResult(this);
      }
      return publicFuture;
    }
  }

  //  private abstract static class BaseAsyncResult<T> implements AsyncResultInternal<T> {
  //
  //    @Nullable private Result<T> readyResult;
  //
  //    BaseAsyncResult(@Nullable Result<T> result) {
  //      this.readyResult = result;
  //    }
  //
  //    @Override
  //    public boolean isCompleted() {
  //      return readyResult != null;
  //    }
  //
  //    public void resolve(Result<T> result) {
  //      this.readyResult = result;
  //    }
  //
  //    @Override
  //    @Nullable
  //    public Result<T> toResult() {
  //      return readyResult;
  //    }
  //  }

  //  abstract static class CombinatorAsyncResult<T> extends BaseAsyncResult<T> {
  //
  //    // The reason to have these two data structures is to optimize the best case where we have a
  //    // combinator with a large number of single deferred (which can be addressed by entry
  // index),
  //    // but little number of nested combinators (which cannot be addressed by an index, but needs
  // to
  //    // be iterated through).
  //    protected final Map<Integer, SingleAsyncResultInternal<?>> unresolvedSingles;
  //    protected final Set<CombinatorAsyncResult<?>> unresolvedCombinators;
  //
  //    CombinatorAsyncResult(
  //        Map<Integer, SingleAsyncResultInternal<?>> unresolvedSingles,
  //        Set<CombinatorAsyncResult<?>> unresolvedCombinators) {
  //      super(null);
  //
  //      this.unresolvedSingles = unresolvedSingles;
  //      this.unresolvedCombinators = unresolvedCombinators;
  //    }
  //
  //    /**
  //     * This method implements the resolution logic, by trying to solve its leafs and inner
  //     * combinator nodes.
  //     *
  //     * <p>In case the {@code newResolvedSingle} is unknown/invalid, this method will still try
  // to
  //     * walk through the inner combinator nodes in order to try resolve them.
  //     *
  //     * @return true if it's resolved, that is subsequent calls to {@link #isCompleted()} return
  //     *     true.
  //     */
  //    abstract boolean tryResolve(int newResolvedSingle);
  //
  //    /** Like {@link #tryResolve(int)}, but iteratively on the provided list. */
  //    boolean tryResolve(List<Integer> resolvedSingle) {
  //      boolean resolved = false;
  //      for (int newResolvedSingle : resolvedSingle) {
  //        resolved = tryResolve(newResolvedSingle);
  //      }
  //      return resolved;
  //    }
  //
  //    @Override
  //    public Stream<SingleAsyncResultInternal<?>> unprocessedLeafs() {
  //      return Stream.concat(
  //          this.unresolvedSingles.values().stream(),
  //          this.unresolvedCombinators.stream().flatMap(CombinatorAsyncResult::unprocessedLeafs));
  //    }
  //  }
  //
  //  static class AnyAsyncResult extends CombinatorAsyncResult<Integer> implements
  // AsyncResult<Integer> {
  //
  //    private final IdentityHashMap<AsyncResultInternal<?>, Integer> indexMapping;
  //
  //    private AnyAsyncResult(List<AsyncResultInternal<?>> children) {
  //      super(
  //          children.stream()
  //              .filter(d -> d instanceof SingleAsyncResultInternal)
  //              .map(d -> (SingleAsyncResultInternal<?>) d)
  //              .collect(Collectors.toMap(SingleAsyncResultInternal::entryIndex,
  // Function.identity())),
  //          children.stream()
  //              .filter(d -> d instanceof CombinatorAsyncResult)
  //              .map(d -> (CombinatorAsyncResult<?>) d)
  //              .collect(Collectors.toSet()));
  //
  //      // The index mapping relies on instance hashing
  //      this.indexMapping = new IdentityHashMap<>();
  //      for (int i = 0; i < children.size(); i++) {
  //        this.indexMapping.put(children.get(i), i);
  //      }
  //    }
  //
  //    @SuppressWarnings("unchecked")
  //    @Override
  //    boolean tryResolve(int newResolvedSingle) {
  //      if (this.isCompleted()) {
  //        return true;
  //      }
  //
  //      SingleAsyncResultInternal<?> resolvedSingle =
  // this.unresolvedSingles.get(newResolvedSingle);
  //      if (resolvedSingle != null) {
  //        // Resolved
  //        this.resolve(Result.success(this.indexMapping.get(resolvedSingle)));
  //        return true;
  //      }
  //
  //      for (CombinatorAsyncResult<?> combinator : this.unresolvedCombinators) {
  //        if (combinator.tryResolve(newResolvedSingle)) {
  //          // Resolved
  //          this.resolve(Result.success(this.indexMapping.get(combinator)));
  //          return true;
  //        }
  //      }
  //
  //      return false;
  //    }
  //  }
  //
  //  static class AllAsyncResult extends CombinatorAsyncResult<Void> {
  //
  //    private AllAsyncResult(List<AsyncResultInternal<?>> children) {
  //      super(
  //          children.stream()
  //              .filter(d -> d instanceof SingleAsyncResultInternal)
  //              .map(d -> (SingleAsyncResultInternal<?>) d)
  //              .collect(
  //                  Collectors.toMap(
  //                      SingleAsyncResultInternal::entryIndex,
  //                      Function.identity(),
  //                      (v1, v2) -> v1,
  //                      HashMap::new)),
  //          children.stream()
  //              .filter(d -> d instanceof CombinatorAsyncResult)
  //              .map(d -> (CombinatorAsyncResult<?>) d)
  //              .collect(Collectors.toCollection(HashSet::new)));
  //    }
  //
  //    @SuppressWarnings("unchecked")
  //    @Override
  //    boolean tryResolve(int newResolvedSingle) {
  //      if (this.isCompleted()) {
  //        return true;
  //      }
  //
  //      SingleAsyncResultInternal<?> resolvedSingle =
  // this.unresolvedSingles.remove(newResolvedSingle);
  //      if (resolvedSingle != null) {
  //        if (!resolvedSingle.toResult().isSuccess()) {
  //          this.resolve((Result<Void>) resolvedSingle.toResult());
  //          return true;
  //        }
  //      }
  //
  //      Iterator<CombinatorAsyncResult<?>> it = this.unresolvedCombinators.iterator();
  //      while (it.hasNext()) {
  //        CombinatorAsyncResult<?> combinator = it.next();
  //        if (combinator.tryResolve(newResolvedSingle)) {
  //          // Resolved
  //          it.remove();
  //
  //          if (!combinator.toResult().isSuccess()) {
  //            this.resolve((Result<Void>) combinator.toResult());
  //            return true;
  //          }
  //        }
  //      }
  //
  //      if (this.unresolvedSingles.isEmpty() && this.unresolvedCombinators.isEmpty()) {
  //        this.resolve(Result.empty());
  //        return true;
  //      }
  //
  //      return false;
  //    }
  //  }
}
