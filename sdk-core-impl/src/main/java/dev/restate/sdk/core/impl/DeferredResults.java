package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.syscalls.DeferredResult;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

abstract class DeferredResults {

  private DeferredResults() {}

  static <T> DeferredResultInternal<T> single(int entryIndex) {
    return new ResolvableSingleDeferredResult<>(null, entryIndex);
  }

  static <T> DeferredResultInternal<T> completedSingle(
      int entryIndex, ReadyResults.ReadyResultInternal<T> readyResultInternal) {
    return new ResolvableSingleDeferredResult<>(readyResultInternal, entryIndex);
  }

  static DeferredResultInternal<Object> any(List<DeferredResultInternal<?>> any) {
    return new AnyDeferredResult(any);
  }

  static DeferredResultInternal<Void> all(List<DeferredResultInternal<?>> all) {
    return new AllDeferredResult(all);
  }

  interface DeferredResultInternal<T> extends DeferredResult<T> {

    @Nullable
    @Override
    ReadyResults.ReadyResultInternal<T> toReadyResult();

    /**
     * Look at the implementation of all and any for more details.
     *
     * @see AllDeferredResult#tryResolve(int)
     * @see AnyDeferredResult#tryResolve(int)
     */
    Stream<DeferredResults.SingleDeferredResultInternal<?>> unprocessedLeafs();
  }

  interface SingleDeferredResultInternal<T> extends DeferredResultInternal<T> {

    int entryIndex();
  }

  private abstract static class BaseDeferredResult<T> implements DeferredResultInternal<T> {

    @Nullable private ReadyResults.ReadyResultInternal<T> readyResult;

    BaseDeferredResult(@Nullable ReadyResults.ReadyResultInternal<T> readyResult) {
      this.readyResult = readyResult;
    }

    @Override
    public boolean isCompleted() {
      return readyResult != null;
    }

    public void resolve(ReadyResults.ReadyResultInternal<T> readyResultInternal) {
      this.readyResult = readyResultInternal;
    }

    @Override
    @Nullable
    public ReadyResults.ReadyResultInternal<T> toReadyResult() {
      return readyResult;
    }
  }

  static class ResolvableSingleDeferredResult<T> extends BaseDeferredResult<T>
      implements SingleDeferredResultInternal<T> {

    private final int entryIndex;

    private ResolvableSingleDeferredResult(
        @Nullable ReadyResults.ReadyResultInternal<T> readyResultInternal, int entryIndex) {
      super(readyResultInternal);
      this.entryIndex = entryIndex;
    }

    @Override
    public int entryIndex() {
      return entryIndex;
    }

    @Override
    public Stream<DeferredResults.SingleDeferredResultInternal<?>> unprocessedLeafs() {
      return Stream.of(this);
    }
  }

  abstract static class CombinatorDeferredResult<T> extends BaseDeferredResult<T> {

    // The reason to have these two data structures is to optimize the best case where we have a
    // combinator with a large number of single deferred (which can be addressed by entry index),
    // but little number of nested combinators (which cannot be addressed by an index, but needs to
    // be iterated through).
    protected final Map<Integer, SingleDeferredResultInternal<?>> unresolvedSingles;
    protected final Set<CombinatorDeferredResult<?>> unresolvedCombinators;

    CombinatorDeferredResult(
        Map<Integer, SingleDeferredResultInternal<?>> unresolvedSingles,
        Set<CombinatorDeferredResult<?>> unresolvedCombinators) {
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
    public Stream<DeferredResults.SingleDeferredResultInternal<?>> unprocessedLeafs() {
      return Stream.concat(
          this.unresolvedSingles.values().stream(),
          this.unresolvedCombinators.stream().flatMap(CombinatorDeferredResult::unprocessedLeafs));
    }
  }

  static class AnyDeferredResult extends CombinatorDeferredResult<Object> {

    private AnyDeferredResult(List<DeferredResultInternal<?>> children) {
      super(
          children.stream()
              .filter(d -> d instanceof SingleDeferredResultInternal)
              .map(d -> (SingleDeferredResultInternal<?>) d)
              .collect(
                  Collectors.toMap(SingleDeferredResultInternal::entryIndex, Function.identity())),
          children.stream()
              .filter(d -> d instanceof CombinatorDeferredResult)
              .map(d -> (CombinatorDeferredResult<?>) d)
              .collect(Collectors.toSet()));
    }

    @SuppressWarnings("unchecked")
    @Override
    boolean tryResolve(int newResolvedSingle) {
      if (this.isCompleted()) {
        return true;
      }

      SingleDeferredResultInternal<?> resolvedSingle =
          this.unresolvedSingles.get(newResolvedSingle);
      if (resolvedSingle != null) {
        // Resolved
        this.resolve((ReadyResults.ReadyResultInternal<Object>) resolvedSingle.toReadyResult());
        return true;
      }

      for (CombinatorDeferredResult<?> combinator : this.unresolvedCombinators) {
        if (combinator.tryResolve(newResolvedSingle)) {
          // Resolved
          this.resolve((ReadyResults.ReadyResultInternal<Object>) combinator.toReadyResult());
          return true;
        }
      }

      return false;
    }
  }

  static class AllDeferredResult extends CombinatorDeferredResult<Void> {

    private AllDeferredResult(List<DeferredResultInternal<?>> children) {
      super(
          children.stream()
              .filter(d -> d instanceof SingleDeferredResultInternal)
              .map(d -> (SingleDeferredResultInternal<?>) d)
              .collect(
                  Collectors.toMap(
                      SingleDeferredResultInternal::entryIndex,
                      Function.identity(),
                      (v1, v2) -> v1,
                      HashMap::new)),
          children.stream()
              .filter(d -> d instanceof CombinatorDeferredResult)
              .map(d -> (CombinatorDeferredResult<?>) d)
              .collect(Collectors.toCollection(HashSet::new)));
    }

    @SuppressWarnings("unchecked")
    @Override
    boolean tryResolve(int newResolvedSingle) {
      if (this.isCompleted()) {
        return true;
      }

      SingleDeferredResultInternal<?> resolvedSingle =
          this.unresolvedSingles.remove(newResolvedSingle);
      if (resolvedSingle != null) {
        if (!resolvedSingle.toReadyResult().isSuccess()) {
          this.resolve((ReadyResults.ReadyResultInternal<Void>) resolvedSingle.toReadyResult());
          return true;
        }
      }

      Iterator<CombinatorDeferredResult<?>> it = this.unresolvedCombinators.iterator();
      while (it.hasNext()) {
        CombinatorDeferredResult<?> combinator = it.next();
        if (combinator.tryResolve(newResolvedSingle)) {
          // Resolved
          it.remove();

          if (!combinator.toReadyResult().isSuccess()) {
            this.resolve((ReadyResults.ReadyResultInternal<Void>) combinator.toReadyResult());
            return true;
          }
        }
      }

      if (this.unresolvedSingles.isEmpty() && this.unresolvedCombinators.isEmpty()) {
        this.resolve(ReadyResults.empty());
        return true;
      }

      return false;
    }
  }
}
