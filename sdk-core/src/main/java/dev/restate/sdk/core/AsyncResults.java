// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.common.function.ThrowingFunction;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.StateMachine.NotificationValue;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

abstract class AsyncResults {

  @FunctionalInterface
  interface Completer<T> {
    void complete(NotificationValue value, CompletableFuture<T> future);
  }

  @FunctionalInterface
  interface NotificationReader {
    java.util.Optional<NotificationValue> take(int handle);
  }

  private AsyncResults() {}

  static <T> AsyncResultInternal<T> single(
      HandlerContextInternal contextInternal, int handle, Completer<T> completer) {
    return new SingleAsyncResultInternal<>(handle, completer, contextInternal);
  }

  static AsyncResultInternal<Integer> any(
      HandlerContextInternal contextInternal, List<AsyncResultInternal<?>> any) {
    return new AnyAsyncResult(contextInternal, any);
  }

  static AsyncResultInternal<Void> all(
      HandlerContextInternal contextInternal, List<AsyncResultInternal<?>> all) {
    return new AllAsyncResult(contextInternal, all);
  }

  non-sealed interface AsyncResultInternal<T> extends AsyncResult<T>, UnresolvedFuture {

    void tryCancel();

    void tryComplete(NotificationReader reader);

    CompletableFuture<T> publicFuture();

    HandlerContextInternal ctx();
  }

  abstract static class BaseAsyncResultInternal<T> implements AsyncResultInternal<T> {
    protected final CompletableFuture<T> publicFuture;

    BaseAsyncResultInternal(CompletableFuture<T> publicFuture) {
      this.publicFuture = publicFuture;
    }

    @Override
    public CompletableFuture<T> poll() {
      if (!this.isDone()) {
        ctx().pollAsyncResult(this);
      }
      return this.publicFuture;
    }

    @Override
    public boolean isDone() {
      return this.publicFuture.isDone();
    }

    @Override
    public CompletableFuture<T> publicFuture() {
      return publicFuture;
    }

    @Override
    public <U> AsyncResult<U> map(
        ThrowingFunction<T, CompletableFuture<U>> successMapper,
        ThrowingFunction<TerminalException, CompletableFuture<U>> failureMapper) {
      return new MappedSingleAsyncResultInternal<>(this, successMapper, failureMapper);
    }

    @Override
    public int singleHandle() {
      throw new UnsupportedOperationException("singleHandle is only valid for a SINGLE await node");
    }

    @Override
    public List<? extends UnresolvedFuture> combinatorChildren() {
      return List.of();
    }
  }

  static final class SingleAsyncResultInternal<T> extends BaseAsyncResultInternal<T> {

    private final int handle;
    private final Completer<T> completer;
    private final HandlerContextInternal contextInternal;

    private SingleAsyncResultInternal(
        int handle, Completer<T> completer, HandlerContextInternal contextInternal) {
      super(new CompletableFuture<>());
      this.handle = handle;
      this.completer = completer;
      this.contextInternal = contextInternal;
    }

    @Override
    public void tryCancel() {
      this.publicFuture.completeExceptionally(
          new TerminalException(TerminalException.CANCELLED_CODE));
    }

    @Override
    public void tryComplete(NotificationReader reader) {
      reader
          .take(handle)
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
    public Kind kind() {
      return Kind.SINGLE;
    }

    @Override
    public int singleHandle() {
      return handle;
    }

    @Override
    public HandlerContextInternal ctx() {
      return this.contextInternal;
    }
  }

  static final class MappedSingleAsyncResultInternal<T, U> extends BaseAsyncResultInternal<U> {
    private final AsyncResultInternal<T> asyncResult;

    MappedSingleAsyncResultInternal(
        AsyncResultInternal<T> asyncResult,
        ThrowingFunction<T, CompletableFuture<U>> successMapper,
        ThrowingFunction<TerminalException, CompletableFuture<U>> failureMapper) {
      super(compose(asyncResult.ctx(), asyncResult.publicFuture(), successMapper, failureMapper));
      this.asyncResult = asyncResult;
    }

    @Override
    public boolean isDone() {
      return asyncResult.isDone();
    }

    @Override
    public void tryCancel() {
      asyncResult.tryCancel();
    }

    @Override
    public void tryComplete(NotificationReader reader) {
      asyncResult.tryComplete(reader);
    }

    @Override
    public Kind kind() {
      // Mapper is arbitrary user code; we can't promise any specific combinator semantics.
      return Kind.UNKNOWN;
    }

    @Override
    public List<? extends UnresolvedFuture> combinatorChildren() {
      return List.of(asyncResult);
    }

    @Override
    public HandlerContextInternal ctx() {
      return asyncResult.ctx();
    }

