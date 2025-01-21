// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.endpoint.AsyncResult;
import dev.restate.sdk.endpoint.Result;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

abstract class DeferredResults {

  private DeferredResults() {}

  static <T> AsyncResultInternal<T> single(int entryIndex) {
    return new ResolvableSingleAsyncResult<>(null, entryIndex);
  }

  static <T> AsyncResultInternal<T> completedSingle(int entryIndex, Result<T> result) {
    return new ResolvableSingleAsyncResult<>(result, entryIndex);
  }

  static AsyncResultInternal<Integer> any(List<AsyncResultInternal<?>> any) {
    return new AnyAsyncResult(any);
  }

  static AsyncResultInternal<Void> all(List<AsyncResultInternal<?>> all) {
    return new AllAsyncResult(all);
  }

  interface AsyncResultInternal<T> extends AsyncResult<T> {

    @Nullable
    @Override
    Result<T> toResult();

    /**
     * Look at the implementation of all and any for more details.
     *
     * @see AllAsyncResult#tryResolve(int)
     * @see AnyAsyncResult#tryResolve(int)
     */
    Stream<SingleAsyncResultInternal<?>> unprocessedLeafs();
  }

  interface SingleAsyncResultInternal<T> extends AsyncResultInternal<T> {

    int entryIndex();
  }

  private abstract static class BaseAsyncResult<T> implements AsyncResultInternal<T> {

    @Nullable private Result<T> readyResult;

    BaseAsyncResult(@Nullable Result<T> result) {
      this.readyResult = result;
    }

    @Override
    public boolean isCompleted() {
      return readyResult != null;
    }

    public void resolve(Result<T> result) {
      this.readyResult = result;
    }

    @Override
    @Nullable
    public Result<T> toResult() {
      return readyResult;
    }
  }

  static class ResolvableSingleAsyncResult<T> extends BaseAsyncResult<T>
      implements SingleAsyncResultInternal<T> {

    private final int entryIndex;

    private ResolvableSingleAsyncResult(@Nullable Result<T> result, int entryIndex) {
      super(result);
      this.entryIndex = entryIndex;
    }

    @Override
    public int entryIndex() {
      return entryIndex;
    }

    @Override
    public Stream<SingleAsyncResultInternal<?>> unprocessedLeafs() {
      return Stream.of(this);
    }
  }

  abstract static class CombinatorAsyncResult<T> extends BaseAsyncResult<T> {

    // The reason to have these two data structures is to optimize the best case where we have a
    // combinator with a large number of single deferred (which can be addressed by entry index),
    // but little number of nested combinators (which cannot be addressed by an index, but needs to
    // be iterated through).
    protected final Map<Integer, SingleAsyncResultInternal<?>> unresolvedSingles;
    protected final Set<CombinatorAsyncResult<?>> unresolvedCombinators;

    CombinatorAsyncResult(
        Map<Integer, SingleAsyncResultInternal<?>> unresolvedSingles,
        Set<CombinatorAsyncResult<?>> unresolvedCombinators) {
      super(null);

      this.unresolvedSingles = unresolvedSingles;
      this.unresolvedCombinators = unresolvedCombinators;
    }

    /**
     * This method implements the resolution logic, by trying to solve its leafs and inner
     * combinator nodes.
     *
     * <p>In case the {@code newResolvedSingle} is unknown/invalid, this method will still try to
     * walk through the inner combinator nodes in order to try resolve them.
     *
     * @return true if it's resolved, that is subsequent calls to {@link #isCompleted()} return
     *     true.
     */
    abstract boolean tryResolve(int newResolvedSingle);

    /** Like {@link #tryResolve(int)}, but iteratively on the provided list. */
    boolean tryResolve(List<Integer> resolvedSingle) {
      boolean resolved = false;
      for (int newResolvedSingle : resolvedSingle) {
        resolved = tryResolve(newResolvedSingle);
      }
      return resolved;
    }

    @Override
    public Stream<SingleAsyncResultInternal<?>> unprocessedLeafs() {
      return Stream.concat(
          this.unresolvedSingles.values().stream(),
          this.unresolvedCombinators.stream().flatMap(CombinatorAsyncResult::unprocessedLeafs));
    }
  }

  static class AnyAsyncResult extends CombinatorAsyncResult<Integer> implements AsyncResult<Integer> {

    private final IdentityHashMap<AsyncResultInternal<?>, Integer> indexMapping;

    private AnyAsyncResult(List<AsyncResultInternal<?>> children) {
      super(
          children.stream()
              .filter(d -> d instanceof SingleAsyncResultInternal)
              .map(d -> (SingleAsyncResultInternal<?>) d)
              .collect(Collectors.toMap(SingleAsyncResultInternal::entryIndex, Function.identity())),
          children.stream()
              .filter(d -> d instanceof CombinatorAsyncResult)
              .map(d -> (CombinatorAsyncResult<?>) d)
              .collect(Collectors.toSet()));

      // The index mapping relies on instance hashing
      this.indexMapping = new IdentityHashMap<>();
      for (int i = 0; i < children.size(); i++) {
        this.indexMapping.put(children.get(i), i);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    boolean tryResolve(int newResolvedSingle) {
      if (this.isCompleted()) {
        return true;
      }

      SingleAsyncResultInternal<?> resolvedSingle = this.unresolvedSingles.get(newResolvedSingle);
      if (resolvedSingle != null) {
        // Resolved
        this.resolve(Result.success(this.indexMapping.get(resolvedSingle)));
        return true;
      }

      for (CombinatorAsyncResult<?> combinator : this.unresolvedCombinators) {
        if (combinator.tryResolve(newResolvedSingle)) {
          // Resolved
          this.resolve(Result.success(this.indexMapping.get(combinator)));
          return true;
        }
      }

      return false;
    }
  }

  static class AllAsyncResult extends CombinatorAsyncResult<Void> {

    private AllAsyncResult(List<AsyncResultInternal<?>> children) {
      super(
          children.stream()
              .filter(d -> d instanceof SingleAsyncResultInternal)
              .map(d -> (SingleAsyncResultInternal<?>) d)
              .collect(
                  Collectors.toMap(
                      SingleAsyncResultInternal::entryIndex,
                      Function.identity(),
                      (v1, v2) -> v1,
                      HashMap::new)),
          children.stream()
              .filter(d -> d instanceof CombinatorAsyncResult)
              .map(d -> (CombinatorAsyncResult<?>) d)
              .collect(Collectors.toCollection(HashSet::new)));
    }

    @SuppressWarnings("unchecked")
    @Override
    boolean tryResolve(int newResolvedSingle) {
      if (this.isCompleted()) {
        return true;
      }

      SingleAsyncResultInternal<?> resolvedSingle = this.unresolvedSingles.remove(newResolvedSingle);
      if (resolvedSingle != null) {
        if (!resolvedSingle.toResult().isSuccess()) {
          this.resolve((Result<Void>) resolvedSingle.toResult());
          return true;
        }
      }

      Iterator<CombinatorAsyncResult<?>> it = this.unresolvedCombinators.iterator();
      while (it.hasNext()) {
        CombinatorAsyncResult<?> combinator = it.next();
        if (combinator.tryResolve(newResolvedSingle)) {
          // Resolved
          it.remove();

          if (!combinator.toResult().isSuccess()) {
            this.resolve((Result<Void>) combinator.toResult());
            return true;
          }
        }
      }

      if (this.unresolvedSingles.isEmpty() && this.unresolvedCombinators.isEmpty()) {
        this.resolve(Result.empty());
        return true;
      }

      return false;
    }
  }
}
