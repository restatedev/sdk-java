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
 * Sends invocations to Restate with exactly-once deduplication.
 *
 * <pre>{@code
 * try (IntegrationClient client = IntegrationClient.builder("http://localhost:8080").build();
 *     ExactlyOnceProducer producer =
 *         client.newExactlyOnceProducer("group-a/orders/0")) {
 *   long offset = checkpoint.load() + 1;
 *   producer.send(
 *       offset,
 *       Invocation.create()
 *           .setServiceName("Orders")
 *           .setHandlerName("ingest")
 *           .setBody(payload));
 *   checkpoint.store(producer.flush());
 * }
 * }</pre>
 *
 * <h2>Buffering</h2>
 *
 * With a positive {@link ProducerOptions#bufferMemory()}, {@link #send} first admits the invocation
 * to a byte-bounded local buffer while it waits to be handed to the transport. A value of zero
 * disables local buffering, so {@code send} instead waits until the protocol and transport are
 * writable. Either wait is bounded by {@link ProducerOptions#maxBlockTime()}. The returned future
 * tracks durable acknowledgement, not admission. Send several invocations without awaiting each
 * future, then use {@link #flush()} or {@link #flushAsync()} to await them in bulk. {@link
 * #close()} does not flush.
 *
 * <h2>Non-blocking admission</h2>
 *
 * For event-loop or callback-based code, {@link #trySend} does not wait for admission. {@link
 * SendAttempt.Accepted} contains the durable-acknowledgement future. On {@link
 * SendAttempt.Backpressured}, use {@link SendAttempt.Backpressured#ready()} to schedule a retry of
 * the same offset and invocation on the event loop; readiness is a notification, not an admission
 * reservation.
 *
 * <pre>{@code
 * static CompletableFuture<SendResult> sendWithoutBlocking(
 *     ExactlyOnceProducer producer, long offset, Invocation invocation, Executor eventLoop) {
 *   SendAttempt attempt = producer.trySend(offset, invocation);
 *   if (attempt instanceof SendAttempt.Accepted accepted) {
 *     return accepted.acknowledgement();
 *   }
 *   return ((SendAttempt.Backpressured) attempt)
 *       .ready()
 *       .thenComposeAsync(
 *           ignored -> sendWithoutBlocking(producer, offset, invocation, eventLoop), eventLoop);
 * }
 * }</pre>
 *
 * <h2>Producer identity and deduplication</h2>
 *
 * Each invocation has a strictly increasing offset. Restate deduplicates on {@code (producerId,
 * offset)}, so choose a producer id that is <b>stable across restarts</b> and <b>distinct per
 * independent offset sequence</b>: for example, a Kafka {@code groupId/topic/partition} or a
 * Postgres logical-replication slot.
 *
 * <p>After a crash, replay from the last checkpoint; Restate drops already-committed offsets.
 * {@link #flush()} and {@link #waitAcknowledged(long)} report the durable watermark to checkpoint,
 * and {@link #lastAcknowledgedOffset()} remains available after a stream failure.
 *
 * <p>A producer is <b>not thread-safe</b> and fails fast with {@link
 * java.util.ConcurrentModificationException} if used from more than one thread at once.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public interface ExactlyOnceProducer extends ProducerBase {

  /**
   * Sends an invocation at {@code offset}.
   *
   * <p>This method waits up to {@link ProducerOptions#maxBlockTime()} when the local buffer is
   * full, or, when buffering is disabled, until protocol and transport readiness permit a direct
   * write. The invocation is refused with {@link ProducerBufferExhaustedException} if the timeout
   * elapses. A zero duration makes this method fail immediately under backpressure.
   *
   * <p>The returned future completes when the invocation is durably committed by Restate.
   *
   * @param offset the offset to assign to this record; must be strictly greater than the previous
   *     one
   * @param invocation the invocation to send
   * @return a future completing, once the record is durably committed by Restate, with the {@link
   *     SendResult} carrying {@code offset}
   * @throws ProducerBufferExhaustedException if the producer cannot admit the invocation before the
   *     configured maximum blocking time elapses, or the thread is interrupted while waiting
   * @throws IllegalStateException if a reentrant producer callback invokes this method when it
   *     would block
   * @throws IllegalArgumentException if {@code offset} is not strictly greater than {@link
   *     #lastSentOffset()}, or buffering is enabled and the serialized invocation is larger than
   *     {@link ProducerOptions#bufferMemory()}
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  CompletableFuture<SendResult> send(long offset, Invocation invocation)
      throws ProducerBufferExhaustedException;

  /**
   * Attempts to send an invocation at {@code offset} without blocking.
   *
   * <p>An {@link SendAttempt.Accepted} carries the durable-acknowledgement future. A {@link
   * SendAttempt.Backpressured} carries a future that completes when retrying may succeed; the
   * notification does not reserve admission.
   *
   * @param offset the offset to assign; must be strictly greater than the previous accepted offset
   * @param invocation the invocation to send
   * @return the admission result
   * @throws IllegalArgumentException if {@code offset} is not strictly greater than {@link
   *     #lastSentOffset()}, or buffering is enabled and the serialized invocation is larger than
   *     {@link ProducerOptions#bufferMemory()}
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  SendAttempt trySend(long offset, Invocation invocation);
}
