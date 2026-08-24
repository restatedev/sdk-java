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
 * Sends invocations to Restate with at-least-once delivery.
 *
 * <pre>{@code
 * try (IntegrationClient client = IntegrationClient.builder("http://localhost:8080").build();
 *     Producer producer = client.newProducer()) {
 *   producer.send(
 *       Invocation.create()
 *           .setServiceName("Greeter")
 *           .setHandlerName("greet")
 *           .setBody(payload));
 *   producer.flush();
 * }
 * }</pre>
 *
 * <h2>Buffering</h2>
 *
 * {@link #send} first admits the invocation to a local buffer, bounded by {@link
 * ProducerOptions#bufferMemory()}, while it waits to be handed to the transport. If the buffer is
 * full, {@code send} waits up to {@link ProducerOptions#maxBlockTime()} and then throws {@link
 * ProducerBufferExhaustedException}. The returned future tracks durable acknowledgement, not buffer
 * admission. Send several invocations without awaiting each future, then use {@link #flush()} or
 * {@link #flushAsync()} to await them in bulk. {@link #close()} does not flush.
 *
 * <h2>Non-blocking admission</h2>
 *
 * For event-loop or callback-based code, {@link #trySend} does not wait for buffer capacity. {@link
 * SendAttempt.Accepted} contains the durable-acknowledgement future. On {@link
 * SendAttempt.Backpressured}, use {@link SendAttempt.Backpressured#ready()} to schedule a retry on
 * the event loop; readiness is a notification, not a capacity reservation.
 *
 * <pre>{@code
 * static CompletableFuture<SendResult> sendWithoutBlocking(
 *     Producer producer, Invocation invocation, Executor eventLoop) {
 *   SendAttempt attempt = producer.trySend(invocation);
 *   if (attempt instanceof SendAttempt.Accepted accepted) {
 *     return accepted.acknowledgement();
 *   }
 *   return ((SendAttempt.Backpressured) attempt)
 *       .ready()
 *       .thenComposeAsync(
 *           ignored -> sendWithoutBlocking(producer, invocation, eventLoop), eventLoop);
 * }
 * }</pre>
 *
 * <p>The client assigns monotonically increasing offsets. Producer-level deduplication is disabled;
 * set an idempotency key on an invocation when handler-level deduplication is required.
 *
 * <p>A producer is <b>not thread-safe</b> and fails fast with {@link
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