    private static <T, U> CompletableFuture<U> compose(
        HandlerContextInternal ctx,
        CompletableFuture<T> upstreamFuture,
        ThrowingFunction<T, CompletableFuture<U>> successMapper,
        ThrowingFunction<TerminalException, CompletableFuture<U>> failureMapper) {
      CompletableFuture<U> downstreamFuture = new CompletableFuture<>();

      upstreamFuture.whenComplete(
          (t, throwable) -> {
            if (ExceptionUtils.isTerminalException(throwable)) {
              // Upstream future failed with Terminal exception
              if (failureMapper != null) {
                try {
                  failureMapper
                      .apply((TerminalException) throwable)
                      .whenComplete(
                          (u, mapperT) -> {
                            if (ExceptionUtils.isTerminalException(mapperT)) {
                              downstreamFuture.completeExceptionally(mapperT);
                            } else if (mapperT != null) {
                              ctx.fail(ExceptionUtils.unwrapCompletionException(mapperT));
                              downstreamFuture.completeExceptionally(
                                  AbortedExecutionException.INSTANCE);
                            } else {
                              downstreamFuture.complete(u);
                            }
                          });
                } catch (Throwable mapperT) {
                  if (ExceptionUtils.isTerminalException(mapperT)) {
                    downstreamFuture.completeExceptionally(mapperT);
                  } else {
                    ctx.fail(ExceptionUtils.unwrapCompletionException(mapperT));
                    downstreamFuture.completeExceptionally(AbortedExecutionException.INSTANCE);
                  }
                }
              } else {
                downstreamFuture.completeExceptionally(throwable);
              }
            } else if (throwable != null) {
              // Aborted exception/some other exception. Just propagate it through
              downstreamFuture.completeExceptionally(throwable);
            } else {
              // Success case!
              if (successMapper != null) {
                try {
                  successMapper
                      .apply(t)
                      .whenComplete(
                          (u, mapperT) -> {
                            if (ExceptionUtils.isTerminalException(mapperT)) {
                              downstreamFuture.completeExceptionally(mapperT);
                            } else if (mapperT != null) {
                              ctx.fail(ExceptionUtils.unwrapCompletionException(mapperT));
                              downstreamFuture.completeExceptionally(
                                  AbortedExecutionException.INSTANCE);
                            } else {
                              downstreamFuture.complete(u);
                            }
                          });
                } catch (Throwable mapperT) {
                  if (ExceptionUtils.isTerminalException(mapperT)) {
                    downstreamFuture.completeExceptionally(mapperT);
                  } else {
                    ctx.fail(ExceptionUtils.unwrapCompletionException(mapperT));
                    downstreamFuture.completeExceptionally(AbortedExecutionException.INSTANCE);
                  }
                }
              } else {
                // Type checked by the API itself
                //noinspection unchecked
                downstreamFuture.complete((U) t);
              }
            }
          });

      return downstreamFuture;
    }
  }

  static final class AnyAsyncResult extends BaseAsyncResultInternal<Integer> {

    private final HandlerContextInternal handlerContextInternal;
    private final List<AsyncResultInternal<?>> asyncResults;

    AnyAsyncResult(
        HandlerContextInternal handlerContextInternal, List<AsyncResultInternal<?>> asyncResults) {
      super(new CompletableFuture<>());
      this.handlerContextInternal = handlerContextInternal;
      this.asyncResults = asyncResults;
    }

    @Override
    public void tryCancel() {
      this.publicFuture.completeExceptionally(
          new TerminalException(TerminalException.CANCELLED_CODE));
    }

    @Override
    public void tryComplete(NotificationReader reader) {
      asyncResults.forEach(ar -> ar.tryComplete(reader));
      for (int i = 0; i < asyncResults.size(); i++) {
        if (asyncResults.get(i).isDone()) {
          publicFuture.complete(i);
          return;
        }
      }
    }

    @Override
    public Kind kind() {
      return Kind.FIRST_COMPLETED;
    }

    @Override
    public List<? extends UnresolvedFuture> combinatorChildren() {
      return asyncResults;
    }

    @Override
    public HandlerContextInternal ctx() {
      return handlerContextInternal;
    }
  }

  static final class AllAsyncResult extends BaseAsyncResultInternal<Void> {

    private final HandlerContextInternal handlerContextInternal;
    private final List<AsyncResultInternal<?>> asyncResults;

    AllAsyncResult(
        HandlerContextInternal handlerContextInternal, List<AsyncResultInternal<?>> asyncResults) {
      super(
          CompletableFuture.allOf(
              asyncResults.stream()
                  .map(AsyncResultInternal::publicFuture)
                  .toArray(CompletableFuture<?>[]::new)));
      this.handlerContextInternal = handlerContextInternal;
      this.asyncResults = asyncResults;
    }

    @Override
    public void tryCancel() {
      this.publicFuture.completeExceptionally(
          new TerminalException(TerminalException.CANCELLED_CODE));
    }

    @Override
    public void tryComplete(NotificationReader reader) {
      asyncResults.forEach(ar -> ar.tryComplete(reader));
      asyncResults.stream()
          .filter(ar -> ar.publicFuture().isCompletedExceptionally())
          .findFirst()
          .ifPresent(
              ar -> {
                try {
                  ar.publicFuture().getNow(null);
                } catch (CompletionException e) {
                  this.publicFuture.completeExceptionally(e.getCause());
                }
              });
    }

    @Override
    public Kind kind() {
      return Kind.ALL_SUCCEEDED_OR_FIRST_FAILED;
    }

    @Override
    public List<? extends UnresolvedFuture> combinatorChildren() {
      return asyncResults;
    }

    @Override
    public HandlerContextInternal ctx() {
      return handlerContextInternal;
    }
  }
}
