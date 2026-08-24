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
import dev.restate.ingestion.v1.IngestionRequest;
import dev.restate.ingestion.v1.IngestionResponse;
import dev.restate.ingestion.v1.IngestionStart;
import dev.restate.ingestion.v1.IngestionSvcGrpc;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Owns exactly one ingestion bidi stream and all its send-side state. See {@link ProducerBase} and
 * the module docs for the concurrency contract.
 *
 * <p>When buffering is enabled, accepted records wait in a byte-bounded queue until Restate's
 * send-window has credit ({@code budget}) and the transport is writable ({@code
 * callObserver.isReady()}). With buffering disabled, records are accepted only when they can be
 * handed directly to gRPC. Once handed off, only their acknowledgement futures remain until the
 * commit watermark passes their offsets.
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
  private volatile @Nullable ClientCallStreamObserver<IngestionRequest> callObserver;

  // ---- fail-fast single-thread guard ----
  private final AtomicReference<@Nullable Thread> owner = new AtomicReference<>();
  private int reentrancy;

  // ---- state guarded by `lock` ----
  private long budget = 0; // remaining Restate send window, in bytes; may go one message negative
  private long lastCommitted = -1; // ack watermark; -1 == nothing committed yet
  private final ArrayDeque<BufferedSend> bufferedSends = new ArrayDeque<>();
  private long bufferedBytes = 0;
  private final List<AdmissionWaiter> admissionWaiters = new ArrayList<>();
  private final TreeMap<Long, List<CompletableFuture<Long>>> ackWaiters = new TreeMap<>();
  private boolean closed = false;
  private @Nullable IntegrationClientException failure;

  private final long bufferMemory;
  private final Duration maxBlockTime;
  private final long maxBlockNanos;

  // Written only by the (guarded) caller thread; never touched by gRPC callbacks.
  long lastSent = -1;

  AbstractProducer(
      IngestionSvcGrpc.IngestionSvcStub stub,
      String producerId,
      DeduplicationMode deduplicationMode,
      ProducerOptions options,
      String integration) {
    this.bufferMemory = options.bufferMemory();
    this.maxBlockTime = options.maxBlockTime();
    this.maxBlockNanos = toNanosSaturated(maxBlockTime);
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
                    .setDefaults(options.toDefaults()))
            .build();
    synchronized (lock) {
      Objects.requireNonNull(callObserver, "gRPC request stream was not initialized").onNext(start);
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
  public CompletableFuture<Long> waitAcknowledged(long offset) {
    acquire();
    try {
      return registerAckWaiter(offset);
    } finally {
      release();
    }
  }

  @Override
  public long flush() {
    acquire();
    try {
      return awaitFlush(registerAckWaiter(lastSent));
    } finally {
      release();
    }
  }

  @Override
  public CompletableFuture<Long> flushAsync() {
    acquire();
    try {
      return registerAckWaiter(lastSent);
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
        return CompletableFuture.failedFuture(
            Objects.requireNonNull(failure, "closed producer has no failure"));
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

  /** Admit a record, blocking up to the configured maximum while the producer is backpressured. */
  final CompletableFuture<SendResult> doSend(long offset, InvocationImpl invocation)
      throws ProducerBufferExhaustedException {
    PreparedSend prepared = prepare(offset, invocation);
    List<CompletableFuture<@Nullable Void>> ready;
    CompletableFuture<SendResult> acknowledgement;
    boolean directWrite;
    long waitStarted = System.nanoTime();
    synchronized (lock) {
      ensureOpenLocked();
      while (!canAdmitLocked(prepared.bufferSize())) {
        if (maxBlockNanos == 0) {
          throw admissionTimeout();
        }
        long remaining = maxBlockNanos - (System.nanoTime() - waitStarted);
        if (remaining <= 0) {
          throw admissionTimeout();
        }
        try {
          long millis = remaining / 1_000_000;
          int nanos = (int) (remaining % 1_000_000);
          lock.wait(millis, nanos);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new ProducerBufferExhaustedException(
              "interrupted while waiting for producer admission", e);
        }
        ensureOpenLocked();
      }
      acknowledgement = acceptLocked(prepared);
      directWrite = bufferMemory == 0;
      ready = directWrite ? List.of() : drainLocked();
    }
    if (directWrite) {
      writeDirect(prepared.request());
      drainAndWake();
    } else {
      completeReady(ready);
    }
    return acknowledgement;
  }

  /** Attempt to admit a record without blocking or consuming an offset under backpressure. */
  final SendAttempt doTrySend(long offset, InvocationImpl invocation) {
    PreparedSend prepared = prepare(offset, invocation);
    List<CompletableFuture<@Nullable Void>> ready;
    SendAttempt result;
    boolean directWrite = false;
    synchronized (lock) {
      ensureOpenLocked();
      if (canAdmitLocked(prepared.bufferSize())) {
        result = new SendAttempt.Accepted(acceptLocked(prepared));
        directWrite = bufferMemory == 0;
        ready = directWrite ? List.of() : drainLocked();
      } else {
        CompletableFuture<@Nullable Void> future = new CompletableFuture<>();
        AdmissionWaiter waiter = new AdmissionWaiter(prepared.bufferSize(), future);
        admissionWaiters.add(waiter);
        future.whenComplete(
            (ignored, failure) -> {
              if (future.isCancelled()) {
                synchronized (lock) {
                  admissionWaiters.remove(waiter);
                }
              }
            });
        result = new SendAttempt.Backpressured(future);
        ready = List.of();
      }
    }
    if (directWrite) {
      writeDirect(prepared.request());
      drainAndWake();
    } else {
      completeReady(ready);
    }
    return result;
  }

  // ---- internals (all `*Locked` methods require `lock`) ----

  private PreparedSend prepare(long offset, InvocationImpl invocation) {
    IngestionRequest request =
        IngestionRequest.newBuilder().setInvocation(invocation.toProtoInvocation(offset)).build();
    long bufferSize = request.getSerializedSize();
    if (bufferMemory > 0 && bufferSize > bufferMemory) {
      throw new IllegalArgumentException(
          "serialized invocation requires "
              + bufferSize
              + " bytes, exceeding bufferMemory "
              + bufferMemory);
    }
    return new PreparedSend(
        offset, request, request.getInvocation().getSerializedSize(), bufferSize);
  }

  private boolean canAdmitLocked(long requiredBytes) {
    if (bufferMemory == 0) {
      ClientCallStreamObserver<IngestionRequest> observer =
          Objects.requireNonNull(callObserver, "gRPC request stream was not initialized");
      return budget > 0 && observer.isReady();
    }
    return requiredBytes <= bufferMemory - bufferedBytes;
  }

  private CompletableFuture<SendResult> acceptLocked(PreparedSend prepared) {
    lastSent = prepared.offset();
    CompletableFuture<Long> committed = new CompletableFuture<>();
    ackWaiters.computeIfAbsent(prepared.offset(), ignored -> new ArrayList<>()).add(committed);
    if (bufferMemory == 0) {
      budget -= prepared.windowDebit();
    } else {
      bufferedSends.addLast(
          new BufferedSend(prepared.request(), prepared.windowDebit(), prepared.bufferSize()));
      bufferedBytes += prepared.bufferSize();
    }
    return committed.thenApply(ignored -> new SendResultImpl(prepared.offset()));
  }

  /**
   * Hand a zero-buffer invocation directly to gRPC, failing the producer if the write is refused.
   */
  private void writeDirect(IngestionRequest request) {
    try {
      Objects.requireNonNull(callObserver, "gRPC request stream was not initialized")
          .onNext(request);
    } catch (RuntimeException e) {
      IntegrationClientException cause =
          new IntegrationClientException(
              IntegrationClientException.Kind.UNKNOWN,
              "failed to write invocation to the ingestion stream",
              e);
      terminate(cause, false);
      throw cause;
    }
  }

  /** Write as many queued records as transport and protocol flow control currently permit. */
  private List<CompletableFuture<@Nullable Void>> drainLocked() {
    if (bufferMemory == 0) {
      if (!canAdmitLocked(0)) {
        return List.of();
      }
      lock.notifyAll();
      List<CompletableFuture<@Nullable Void>> ready = new ArrayList<>(admissionWaiters.size());
      for (AdmissionWaiter waiter : admissionWaiters) {
        ready.add(waiter.future());
      }
      admissionWaiters.clear();
      return ready;
    }

    boolean freedCapacity = false;
    ClientCallStreamObserver<IngestionRequest> observer =
        Objects.requireNonNull(callObserver, "gRPC request stream was not initialized");
    while (!closed && budget > 0 && observer.isReady() && !bufferedSends.isEmpty()) {
      BufferedSend send = bufferedSends.removeFirst();
      bufferedBytes -= send.bufferSize();
      budget -= send.windowDebit();
      freedCapacity = true;
      observer.onNext(send.request());
    }

    if (!freedCapacity) {
      return List.of();
    }

    lock.notifyAll();
    long available = bufferMemory - bufferedBytes;
    List<CompletableFuture<@Nullable Void>> ready = new ArrayList<>();
    for (Iterator<AdmissionWaiter> it = admissionWaiters.iterator(); it.hasNext(); ) {
      AdmissionWaiter waiter = it.next();
      if (waiter.requiredBytes() <= available) {
        ready.add(waiter.future());
        it.remove();
      }
    }
    return ready;
  }

  private void drainAndWake() {
    List<CompletableFuture<@Nullable Void>> ready;
    synchronized (lock) {
      ready = drainLocked();
    }
    completeReady(ready);
  }

  private static void completeReady(List<CompletableFuture<@Nullable Void>> ready) {
    for (CompletableFuture<@Nullable Void> future : ready) {
      future.complete(null);
    }
  }

  private void ensureOpenLocked() {
    if (closed) {
      throw new IllegalStateException("producer is closed", failure);
    }
  }

  private ProducerBufferExhaustedException admissionTimeout() {
    String condition =
        bufferMemory == 0 ? "producer remained backpressured" : "producer buffer remained full";
    return new ProducerBufferExhaustedException(condition + " for " + maxBlockTime);
  }

  private static long awaitFlush(CompletableFuture<Long> flush) {
    try {
      return flush.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IntegrationClientException(
          IntegrationClientException.Kind.UNKNOWN, "interrupted while flushing producer", e);
    } catch (ExecutionException e) {
      @Nullable Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      if (cause == null) {
        throw new IntegrationClientException(
            IntegrationClientException.Kind.UNKNOWN, "producer flush failed");
      }
      throw new IntegrationClientException(
          IntegrationClientException.Kind.UNKNOWN, "producer flush failed", cause);
    }
  }

  private static long toNanosSaturated(Duration duration) {
    try {
      return duration.toNanos();
    } catch (ArithmeticException ignored) {
      return Long.MAX_VALUE;
    }
  }

  private void onResponse(IngestionResponse resp) {
    @Nullable List<CompletableFuture<Long>> acksToComplete = null;
    long watermark = -1;
    boolean drain = false;
    @Nullable IntegrationClientException err = null;
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
        drain = true;
      } else if (resp.hasError()) {
        err = mapError(resp.getError());
      }
    }
    if (err != null) {
      terminate(err, false);
    } else if (drain) {
      drainAndWake();
    }
    if (acksToComplete != null) {
      for (CompletableFuture<Long> f : acksToComplete) {
        f.complete(watermark);
      }
    }
  }

  /** Mark the producer terminally closed and fail every pending future with {@code cause}. */
  private void terminate(IntegrationClientException cause, boolean halfClose) {
    List<CompletableFuture<@Nullable Void>> capacity;
    List<CompletableFuture<Long>> acks = new ArrayList<>();
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      failure = cause;
      capacity = new ArrayList<>(admissionWaiters.size());
      for (AdmissionWaiter waiter : admissionWaiters) {
        capacity.add(waiter.future());
      }
      admissionWaiters.clear();
      bufferedSends.clear();
      bufferedBytes = 0;
      lock.notifyAll();
      for (List<CompletableFuture<Long>> waiters : ackWaiters.values()) {
        acks.addAll(waiters);
      }
      ackWaiters.clear();
    }
    if (halfClose) {
      @Nullable ClientCallStreamObserver<IngestionRequest> obs = callObserver;
      if (obs != null) {
        try {
          obs.onCompleted();
        } catch (RuntimeException ignored) {
          // Already torn down transport-side; nothing to half-close.
        }
      }
    }
    for (CompletableFuture<@Nullable Void> f : capacity) {
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
      requestStream.setOnReadyHandler(AbstractProducer.this::drainAndWake);
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

  private record PreparedSend(
      long offset, IngestionRequest request, long windowDebit, long bufferSize) {}

  private record BufferedSend(IngestionRequest request, long windowDebit, long bufferSize) {}

  private record AdmissionWaiter(long requiredBytes, CompletableFuture<@Nullable Void> future) {}

  private record SendResultImpl(long offset) implements SendResult {}
}
