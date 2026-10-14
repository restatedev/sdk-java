// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import dev.restate.common.Output;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.common.*;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Internal interface to access Restate functionalities. Users can use the ad-hoc RestateContext
 * interfaces provided by the various implementations.
 *
 * <p>The operations producing a result return a {@link Handle} to it. To get the result, build a
 * future tree out of the handles ({@link UnresolvedFuture}) and {@link #await(UnresolvedFuture)}
 * it.
 */
public interface HandlerContext {

  String objectKey();

  HandlerRequest request();

  /**
   * Restate-protocol-level HTTP headers received on the current invocation attempt. Differs across
   * retries.
   */
  HeadersAccessor attemptHeaders();

  boolean canReadState();

  boolean canWriteState();

  boolean canReadPromises();

  boolean canWritePromises();

  // ----- IO
  // Note: These are not supposed to be exposed in the user's facing Context API.

  @Deprecated(forRemoval = true)
  CompletableFuture<Void> writeOutput(Slice value);

  @Deprecated(forRemoval = true)
  CompletableFuture<Void> writeOutput(TerminalException exception);

  // ----- State

  CompletableFuture<Handle<Optional<Slice>>> get(String name);

  CompletableFuture<Handle<Collection<String>>> getKeys();

  CompletableFuture<Void> clear(String name);

  CompletableFuture<Void> clearAll();

  CompletableFuture<Void> set(String name, Slice value);

  CompletableFuture<Handle<Void>> timer(Duration duration, @Nullable String name);

  record CallHandles(Handle<String> invocationIdHandle, Handle<Slice> resultHandle) {}

  @Deprecated
  default CompletableFuture<CallHandles> call(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers) {
    return call(target, parameter, idempotencyKey, null, headers);
  }

  CompletableFuture<CallHandles> call(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers);

  @Deprecated
  default CompletableFuture<Handle<String>> send(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay) {
    return send(target, parameter, idempotencyKey, null, headers, delay);
  }

  CompletableFuture<Handle<String>> send(
      Target target,
      Slice parameter,
      @Nullable String idempotencyKey,
      @Nullable String limitKey,
      @Nullable Collection<Map.Entry<String, String>> headers,
      @Nullable Duration delay);

  interface RunCompleter {
    void proposeSuccess(Slice toWrite);

    void proposeFailure(Throwable toWrite, @Nullable RetryPolicy retryPolicy);
  }

  CompletableFuture<Handle<Slice>> submitRun(@Nullable String name, Consumer<RunCompleter> closure);

  record Awakeable(String id, Handle<Slice> handle) {}

  CompletableFuture<Awakeable> awakeable();

  CompletableFuture<Void> resolveAwakeable(String id, Slice payload);

  CompletableFuture<Void> rejectAwakeable(String id, TerminalException reason);

  CompletableFuture<Handle<Slice>> promise(String key);

  CompletableFuture<Handle<Output<Slice>>> peekPromise(String key);

  CompletableFuture<Handle<Void>> resolvePromise(String key, Slice payload);

  CompletableFuture<Handle<Void>> rejectPromise(String key, TerminalException reason);

  // ----- Named signals
  //
  // Signals are identified by (invocationId, name). Unlike awakeables, signals do not need to be
  // pre-registered: the resolution can arrive before or after the handler starts waiting.

  CompletableFuture<Handle<Slice>> signal(String name);

  CompletableFuture<Void> resolveSignal(String invocationId, String name, Slice payload);

  CompletableFuture<Void> rejectSignal(String invocationId, String name, TerminalException reason);

  CompletableFuture<Void> cancelInvocation(String invocationId);

  CompletableFuture<Handle<Slice>> attachInvocation(String invocationId);

  CompletableFuture<Handle<Output<Slice>>> getInvocationOutput(String invocationId);

  void fail(Throwable cause);

  // ----- Await

  /**
   * Await the given future tree, until either one of its singles completes, or the invocation is
   * cancelled or aborted.
   *
   * <p>At most one single is completed per invocation of this method: when it returns {@link
   * AwaitResult.AnyCompleted}, the caller MUST store the result in the returned single, otherwise
   * it is lost. The caller should then re-evaluate the tree, and invoke this method again until the
   * tree is completed.
   *
   * <p>The tree is read concurrently while the returned future is not completed, and MUST NOT be
   * modified in the meantime. Nodes that are already done are skipped, and invoking this method on
   * a tree that is already done is an error.
   */
  CompletableFuture<AwaitResult> await(UnresolvedFuture tree);

  /**
   * Opaque handle to the result of an operation, returned by the {@link HandlerContext} operations,
   * and used to build the {@link UnresolvedFuture.Single} nodes of a future tree.
   *
   * @param <T> type of the result
   */
  interface Handle<T> {}

  /**
   * Node of a future tree, awaited with {@link #await(UnresolvedFuture)}. A node is either a {@link
   * Single} node, a {@link Mapped} node, or a {@link Combinator} node ({@link AnyOf} or {@link
   * AllOf}).
   *
   * <p>The {@link HandlerContext} implementation reads the tree from another thread while {@link
   * #await(UnresolvedFuture)} is in progress: the tree structure must not change, and {@link
   * #isDone()} must be safe to read from other threads.
   */
  sealed interface UnresolvedFuture {
    /**
     * @return true if this node is completed. Completed nodes, and their children, are not awaited
     *     anymore.
     */
    boolean isDone();

    /** Node completed by the result of its {@link Handle}, with no children. */
    non-sealed interface Single extends UnresolvedFuture {
      Handle<?> handle();
    }

    /** Node mapping the result of the {@code upstream} node. */
    non-sealed interface Mapped extends UnresolvedFuture {
      UnresolvedFuture upstream();
    }

    /** Node combining several children. */
    sealed interface Combinator extends UnresolvedFuture {
      int childrenCount();

      UnresolvedFuture child(int index);
    }

    /** Completes as soon as any child completes, either with success or failure. */
    non-sealed interface AnyOf extends Combinator {}

    /** Completes when all children succeed, or fails as soon as one child fails. */
    non-sealed interface AllOf extends Combinator {}
  }

  /** Result of {@link #await(UnresolvedFuture)}. */
  sealed interface AwaitResult {

    /**
     * One single of the awaited tree completed.
     *
     * <p>The caller MUST store the {@code result} in the {@code single}, otherwise it is lost.
     *
     * @param single the completed single of the awaited tree
     * @param result the result of the single, its type matches the type of the single {@link
     *     Handle}
     */
    record AnyCompleted(UnresolvedFuture.Single single, Result<?> result) implements AwaitResult {}

    /** The invocation was cancelled: the awaited tree should be completed as cancelled. */
    record Cancelled() implements AwaitResult {
      public static final Cancelled INSTANCE = new Cancelled();
    }

    /**
     * The invocation was aborted, e.g. because it suspended or failed: the awaited tree should be
     * completed as aborted.
     */
    record Aborted() implements AwaitResult {
      public static final Aborted INSTANCE = new Aborted();
    }
  }

  /**
   * Result of an operation.
   *
   * @param <T> type of the result
   */
  sealed interface Result<T> {

    /**
     * @return the value, or throws the {@link TerminalException} of a failure.
     */
    T getResultOrThrow() throws TerminalException;

    record Success<T>(T value) implements Result<T> {
      @Override
      public T getResultOrThrow() {
        return value;
      }
    }

    record Failure<T>(TerminalException exception) implements Result<T> {
      @Override
      public T getResultOrThrow() throws TerminalException {
        throw exception;
      }
    }
  }
}
