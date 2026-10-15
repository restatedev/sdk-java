// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import java.util.concurrent.CompletableFuture;

/**
 * Common producer offsets, acknowledgement, flushing, and lifecycle operations.
 *
 * <p>Producer futures can complete inline on a transport callback. A synchronous continuation must
 * not invoke an operation that would block, such as {@link #flush()}; offload the continuation to
 * an executor instead. A reentrant call that would block is rejected with {@link
 * IllegalStateException} rather than deadlocking the transport callback lane.
 *
 * @see Producer
 * @see ExactlyOnceProducer
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public interface ProducerBase extends AutoCloseable {

  /**
   * Returns the highest offset successfully accepted by {@code send} or {@code trySend} so far.
   *
   * @return the highest offset accepted, or {@code -1} if nothing has been accepted yet
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  long lastSentOffset();

  /**
   * Returns the highest offset durably acknowledged by Restate.
   *
   * <p>This value remains available after the producer closes or fails, so an exactly-once producer
   * can use it to determine where to resume.
   *
   * @return the highest durably acknowledged offset, or {@code -1} if nothing has been acknowledged
   *     yet
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  long lastAcknowledgedOffset();

  /**
   * Awaits durable acknowledgement of all invocations up to and including {@code offset}.
   *
   * @param offset the offset to wait for
   * @return a future completing, once every invocation up to and including {@code offset} is
   *     durably acknowledged by Restate, with the highest acknowledged offset
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  CompletableFuture<Long> waitAcknowledged(long offset);

  /**
   * Blocks until every invocation accepted before this call is durably acknowledged.
   *
   * @return the highest durably committed offset
   * @throws IntegrationClientException if the producer fails before all invocations are
   *     acknowledged
   * @throws IllegalStateException if a reentrant producer callback invokes this method when it
   *     would block
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  long flush();

  /**
   * Asynchronously awaits durable acknowledgement of every invocation accepted before this call.
   *
   * @return a future completing with the highest durably committed offset once all invocations sent
   *     so far are acknowledged
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  CompletableFuture<Long> flushAsync();

  /**
   * Immediately closes the producer and shuts down its stream without flushing. Any
   * not-yet-acknowledged invocation completes its future exceptionally with an {@link
   * IntegrationClientException}.
   *
   * <p>Call {@link #flush()} before closing, or await {@link #flushAsync()}, when accepted
   * invocations must be durably committed.
   *
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  @Override
  void close();
}
