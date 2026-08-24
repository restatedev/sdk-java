// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import dev.restate.ingestion.v1.DeduplicationMode;
import dev.restate.ingestion.v1.ErrorKind;
import dev.restate.ingestion.v1.IngestionDefaults;
import dev.restate.ingestion.v1.IngestionRequest;
import dev.restate.ingestion.v1.IngestionResponse;
import dev.restate.ingestion.v1.IngestionStart;
import dev.restate.ingestion.v1.IngestionSvcGrpc;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns exactly one ingestion bidi stream and all its send-side state. See {@link ProducerBase} and
 * the module docs for the concurrency contract.
 *
 * <p>There is <b>no client-side record queue</b>: {@link #doSend} writes the record straight to the
 * gRPC stream when the producer is ready — Restate's byte send-window has credit ({@code budget})
 * <i>and</i> the transport is writable ({@code callObserver.isReady()}) — or throws {@link
 * ProducerNotReadyException} otherwise. In-flight records live on the wire; the only per-record
 * client state until commit is a future parked in {@link #ackWaiters}, completed when the ack
 * watermark passes its offset.
 *
 * <p>Two-tier concurrency:
 *
 * <ul>
 *   <li>A fail-fast, KafkaConsumer-style guard ({@link #acquire()}/{@link #release()}) rejects
 *       <b>concurrent</b> use from multiple threads; sequential hand-off between threads is fine.
 *   <li>A single monitor ({@link #lock}) guards the small set of fields genuinely shared between
 *       the caller thread and gRPC's callback threads. Futures are always completed outside the
 *       monitor.
 * </ul>
 */
abstract class AbstractProducer implements ProducerBase {

  private final Object lock = new Object();

  // Set once, synchronously, in beforeStart() before the constructor sends the Start frame.
  private volatile ClientCallStreamObserver<IngestionRequest> callObserver;

  // ---- fail-fast single-thread guard ----
  private final AtomicReference<Thread> owner = new AtomicReference<>();
  private int reentrancy;

  // ---- state guarded by `lock` ----
  private long budget = 0; // remaining Restate send window, in bytes; may go one message negative
  private long lastCommitted = -1; // ack watermark; -1 == nothing committed yet
  private final List<CompletableFuture<Void>> readyWaiters = new ArrayList<>();
  private final TreeMap<Long, List<CompletableFuture<Long>>> ackWaiters = new TreeMap<>();
  private boolean closed = false;
  private IntegrationClientException failure;

  // Written only by the (guarded) caller thread; never touched by gRPC callbacks.
  long lastSent = -1;

  AbstractProducer(
      IngestionSvcGrpc.IngestionSvcStub stub,
      String producerId,
      DeduplicationMode deduplicationMode,
      IngestionDefaults defaults,
      String integration) {
    // Opening the call invokes beforeStart() synchronously, wiring callObserver + the ready
    // handler.
    stub.ingest(new ResponseObserver());
    // Mandatory Start handshake: the first frame on the stream (not flow-controlled).
    IngestionRequest start =
        IngestionRequest.newBuilder()
            .setStart(
                IngestionStart.newBuilder()
                    .setProducerId(producerId)
                    .setIntegration(integration)
                    .setDeduplicationMode(deduplicationMode)
                    .setDefaults(defaults))
            .build();
    synchronized (lock) {
      callObserver.onNext(start);
    }
  }

  // ---- ProducerBase ----

  @Override
  public long lastSentOffset() {
    acquire();
    try {
      return lastSent;
    } finally {
      release();
    }
  }

  @Override
  public long lastAcknowledgedOffset() {
    acquire();
    try {
      synchronized (lock) {
        return lastCommitted;
      }
    } finally {
      release();
    }
  }

  @Override
  public CompletableFuture<Void> waitReady() {
    acquire();
    try {
      synchronized (lock) {
        if (closed) {
          return CompletableFuture.failedFuture(failure);
        }
        if (isReadyLocked()) {
          return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        readyWaiters.add(f);
        return f;
      }
    } finally {
      release();
    }
  }

  @Override
  public CompletableFuture<Long> waitAcknowledged(long offset) {
    acquire();
    try {
      return registerAckWaiter(offset);
    } finally {
      release();
    }
  }

  /**
   * Register an ack waiter for {@code offset}. The returned future completes with the ack watermark
   * once it reaches {@code offset}. Only touches {@code lock}-guarded state (Java monitors are
   * reentrant, so this is safe to call while already holding {@code lock}).
   */
  private CompletableFuture<Long> registerAckWaiter(long offset) {
    synchronized (lock) {
      if (closed) {
        return CompletableFuture.failedFuture(failure);
      }
      if (offset <= lastCommitted) {
        return CompletableFuture.completedFuture(lastCommitted);
      }
      CompletableFuture<Long> f = new CompletableFuture<>();
      ackWaiters.computeIfAbsent(offset, k -> new ArrayList<>()).add(f);
      return f;
    }
  }

  @Override
  public void close() {
    acquire();
    try {
      terminate(
          new IntegrationClientException(
              IntegrationClientException.Kind.UNKNOWN, "producer closed"),
          true);
    } finally {
      release();
    }
  }

  // ---- send path, shared by the subclasses (caller holds the guard) ----

  /**
   * Send at {@code offset}: write it to the stream now if the producer is ready, else throw {@link
   * ProducerNotReadyException}. Returns a future that completes with a {@link SendResult} once the
   * record is durably committed by Restate. There is no buffering — a not-ready producer refuses
   * rather than parking the record.
   */
  final CompletableFuture<SendResult> doSend(long offset, InvocationImpl invocation)
      throws ProducerNotReadyException {
    IngestionRequest req =
        IngestionRequest.newBuilder().setInvocation(invocation.toProtoInvocation(offset)).build();
    long debit = req.getInvocation().getSerializedSize();
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("producer is closed", failure);
      }
      if (!isReadyLocked()) {
        throw new ProducerNotReadyException("producer is not ready");
      }
      writeLocked(req, debit);
      lastSent = offset;
      CompletableFuture<Long> committed = new CompletableFuture<>();
      ackWaiters.computeIfAbsent(offset, k -> new ArrayList<>()).add(committed);
      return committed.thenApply(watermark -> new SendResultImpl(offset));
    }
  }

  // ---- internals (all `*Locked` methods require `lock`) ----

  private boolean isReadyLocked() {
    return !closed && budget > 0 && callObserver.isReady();
  }

  private void writeLocked(IngestionRequest req, long debit) {
    callObserver.onNext(req);
    budget -= debit;
  }

  /** Wake readiness waiters once the stream can accept writes again (window credit + writable). */
  private void wakeReadyWaiters() {
    List<CompletableFuture<Void>> wakeReady = null;
    synchronized (lock) {
      if (closed) {
        return;
      }
      if (isReadyLocked() && !readyWaiters.isEmpty()) {
        wakeReady = new ArrayList<>(readyWaiters);
        readyWaiters.clear();
      }
    }
    if (wakeReady != null) {
      for (CompletableFuture<Void> f : wakeReady) {
        f.complete(null);
      }
    }
  }

  private void onResponse(IngestionResponse resp) {
    List<CompletableFuture<Long>> acksToComplete = null;
    long watermark = -1;
    boolean wakeReady = false;
    IntegrationClientException err = null;
    synchronized (lock) {
      if (closed) {
        return;
      }
      if (resp.hasLastCommitted() && resp.getLastCommitted() > lastCommitted) {
        lastCommitted = resp.getLastCommitted();
        watermark = lastCommitted;
        if (!ackWaiters.isEmpty()) {
          acksToComplete = new ArrayList<>();
          Map<Long, List<CompletableFuture<Long>>> head = ackWaiters.headMap(watermark, true);
          for (List<CompletableFuture<Long>> waiters : head.values()) {
            acksToComplete.addAll(waiters);
          }
          head.clear();
        }
      }
      if (resp.hasWindowUpdate()) {
        // increment_bytes is a uint32; read it as unsigned.
        budget += Integer.toUnsignedLong(resp.getWindowUpdate().getIncrementBytes());
        wakeReady = true;
      } else if (resp.hasError()) {
        err = mapError(resp.getError());
      }
    }
    if (acksToComplete != null) {
      for (CompletableFuture<Long> f : acksToComplete) {
        f.complete(watermark);
      }
    }
    if (err != null) {
      terminate(err, false);
    } else if (wakeReady) {
      wakeReadyWaiters();
    }
  }

  /** Mark the producer terminally closed and fail every pending future with {@code cause}. */
  private void terminate(IntegrationClientException cause, boolean halfClose) {
    List<CompletableFuture<Void>> ready;
    List<CompletableFuture<Long>> acks = new ArrayList<>();
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      failure = cause;
      ready = new ArrayList<>(readyWaiters);
      readyWaiters.clear();
      for (List<CompletableFuture<Long>> waiters : ackWaiters.values()) {
        acks.addAll(waiters);
      }
      ackWaiters.clear();
    }
    if (halfClose) {
      ClientCallStreamObserver<IngestionRequest> obs = callObserver;
      if (obs != null) {
        try {
          obs.onCompleted();
        } catch (RuntimeException ignored) {
          // Already torn down transport-side; nothing to half-close.
        }
      }
    }
    for (CompletableFuture<Void> f : ready) {
      f.completeExceptionally(cause);
    }
    for (CompletableFuture<Long> f : acks) {
      f.completeExceptionally(cause);
    }
  }

  private static IntegrationClientException mapError(dev.restate.ingestion.v1.Error error) {
    String detail =
        error.hasInvocationOffset()
            ? "[offset=" + error.getInvocationOffset() + "] " + error.getMessage()
            : error.getMessage();
    return new IntegrationClientException(mapKind(error.getKind()), detail);
  }

  private static IntegrationClientException.Kind mapKind(ErrorKind kind) {
    switch (kind) {
      case ERROR_KIND_SHUTTING_DOWN:
        return IntegrationClientException.Kind.SHUTTING_DOWN;
      case ERROR_KIND_GO_AWAY:
        return IntegrationClientException.Kind.GO_AWAY;
      case ERROR_KIND_NOT_FOUND:
        return IntegrationClientException.Kind.NOT_FOUND;
      case ERROR_KIND_BAD_REQUEST:
        return IntegrationClientException.Kind.BAD_REQUEST;
      default:
        return IntegrationClientException.Kind.UNKNOWN;
    }
  }

  // ---- fail-fast guard ----

  final void acquire() {
    Thread current = Thread.currentThread();
    if (owner.get() == current) {
      reentrancy++;
      return;
    }
    if (!owner.compareAndSet(null, current)) {
      throw new ConcurrentModificationException("Producer is not safe for multi-threaded access");
    }
    reentrancy = 1;
  }

  final void release() {
    if (--reentrancy == 0) {
      owner.set(null);
    }
  }

  private final class ResponseObserver
      implements ClientResponseObserver<IngestionRequest, IngestionResponse> {
    @Override
    public void beforeStart(ClientCallStreamObserver<IngestionRequest> requestStream) {
      callObserver = requestStream;
      requestStream.setOnReadyHandler(AbstractProducer.this::wakeReadyWaiters);
    }

    @Override
    public void onNext(IngestionResponse value) {
      onResponse(value);
    }

    @Override
    public void onError(Throwable t) {
      terminate(
          new IntegrationClientException(
              IntegrationClientException.Kind.UNKNOWN,
              "ingestion stream failed: " + t.getMessage(),
              t),
          false);
    }

    @Override
    public void onCompleted() {
      terminate(
          new IntegrationClientException(
              IntegrationClientException.Kind.UNKNOWN, "ingestion stream closed by server"),
          false);
    }
  }

  private record SendResultImpl(long offset) implements SendResult {}
}
