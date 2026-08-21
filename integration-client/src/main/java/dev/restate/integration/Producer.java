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
 * {@link #send} writes the record straight to the stream and returns a future that completes once
 * Restate has durably committed it. If the producer is not ready {@code send} throws {@link
 * ProducerNotReadyException} rather than queueing. Catch it, await {@link #waitReady()}, and retry.
 *
 * <p>Awaiting each {@code send} future before the next send serializes to one in-flight record. To
 * parallelize sending, just keep {@code send}ing and use {@link #flush} to await durability in
 * bulk.
 *
 * <pre>{@code
 * try (IntegrationClient client = IntegrationClient.builder("http://localhost:8080").build();
 *     Producer producer = client.newProducer()) {
 *   for (byte[] payload : payloads) {
 *     Invocation invocation = Invocation.create().setBody(payload);
 *     while (true) {
 *       try {
 *         producer.send(invocation);
 *         break;
 *       } catch (ProducerNotReadyException notReady) {
 *         producer.waitReady().get(); // block until there is capacity, then retry
 *       }
 *     }
 *   }
 *   producer.flush().get(); // block until everything sent so far is durably committed
 * }
 * }</pre>
 *
 * <h2>Stream defaults</h2>
 *
 * Pass an {@link InvocationMetadata} to {@link IntegrationClient#newProducer(InvocationMetadata)}
 * to set fields shared by every record (e.g. the target service/handler) once; per-invocation
 * fields override them.
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
 * java.util.ConcurrentModificationException}) if used from more than one thread at once.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public interface Producer extends ProducerBase {

  /**
   * Sends an invocation.
   *
   * <p>If the internal buffer is full, or the producer doesn't have enough window credit, sending
   * is refused with a {@link ProducerNotReadyException} exception, await {@link #waitReady()}, and
   * retry. See the example in {@link Producer} for more details.
   *
   * <p>The returned future completes when the invocation is durably committed by Restate.
   *
   * @param invocation the invocation to send
   * @return a future completing, once the invocation is durably committed by Restate.
   * @throws ProducerNotReadyException if the producer cannot accept a record right now
   * @throws java.util.ConcurrentModificationException if the producer is used concurrently from
   *     another thread
   */
  CompletableFuture<SendResult> send(Invocation invocation) throws ProducerNotReadyException;
}
