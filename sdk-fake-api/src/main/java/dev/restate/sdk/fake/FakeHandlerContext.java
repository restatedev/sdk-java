// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.fake;

import dev.restate.common.Output;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.common.function.ThrowingFunction;
import dev.restate.sdk.common.HandlerRequest;
import dev.restate.sdk.common.InvocationId;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.ExceptionUtils;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;

/** Implementation of {@link HandlerContext}, this implements the mocking behavior. */
@org.jetbrains.annotations.ApiStatus.Experimental
class FakeHandlerContext implements HandlerContext {

  private final ContextExpectations expectations;

  FakeHandlerContext(ContextExpectations expectations) {
    this.expectations = expectations;
  }

  @Override
  public String objectKey() {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public HandlerRequest request() {
    return new HandlerRequest(
        new InvocationId() {
          @Override
          public long toRandomSeed() {
            return expectations.randomSeed();
          }

          @Override
          public String toString() {
            return expectations.invocationId();
          }
        },
        Context.root(),
        Slice.EMPTY,
        expectations.requestHeaders());
  }

  @Override
  public CompletableFuture<Void> writeOutput(Slice slice) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> writeOutput(TerminalException e) {
    throw e;
  }

  @Override
  public CompletableFuture<AsyncResult<Optional<Slice>>> get(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<Collection<String>>> getKeys() {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> clear(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> clearAll() {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> set(String s, Slice slice) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> timer(Duration duration, String s) {
    CompletableFuture<Void> upstreamFuture =
        expectations.completeTimerIf().test(duration, s)
            ? CompletableFuture.completedFuture(null)
            : new CompletableFuture<>();
    return CompletableFuture.completedFuture(new MockAsyncResult<>(this, upstreamFuture));
  }

  @Override
  public CompletableFuture<CallResult> call(
      Target target, Slice slice, String s, Collection<Map.Entry<String, String>> collection) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<String>> send(
      Target target,
      Slice slice,
      String s,
      Collection<Map.Entry<String, String>> collection,
      Duration duration) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> submitRun(
      String runName, Consumer<RunCompleter> consumer) {
    var runExpectation =
        this.expectations
            .runExpectations()
            .getOrDefault(runName, ContextExpectations.RunExpectation.PASS);

    if (runExpectation == ContextExpectations.RunExpectation.DONT_EXECUTE) {
      // Don't run the side effect at all
      return CompletableFuture.completedFuture(
          new MockAsyncResult<>(this, new CompletableFuture<>()));
    }

    CompletableFuture<Slice> upstreamFuture =
        CompletableFuture.supplyAsync(
                () -> {
                  var innerFut = new CompletableFuture<Slice>();
                  consumer.accept(
                      new RunCompleter() {
                        @Override
                        public void proposeSuccess(Slice slice) {
                          innerFut.complete(slice);
                        }

                        @Override
                        public void proposeFailure(Throwable throwable, RetryPolicy retryPolicy) {
                          if (throwable instanceof TerminalException) {
                            innerFut.completeExceptionally(throwable);
                          } else {
                            if (runExpectation == ContextExpectations.RunExpectation.DONT_RETRY) {
                              if (retryPolicy.getMaxAttempts() == null
                                  && retryPolicy.getMaxDuration() == null) {
                                Assertions.fail(
                                    "ContextExpectations.dontRetryRun can be used only on ctx.run with a retry policy setting either maxAttempts or maxDuration.\nRestate stops retrying, and rethrows a TerminalException, only when one of those two values (or both) are set.\nCheck https://docs.restate.dev/develop/java/durable-steps#error-handling-and-retry-policies for more details.");
                              }
                              throw new TerminalException(throwable.toString());
                            } else {
                              sneakyThrow(throwable);
                            }
                          }
                        }
                      });
                  return innerFut;
                })
            .thenCompose(Function.identity());

    return CompletableFuture.completedFuture(new MockAsyncResult<>(this, upstreamFuture));
  }

  @Override
  public CompletableFuture<Awakeable> awakeable() {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> resolveAwakeable(String s, Slice slice) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> rejectAwakeable(String s, TerminalException e) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> promise(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> peekPromise(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> resolvePromise(String s, Slice slice) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<Void>> rejectPromise(String s, TerminalException e) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> cancelInvocation(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<Slice>> attachInvocation(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<AsyncResult<Output<Slice>>> getInvocationOutput(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public void fail(Throwable throwable) {
    sneakyThrow(throwable);
  }

  @Override
  public AsyncResult<Integer> createAnyAsyncResult(List<AsyncResult<?>> list) {
    CompletableFuture<Integer> upstreamFuture = new CompletableFuture<>();
    for (int i = 0; i < list.size(); i++) {
      int finalI = i;
      list.get(i)
          .poll()
          .whenComplete(
              (res, t) -> {
                if (t != null && !(t instanceof TerminalException)) {
                  upstreamFuture.completeExceptionally(t);
                } else {
                  upstreamFuture.complete(finalI);
                }
              });
    }
    return new MockAsyncResult<>(this, upstreamFuture);
  }

  @Override
  public AsyncResult<Void> createAllAsyncResult(List<AsyncResult<?>> list) {
    CompletableFuture<Void> upstreamFuture = new CompletableFuture<>();
    AtomicInteger completedCount = new AtomicInteger(0);
    for (int i = 0; i < list.size(); i++) {
      list.get(i)
          .poll()
          .whenComplete(
              (res, t) -> {
                if (t != null && !(t instanceof TerminalException)) {
                  upstreamFuture.completeExceptionally(t);
                } else {
                  var completed = completedCount.addAndGet(1);
                  if (completed == list.size()) {
                    upstreamFuture.complete(null);
                  }
                }
              });
    }
    return new MockAsyncResult<>(this, upstreamFuture);
  }

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void sneakyThrow(Object e) throws E {
    throw (E) e;
  }

  private static class MockAsyncResult<T> implements AsyncResult<T> {
    private final CompletableFuture<T> upstreamFuture;
    private final HandlerContext ctx;

    private MockAsyncResult(HandlerContext ctx, CompletableFuture<T> upstreamFuture) {
      this.upstreamFuture = upstreamFuture;
      this.ctx = ctx;
    }

    @Override
    public CompletableFuture<T> poll() {
      return upstreamFuture;
    }

    @Override
    public HandlerContext ctx() {
      return ctx;
    }

    @Override
    public <U> AsyncResult<U> map(
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
                      .whenCompleteAsync(
                          (u, mapperT) -> {
                            if (ExceptionUtils.isTerminalException(mapperT)) {
                              downstreamFuture.completeExceptionally(mapperT);
                            } else if (mapperT != null) {
                              downstreamFuture.completeExceptionally(mapperT);
                            } else {
                              downstreamFuture.complete(u);
                            }
                          });
                } catch (Throwable mapperT) {
                  if (ExceptionUtils.isTerminalException(mapperT)) {
                    downstreamFuture.completeExceptionally(mapperT);
                  } else {
                    downstreamFuture.completeExceptionally(mapperT);
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
                      .whenCompleteAsync(
                          (u, mapperT) -> {
                            if (ExceptionUtils.isTerminalException(mapperT)) {
                              downstreamFuture.completeExceptionally(mapperT);
                            } else if (mapperT != null) {
                              downstreamFuture.completeExceptionally(mapperT);
                            } else {
                              downstreamFuture.complete(u);
                            }
                          });
                } catch (Throwable mapperT) {
                  if (ExceptionUtils.isTerminalException(mapperT)) {
                    downstreamFuture.completeExceptionally(mapperT);
                  } else {
                    downstreamFuture.completeExceptionally(mapperT);
                  }
                }
              } else {
                // Type checked by the API itself
                //noinspection unchecked
                downstreamFuture.complete((U) t);
              }
            }
          });

      return new MockAsyncResult<>(this.ctx, downstreamFuture);
    }
  }
}
