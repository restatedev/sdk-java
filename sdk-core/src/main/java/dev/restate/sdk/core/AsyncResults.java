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
import dev.restate.sdk.core.statemachine.NotificationValue;
import dev.restate.sdk.core.statemachine.StateMachine;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.types.AbortedExecutionException;
import dev.restate.sdk.types.TerminalException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

  static AsyncResultInternal<Integer> any(
      HandlerContextInternal contextInternal, List<AsyncResultInternal<?>> any) {
    return new AnyAsyncResult(contextInternal, any);
  }

  static AsyncResultInternal<Void> all(
      HandlerContextInternal contextInternal, List<AsyncResultInternal<?>> all) {
    return new AllAsyncResult(contextInternal, all);
  }

  interface AsyncResultInternal<T> extends AsyncResult<T> {
    boolean isDone();

    void tryCancel();

    void tryComplete(StateMachine stateMachine);

    CompletableFuture<T> publicFuture();

    Stream<Integer> uncompletedLeaves();

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
    public <U> AsyncResult<U> map(ThrowingFunction<T, CompletableFuture<U>> mapper) {
      return new MappedSingleAsyncResultInternal<>(this, mapper);
    }
  }

  static class SingleAsyncResultInternal<T> extends BaseAsyncResultInternal<T> {

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
    public HandlerContextInternal ctx() {
      return this.contextInternal;
    }
  }

  static class MappedSingleAsyncResultInternal<T, U> extends BaseAsyncResultInternal<U> {
    private final AsyncResultInternal<T> asyncResult;

    MappedSingleAsyncResultInternal(
        AsyncResultInternal<T> asyncResult, ThrowingFunction<T, CompletableFuture<U>> mapper) {
      super(
          asyncResult
              .publicFuture()
              .thenCompose(
                  t -> {
                    try {
                      CompletableFuture<U> fut = new CompletableFuture<>();
                      mapper
                          .apply(t)
                          .whenCompleteAsync(
                              (u, throwable) -> {
                                if (throwable != null) {
                                  if (ExceptionUtils.isTerminalException(throwable)) {
                                    fut.completeExceptionally(throwable);
                                  } else {
                                    asyncResult.ctx().failWithoutContextSwitch(throwable);
                                    fut.completeExceptionally(AbortedExecutionException.INSTANCE);
                                  }
                                } else {
                                  fut.complete(u);
                                }
                              },
                              asyncResult.ctx().stateMachineExecutor());
                      return fut;
                    } catch (Throwable e) {
                      if (ExceptionUtils.isTerminalException(e)) {
                        return CompletableFuture.failedFuture(e);
                      }
                      asyncResult.ctx().failWithoutContextSwitch(e);
                      return CompletableFuture.failedFuture(AbortedExecutionException.INSTANCE);
                    }
                  }));
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
    public void tryComplete(StateMachine stateMachine) {
      asyncResult.tryComplete(stateMachine);
    }

    @Override
    public Stream<Integer> uncompletedLeaves() {
      return asyncResult.uncompletedLeaves();
    }

    @Override
    public HandlerContextInternal ctx() {
      return asyncResult.ctx();
    }
  }

  static class AnyAsyncResult extends BaseAsyncResultInternal<Integer> {

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
    public void tryComplete(StateMachine stateMachine) {
      asyncResults.forEach(ar -> ar.tryComplete(stateMachine));
      for (int i = 0; i < asyncResults.size(); i++) {
        if (asyncResults.get(i).isDone()) {
          publicFuture.complete(i);
          return;
        }
      }
    }

    @Override
    public Stream<Integer> uncompletedLeaves() {
      if (publicFuture.isDone()) {
        return Stream.empty();
      }
      return asyncResults.stream().flatMap(AsyncResultInternal::uncompletedLeaves);
    }

    @Override
    public HandlerContextInternal ctx() {
      return handlerContextInternal;
    }
  }

  static class AllAsyncResult extends BaseAsyncResultInternal<Void> {

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
    public void tryComplete(StateMachine stateMachine) {
      asyncResults.forEach(ar -> ar.tryComplete(stateMachine));
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
    public Stream<Integer> uncompletedLeaves() {
      if (publicFuture.isDone()) {
        return Stream.empty();
      }
      return asyncResults.stream().flatMap(AsyncResultInternal::uncompletedLeaves);
    }

    @Override
    public HandlerContextInternal ctx() {
      return handlerContextInternal;
    }
  }
}
