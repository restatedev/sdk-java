package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.syscalls.DeferredResult;
import java.util.List;
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

    // Return only single leafs, and not combinators
    Stream<DeferredResults.SingleDeferredResultInternal<?>> leafs();
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

    @SuppressWarnings("unchecked")
    public void resolve(ReadyResults.ReadyResultInternal<?> readyResultInternal) {
      this.readyResult = (ReadyResults.ReadyResultInternal<T>) readyResultInternal;
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
    public Stream<DeferredResults.SingleDeferredResultInternal<?>> leafs() {
      return Stream.of(this);
    }
  }

  abstract static class CombinatorDeferredResult<T> extends BaseDeferredResult<T> {
    final List<DeferredResultInternal<?>> children;

    private CombinatorDeferredResult(List<DeferredResultInternal<?>> children) {
      super(null);
      this.children = children;
    }

    /** Returns true if it's resolved, that is {@link #isCompleted()} returns true. */
    abstract boolean tryResolve(List<Integer> resolvedSingles);

    @Override
    public Stream<DeferredResults.SingleDeferredResultInternal<?>> leafs() {
      return this.children.stream().flatMap(DeferredResultInternal::leafs);
    }
  }

  static class AnyDeferredResult extends CombinatorDeferredResult<Object> {

    private AnyDeferredResult(List<DeferredResultInternal<?>> children) {
      super(children);
    }

    @Override
    boolean tryResolve(List<Integer> resolvedSingles) {
      if (this.isCompleted()) {
        return true;
      }

      for (DeferredResultInternal<?> child : this.children) {
        boolean resolved;
        if (child instanceof CombinatorDeferredResult) {
          resolved = ((CombinatorDeferredResult<?>) child).tryResolve(resolvedSingles);
        } else {
          resolved =
              resolvedSingles.contains(((SingleDeferredResultInternal<?>) child).entryIndex());
        }

        if (resolved) {
          this.resolve(child.toReadyResult());
          return true;
        }
      }

      return false;
    }
  }

  static class AllDeferredResult extends CombinatorDeferredResult<Void> {

    private AllDeferredResult(List<DeferredResultInternal<?>> childs) {
      super(childs);
    }

    @Override
    boolean tryResolve(List<Integer> resolvedSingles) {
      if (this.isCompleted()) {
        return true;
      }

      for (DeferredResultInternal<?> child : this.children) {
        boolean resolved;
        if (child instanceof CombinatorDeferredResult) {
          resolved = ((CombinatorDeferredResult<?>) child).tryResolve(resolvedSingles);
        } else {
          resolved =
              resolvedSingles.contains(((SingleDeferredResultInternal<?>) child).entryIndex());
        }

        if (!resolved) {
          return false;
        }
        if (!child.toReadyResult().isSuccess()) {
          this.resolve(child.toReadyResult());
          return true;
        }
      }

      this.resolve(ReadyResults.empty());
      return true;
    }
  }
}
