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
 * sequence</b>. E.g., for a Kafka consumer {@code groupId/topic/partition}), for Postgres logical
 * replication the slot name. Because deduplication happens on {@code (producerId, offset)}, it is
 * then safe to replay from your last checkpoint after a crash: already-committed offsets are
 * dropped, and {@link #flush} / {@link #waitAcknowledged(long)} reports how far Restate has durably
 * caught up so you can advance the checkpoint.
 *
 * <h2>Sending</h2>
 *
 * {@link #send} writes the record straight to the stream and returns a future that completes once
 * Restate has durably committed it. If the producer is not ready {@code send} throws {@link
 * ProducerNotReadyException} rather than queueing. Catch it, await {@link #waitReady()}, and retry.
 *
 * <p>Awaiting each {@code send} future before the next send serializes to one in-flight record. To
 * parallelize sending, just keep {@code send}ing and use {@link #flush} to await durability in
 * bulk.
 *
 * <pre>{@code
 * while (true) {
 *   try {
 *     producer.send(lsn, Invocation.create().setBody(payload));
 *     break;
 *   } catch (ProducerNotReadyException notReady) {
 *     producer.waitReady().get();
 *   }
 * }
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
   * <p>If the internal buffer is full, or the producer doesn't have enough window credit, sending
   * is refused with a {@link ProducerNotReadyException} exception, await {@link #waitReady()}, and
   * retry. See the example in {@link ExactlyOnceProducer} for more details.
   *
   * <p>The returned future completes when the invocation is durably committed by Restate.
   *
   * @param offset the offset to assign to this record; must be strictly greater than the previous
   *     one
   * @param invocation the invocation to send
   * @return a future completing, once the record is durably committed by Restate, with the {@link
   *     SendResult} carrying {@code offset}
   * @throws ProducerNotReadyException if the producer cannot accept a record right now
   * @throws IllegalArgumentException if {@code offset} is not strictly greater than {@link
   *     #lastSentOffset()}
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  CompletableFuture<SendResult> send(long offset, Invocation invocation)
      throws ProducerNotReadyException;
}
