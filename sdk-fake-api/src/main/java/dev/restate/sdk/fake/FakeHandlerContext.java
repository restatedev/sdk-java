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
import dev.restate.sdk.common.HandlerRequest;
import dev.restate.sdk.common.InvocationId;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
        expectations.requestHeaders(),
        null,
        null,
        null,
        "FakeService",
        "fakeHandler");
  }

  @Override
  public HeadersAccessor attemptHeaders() {
    return HeadersAccessor.wrap(Collections.emptyMap());
  }

  @Override
  public boolean canReadState() {
    return true;
  }

  @Override
  public boolean canWriteState() {
    return true;
  }

  @Override
  public boolean canReadPromises() {
    return true;
  }

  @Override
  public boolean canWritePromises() {
    return true;
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
  public CompletableFuture<Handle<Optional<Slice>>> get(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Handle<Collection<String>>> getKeys() {
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
  public CompletableFuture<Handle<Void>> timer(Duration duration, String s) {
    CompletableFuture<Void> upstreamFuture =
        expectations.completeTimerIf().test(duration, s)
            ? CompletableFuture.completedFuture(null)
            : new CompletableFuture<>();
    return CompletableFuture.completedFuture(new MockHandle<>(upstreamFuture));
  }

  @Override
  public CompletableFuture<CallHandles> call(
      Target target,
      Slice slice,
      String s,
      String limitKey,
      Collection<Map.Entry<String, String>> collection) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Handle<String>> send(
      Target target,
      Slice slice,
      String s,
      String limitKey,
      Collection<Map.Entry<String, String>> collection,
      Duration duration) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Handle<Slice>> submitRun(
      String runName, Consumer<RunCompleter> consumer) {
    var runExpectation =
        this.expectations
            .runExpectations()
            .getOrDefault(runName, ContextExpectations.RunExpectation.PASS);

    if (runExpectation == ContextExpectations.RunExpectation.DONT_EXECUTE) {
      // Don't run the side effect at all
      return CompletableFuture.completedFuture(new MockHandle<>(new CompletableFuture<>()));
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

    return CompletableFuture.completedFuture(new MockHandle<>(upstreamFuture));
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
  public CompletableFuture<Handle<Slice>> promise(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Handle<Output<Slice>>> peekPromise(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Handle<Void>> resolvePromise(String s, Slice slice) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Handle<Void>> rejectPromise(String s, TerminalException e) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Handle<Slice>> signal(String name) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> resolveSignal(String invocationId, String name, Slice payload) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> rejectSignal(
      String invocationId, String name, TerminalException reason) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Void> cancelInvocation(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Handle<Slice>> attachInvocation(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public CompletableFuture<Handle<Output<Slice>>> getInvocationOutput(String s) {
    throw new UnsupportedOperationException(
        "FakeHandlerContext doesn't currently support mocking this operation");
  }

  @Override
  public void fail(Throwable throwable) {
    sneakyThrow(throwable);
  }

  @Override
  public CompletableFuture<AwaitResult> await(UnresolvedFuture tree) {
    List<UnresolvedFuture.Single> pendingSingles = new ArrayList<>();
    collectPendingSingles(tree, pendingSingles);
    if (pendingSingles.isEmpty()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("The awaited future is already completed"));
    }

    return CompletableFuture.anyOf(
            pendingSingles.stream()
                .map(single -> ((MockHandle<?>) single.handle()).future)
                .toArray(CompletableFuture[]::new))
        .handle(
            (ignoredValue, ignoredThrowable) -> {
              // Pick the first completed single, in depth-first order
              for (UnresolvedFuture.Single single : pendingSingles) {
                CompletableFuture<?> future = ((MockHandle<?>) single.handle()).future;
                if (future.isDone()) {
                  return new AwaitResult.AnyCompleted(single, toResult(future));
                }
              }
              throw new IllegalStateException("No completed single");
            });
  }

  private static Result<?> toResult(CompletableFuture<?> future) {
    try {
      return new Result.Success<>(future.join());
    } catch (CompletionException e) {
      if (e.getCause() instanceof TerminalException terminalException) {
        return new Result.Failure<>(terminalException);
      }
      // Not a terminal failure: propagate it as is
      sneakyThrow(e.getCause());
      return null;
    }
  }

  private static void collectPendingSingles(
      UnresolvedFuture node, List<UnresolvedFuture.Single> out) {
    if (node.isDone()) {
      return;
    }
    if (node instanceof UnresolvedFuture.Single single) {
      out.add(single);
    } else if (node instanceof UnresolvedFuture.Combinator combinator) {
      for (int i = 0; i < combinator.childrenCount(); i++) {
        collectPendingSingles(combinator.child(i), out);
      }
    } else if (node instanceof UnresolvedFuture.Mapped mapped) {
      collectPendingSingles(mapped.upstream(), out);
    } else {
      throw new IllegalArgumentException("Unknown future tree node " + node.getClass());
    }
  }

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void sneakyThrow(Object e) throws E {
    throw (E) e;
  }

  private record MockHandle<T>(CompletableFuture<T> future) implements Handle<T> {}
}
