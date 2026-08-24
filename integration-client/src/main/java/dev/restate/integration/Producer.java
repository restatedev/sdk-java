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
 * An at-least-once producer: the client assigns a monotonically increasing offset to each
 * invocation. Deduplication is disabled (empty producer id); add an idempotency key on the
 * invocations if you need handler-level dedup.
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
 * try (IntegrationClient client = IntegrationClient.builder("http://localhost:8080").build();
 *     Producer producer = client.newProducer()) {
 *   for (byte[] payload : payloads) {
 *     producer.send(Invocation.create().setBody(payload));
 *   }
 *   producer.flush(); // block until everything sent so far is durably committed
 * }
 * }</pre>
 *
 * <p>{@link #close()} does not flush. Call {@link #flush()} before closing, or await {@link
 * #flushAsync()}, when accepted invocations must be durably committed.
 *
 * <h2>Stream defaults</h2>
 *
 * Pass an {@link InvocationMetadata} to {@link IntegrationClient#newProducer(InvocationMetadata)},
 * or set {@link ProducerOptions.Builder#defaultMetadata(InvocationMetadata)}, to configure fields
 * shared by every record (e.g. the target service/handler) once; per-invocation fields override
 * them.
 *
 * <pre>{@code
 * Producer producer =
 *     client.newProducer(
 *         InvocationMetadata.create().setServiceName("Greeter").setHandlerName("greet"));
 * }</pre>
 *
 * <h2>Thread safety</h2>
 *
 * A producer is <b>not thread-safe</b> and fails fast with {@link
 * java.util.ConcurrentModificationException} if used from more than one thread at once.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public interface Producer extends ProducerBase {

  /**
   * Sends an invocation.
   *
   * <p>If the local buffer is full, this method waits up to {@link ProducerOptions#maxBlockTime()}
   * for capacity. The invocation is refused with {@link ProducerBufferExhaustedException} if the
   * timeout elapses. A zero duration makes this method fail immediately under backpressure.
   *
   * <p>The returned future completes when the invocation is durably committed by Restate.
   *
   * @param invocation the invocation to send
   * @return a future completing, once the invocation is durably committed by Restate.
   * @throws ProducerBufferExhaustedException if buffer capacity does not become available before
   *     the configured maximum blocking time elapses, or the thread is interrupted while waiting
   * @throws IllegalArgumentException if the serialized invocation is larger than {@link
   *     ProducerOptions#bufferMemory()}
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  CompletableFuture<SendResult> send(Invocation invocation) throws ProducerBufferExhaustedException;

  /**
   * Attempts to send an invocation without blocking.
   *
   * <p>An {@link SendAttempt.Accepted} carries the durable-acknowledgement future. A {@link
   * SendAttempt.Backpressured} carries a future that completes when retrying may succeed; the
   * notification does not reserve capacity.
   *
   * @param invocation the invocation to send
   * @return the admission result
   * @throws IllegalArgumentException if the serialized invocation is larger than {@link
   *     ProducerOptions#bufferMemory()}
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  SendAttempt trySend(Invocation invocation);
}
