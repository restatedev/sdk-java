// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.syscalls.Deferred;
import dev.restate.sdk.common.syscalls.Result;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

abstract class DeferredResults {

  private DeferredResults() {}

  static <T> DeferredInternal<T> single(int entryIndex) {
    return new ResolvableSingleDeferred<>(null, entryIndex);
  }

  static <T> DeferredInternal<T> completedSingle(int entryIndex, Result<T> result) {
    return new ResolvableSingleDeferred<>(result, entryIndex);
  }

  static DeferredInternal<Integer> any(List<DeferredInternal<?>> any) {
    return new AnyDeferred(any);
  }

  static DeferredInternal<Void> all(List<DeferredInternal<?>> all) {
    return new AllDeferred(all);
  }

  interface DeferredInternal<T> extends Deferred<T> {

    @Nullable
    @Override
    Result<T> toResult();

    /**
     * Look at the implementation of all and any for more details.
     *
     * @see AllDeferred#tryResolve(int)
     * @see AnyDeferred#tryResolve(int)
     */
    Stream<SingleDeferredInternal<?>> unprocessedLeafs();
  }

  interface SingleDeferredInternal<T> extends DeferredInternal<T> {

    int entryIndex();
  }

  private abstract static class BaseDeferred<T> implements DeferredInternal<T> {

    @Nullable private Result<T> readyResult;

    BaseDeferred(@Nullable Result<T> result) {
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

  static class ResolvableSingleDeferred<T> extends BaseDeferred<T>
      implements SingleDeferredInternal<T> {

    private final int entryIndex;

    private ResolvableSingleDeferred(@Nullable Result<T> result, int entryIndex) {
      super(result);
      this.entryIndex = entryIndex;
    }

    @Override
    public int entryIndex() {
      return entryIndex;
    }

    @Override
    public Stream<SingleDeferredInternal<?>> unprocessedLeafs() {
      return Stream.of(this);
    }
  }

  abstract static class CombinatorDeferred<T> extends BaseDeferred<T> {

    // The reason to have these two data structures is to optimize the best case where we have a
    // combinator with a large number of single deferred (which can be addressed by entry index),
    // but little number of nested combinators (which cannot be addressed by an index, but needs to
    // be iterated through).
    protected final Map<Integer, SingleDeferredInternal<?>> unresolvedSingles;
    protected final Set<CombinatorDeferred<?>> unresolvedCombinators;

    CombinatorDeferred(
        Map<Integer, SingleDeferredInternal<?>> unresolvedSingles,
        Set<CombinatorDeferred<?>> unresolvedCombinators) {
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
    public Stream<SingleDeferredInternal<?>> unprocessedLeafs() {
      return Stream.concat(
          this.unresolvedSingles.values().stream(),
          this.unresolvedCombinators.stream().flatMap(CombinatorDeferred::unprocessedLeafs));
    }
  }

  static class AnyDeferred extends CombinatorDeferred<Integer> implements Deferred<Integer> {

    private final IdentityHashMap<DeferredInternal<?>, Integer> indexMapping;

    private AnyDeferred(List<DeferredInternal<?>> children) {
      super(
          children.stream()
              .filter(d -> d instanceof SingleDeferredInternal)
              .map(d -> (SingleDeferredInternal<?>) d)
              .collect(Collectors.toMap(SingleDeferredInternal::entryIndex, Function.identity())),
          children.stream()
              .filter(d -> d instanceof CombinatorDeferred)
              .map(d -> (CombinatorDeferred<?>) d)
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

      SingleDeferredInternal<?> resolvedSingle = this.unresolvedSingles.get(newResolvedSingle);
      if (resolvedSingle != null) {
        // Resolved
        this.resolve(Result.success(this.indexMapping.get(resolvedSingle)));
        return true;
      }

      for (CombinatorDeferred<?> combinator : this.unresolvedCombinators) {
        if (combinator.tryResolve(newResolvedSingle)) {
          // Resolved
          this.resolve(Result.success(this.indexMapping.get(combinator)));
          return true;
        }
      }

      return false;
    }
  }

  static class AllDeferred extends CombinatorDeferred<Void> {

    private AllDeferred(List<DeferredInternal<?>> children) {
      super(
          children.stream()
              .filter(d -> d instanceof SingleDeferredInternal)
              .map(d -> (SingleDeferredInternal<?>) d)
              .collect(
                  Collectors.toMap(
                      SingleDeferredInternal::entryIndex,
                      Function.identity(),
                      (v1, v2) -> v1,
                      HashMap::new)),
          children.stream()
              .filter(d -> d instanceof CombinatorDeferred)
              .map(d -> (CombinatorDeferred<?>) d)
              .collect(Collectors.toCollection(HashSet::new)));
    }

    @SuppressWarnings("unchecked")
    @Override
    boolean tryResolve(int newResolvedSingle) {
      if (this.isCompleted()) {
        return true;
      }

      SingleDeferredInternal<?> resolvedSingle = this.unresolvedSingles.remove(newResolvedSingle);
      if (resolvedSingle != null) {
        if (!resolvedSingle.toResult().isSuccess()) {
          this.resolve((Result<Void>) resolvedSingle.toResult());
          return true;
        }
      }

      Iterator<CombinatorDeferred<?>> it = this.unresolvedCombinators.iterator();
      while (it.hasNext()) {
        CombinatorDeferred<?> combinator = it.next();
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
