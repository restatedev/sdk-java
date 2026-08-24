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
 * Like {@link Producer}, but with exactly-once semantics.
 *
 * <h2>Exactly once</h2>
 *
 * Pick a producer id that is <b>stable across restarts</b> and <b>distinct per independent offset
 * sequence</b>. E.g., for a Kafka consumer {@code groupId/topic/partition}, for Postgres logical
 * replication the slot name. Because deduplication happens on {@code (producerId, offset)}, it is
 * then safe to replay from your last checkpoint after a crash: already-committed offsets are
 * dropped, and {@link #flush} / {@link #waitAcknowledged(long)} reports how far Restate has durably
 * caught up so you can advance the checkpoint. After a stream failure, {@link
 * #lastAcknowledgedOffset()} remains available so you can determine where to resume.
 *
 * <h2>Sending</h2>
 *
 * {@link #send} admits the record into a byte-bounded local buffer, waiting up to {@link
 * ProducerOptions#maxBlockTime()} for capacity, and returns a future that completes once Restate
 * has durably committed it. Use {@link #trySend} when the calling thread must never block.
 *
 * <p>Awaiting each {@code send} future before the next send serializes to one in-flight record. To
 * parallelize sending, just keep {@code send}ing and use {@link #flush} to await durability in
 * bulk.
 *
 * <pre>{@code
 * producer.send(lsn, Invocation.create().setBody(payload));
 * long committed = producer.flush().get();
 * checkpoint.store(committed);
 * }</pre>
 *
 * <h2>Thread safety</h2>
 *
 * A producer is <b>not thread-safe</b> and fails fast with {@link
 * java.util.ConcurrentModificationException} if used from more than one thread at once.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public interface ExactlyOnceProducer extends ProducerBase {

  /**
   * Sends an invocation at {@code offset}.
   *
   * <p>If the local buffer is full, this method waits up to {@link ProducerOptions#maxBlockTime()}
   * for capacity. The invocation is refused with {@link ProducerNotReadyException} if the timeout
   * elapses. A zero duration makes this method fail immediately under backpressure.
   *
   * <p>The returned future completes when the invocation is durably committed by Restate.
   *
   * @param offset the offset to assign to this record; must be strictly greater than the previous
   *     one
   * @param invocation the invocation to send
   * @return a future completing, once the record is durably committed by Restate, with the {@link
   *     SendResult} carrying {@code offset}
   * @throws ProducerNotReadyException if buffer capacity does not become available before the
   *     configured maximum blocking time elapses
   * @throws IllegalArgumentException if {@code offset} is not strictly greater than {@link
   *     #lastSentOffset()}, or the serialized invocation is larger than {@link
   *     ProducerOptions#bufferMemory()}
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  CompletableFuture<SendResult> send(long offset, Invocation invocation)
      throws ProducerNotReadyException;

  /**
   * Attempts to send an invocation at {@code offset} without blocking.
   *
   * <p>An {@link SendAttempt.Accepted} carries the durable-acknowledgement future. A {@link
   * SendAttempt.Backpressured} carries a future that completes when retrying may succeed; the
   * notification does not reserve capacity.
   *
   * @param offset the offset to assign; must be strictly greater than the previous accepted offset
   * @param invocation the invocation to send
   * @return the admission result
   * @throws IllegalArgumentException if {@code offset} is not strictly greater than {@link
   *     #lastSentOffset()}, or the serialized invocation is larger than {@link
   *     ProducerOptions#bufferMemory()}
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  SendAttempt trySend(long offset, Invocation invocation);
}
