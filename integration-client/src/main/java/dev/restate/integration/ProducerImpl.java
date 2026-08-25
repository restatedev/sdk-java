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
import java.util.concurrent.locks.ReentrantLock;
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
final class ProducerImpl implements Producer, ExactlyOnceProducer {

  // Blocking from an inline transport/future callback can deadlock gRPC's serialized callback lane.
  private static final ThreadLocal<Boolean> INLINE_CALLBACK = new ThreadLocal<>();

  private final Object lock = new Object();

  // Set once, synchronously, in beforeStart() before the constructor sends the Start frame.
  private volatile @Nullable ClientCallStreamObserver<IngestionRequest> callObserver;

  // Reentrant because completing a future can synchronously call back into this producer.
  private final ReentrantLock usageGuard = new ReentrantLock();

  // ---- state guarded by `lock` ----
  private long budget = 0; // remaining Restate send window, in bytes; may go one message negative
  private long lastCommitted = -1; // ack watermark; -1 == nothing committed yet
  private final ArrayDeque<PreparedSend> pendingWrites = new ArrayDeque<>();
  private long bufferedBytes = 0;
  private final List<AdmissionWaiter> admissionWaiters = new ArrayList<>();
  private final TreeMap<Long, CompletableFuture<Long>> ackWaiters = new TreeMap<>();
  private @Nullable IntegrationClientException terminalFailure;
  // Exactly one thread at a time may call the non-thread-safe outbound observer.
  private boolean draining = false;
  private boolean halfClosePending = false;

  private final long bufferMemory;
  private final Duration maxBlockTime;
  private final long maxBlockNanos;
  private final boolean exactlyOnce;

  // Written only by the (guarded) caller thread; never touched by gRPC callbacks.
  private long lastSent = -1;

  ProducerImpl(
      IngestionSvcGrpc.IngestionSvcStub stub, ProducerOptions options, String integration) {
    this(stub, "", DeduplicationMode.DISABLED, options, integration);
  }

  ProducerImpl(
      IngestionSvcGrpc.IngestionSvcStub stub,
      String producerId,
      ProducerOptions options,
      String integration) {
    this(stub, producerId, DeduplicationMode.OFFSET_BASED, options, integration);
  }

  private ProducerImpl(
      IngestionSvcGrpc.IngestionSvcStub stub,
      String producerId,
      DeduplicationMode deduplicationMode,
      ProducerOptions options,
      String integration) {
    this.bufferMemory = options.bufferMemory();
    this.maxBlockTime = options.maxBlockTime();
    this.maxBlockNanos = toNanosSaturated(maxBlockTime);
    this.exactlyOnce = deduplicationMode == DeduplicationMode.OFFSET_BASED;
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
    writeToTransport(start);
  }

  // ---- Producer / ExactlyOnceProducer ----

  @Override
  public CompletableFuture<SendResult> send(Invocation invocation)
      throws ProducerBufferExhaustedException {
    acquire();
    try {
      checkMode(false);
      return doSend(nextOffset(), (InvocationImpl) invocation);
    } finally {
      release();
    }
  }

  @Override
  public SendAttempt trySend(Invocation invocation) {
    acquire();
    try {
      checkMode(false);
      return doTrySend(nextOffset(), (InvocationImpl) invocation);
    } finally {
      release();
    }
  }

  @Override
  public CompletableFuture<SendResult> send(long offset, Invocation invocation)
      throws ProducerBufferExhaustedException {
    acquire();
    try {
      checkMode(true);
      checkOffset(offset);
      return doSend(offset, (InvocationImpl) invocation);
    } finally {
      release();
    }
  }

  @Override
  public SendAttempt trySend(long offset, Invocation invocation) {
    acquire();
    try {
      checkMode(true);
      checkOffset(offset);
      return doTrySend(offset, (InvocationImpl) invocation);
    } finally {
      release();
    }
  }

  private void checkOffset(long offset) {
    if (offset <= lastSent) {
      throw new IllegalArgumentException(
          "offset must be strictly increasing; last sent " + lastSent + ", got " + offset);
    }
  }

  private long nextOffset() {
    if (lastSent == Long.MAX_VALUE) {
      throw new IllegalStateException("producer offset sequence is exhausted");
    }
    return lastSent + 1;
  }

  private void checkMode(boolean exactlyOnceExpected) {
    if (exactlyOnce != exactlyOnceExpected) {
      throw new IllegalStateException(
          exactlyOnce
              ? "exactly-once producers require explicit offsets"
              : "at-least-once producers assign offsets automatically");
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
      CompletableFuture<Long> flush = registerAckWaiter(lastSent);
      if (cannotBlockInline() && !flush.isDone()) {
        throw new IllegalStateException("cannot block in a reentrant producer call");
      }
      return awaitFlush(flush);
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
      return ackBarrierLocked(offset).copy();
    }
  }

  /** Returns the internal completion barrier shared by all waiters for {@code offset}. */
  private CompletableFuture<Long> ackBarrierLocked(long offset) {
    if (offset <= lastCommitted) {
      return CompletableFuture.completedFuture(lastCommitted);
    }
    if (terminalFailure != null) {
      return CompletableFuture.failedFuture(terminalFailure);
    }
    return ackWaiters.computeIfAbsent(offset, ignored -> new CompletableFuture<>());
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

  // ---- send path, shared by both producer modes (caller holds the guard) ----

  /** Admit a record, blocking up to the configured maximum while the producer is backpressured. */
  private CompletableFuture<SendResult> doSend(long offset, InvocationImpl invocation)
      throws ProducerBufferExhaustedException {
    PreparedSend prepared = prepare(offset, invocation);
    AcceptedSend accepted;
    long waitStarted = System.nanoTime();
    synchronized (lock) {
      ensureOpenLocked();
      while (!canAdmitLocked(prepared.size())) {
        if (maxBlockNanos == 0) {
          throw admissionTimeout();
        }
        if (cannotBlockInline()) {
          throw new IllegalStateException("cannot block in a reentrant producer call");
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
      accepted = acceptLocked(prepared);
    }
    drain(accepted.claimedWrite());
    return accepted.acknowledgement();
  }

  /** Attempt to admit a record without blocking or consuming an offset under backpressure. */
  private SendAttempt doTrySend(long offset, InvocationImpl invocation) {
    PreparedSend prepared = prepare(offset, invocation);
    SendAttempt result;
    @Nullable PreparedSend claimedWrite = null;
    boolean accepted = false;
    synchronized (lock) {
      ensureOpenLocked();
      if (canAdmitLocked(prepared.size())) {
        AcceptedSend acceptedSend = acceptLocked(prepared);
        result = new SendAttempt.Accepted(acceptedSend.acknowledgement());
        claimedWrite = acceptedSend.claimedWrite();
        accepted = true;
      } else {
        CompletableFuture<@Nullable Void> future = new CompletableFuture<>();
        AdmissionWaiter waiter = new AdmissionWaiter(prepared.size(), future);
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
      }
    }
    if (accepted) {
      drain(claimedWrite);
    }
    return result;
  }

  // ---- internals (all `*Locked` methods require `lock`) ----

  private PreparedSend prepare(long offset, InvocationImpl invocation) {
    IngestionRequest request =
        IngestionRequest.newBuilder().setInvocation(invocation.toProtoInvocation(offset)).build();
    long size = request.getInvocation().getSerializedSize();
    if (bufferMemory > 0 && size > bufferMemory) {
      throw new IllegalArgumentException(
          "serialized invocation requires "
              + size
              + " bytes, exceeding bufferMemory "
              + bufferMemory);
    }
    return new PreparedSend(offset, request, size);
  }

  private boolean canAdmitLocked(long requiredBytes) {
    if (bufferMemory == 0) {
      ClientCallStreamObserver<IngestionRequest> observer =
          Objects.requireNonNull(callObserver, "gRPC request stream was not initialized");
      return !draining && pendingWrites.isEmpty() && budget > 0 && observer.isReady();
    }
    return requiredBytes <= bufferMemory - bufferedBytes;
  }

  private AcceptedSend acceptLocked(PreparedSend prepared) {
    lastSent = prepared.offset();
    CompletableFuture<Long> committed = ackBarrierLocked(prepared.offset());
    pendingWrites.addLast(prepared);
    @Nullable PreparedSend claimedWrite = null;
    if (bufferMemory == 0) {
      // Direct admission reserves the observed readiness for this caller. Do not re-check it.
      draining = true;
      budget -= prepared.size();
      claimedWrite = prepared;
    } else {
      bufferedBytes += prepared.size();
    }
    return new AcceptedSend(
        committed.thenApply(ignored -> new SendResultImpl(prepared.offset())), claimedWrite);
  }

  /** Writes one request without holding {@link #lock}, terminating the producer on failure. */
  private void writeToTransport(IngestionRequest request) {
    ClientCallStreamObserver<IngestionRequest> observer =
        Objects.requireNonNull(callObserver, "gRPC request stream was not initialized");
    try {
      runInlineCallbacks(() -> observer.onNext(request));
    } catch (RuntimeException e) {
      IntegrationClientException cause = transportWriteFailure(e);
      failTransportWrite(observer, cause);
      throw cause;
    } catch (Error e) {
      IntegrationClientException cause = transportWriteFailure(e);
      failTransportWrite(observer, cause);
      throw e;
    }
  }

  private static IntegrationClientException transportWriteFailure(Throwable cause) {
    return new IntegrationClientException(
        IntegrationClientException.Kind.UNKNOWN,
        "failed to write invocation to the ingestion stream",
        cause);
  }

  private void failTransportWrite(
      ClientCallStreamObserver<IngestionRequest> observer, IntegrationClientException cause) {
    @Nullable Termination termination;
    synchronized (lock) {
      termination = beginTerminationLocked(cause, false);
      draining = false;
      // A failed write is cancelled, never followed by a deferred half-close.
      halfClosePending = false;
    }
    cancelTransport(observer, cause);
    if (termination != null) {
      finishTermination(termination);
    }
  }

  private static void cancelTransport(
      ClientCallStreamObserver<IngestionRequest> observer, IntegrationClientException cause) {
    try {
      observer.cancel(cause.getMessage(), cause);
    } catch (RuntimeException ignored) {
      // The failed write may already have torn down the call.
    }
  }

  private void drainFromCallback() {
    try {
      drain(null);
    } catch (IntegrationClientException ignored) {
      // writeToTransport already made the failure terminal and failed pending futures.
    }
  }

  /**
   * Hands accepted invocations to gRPC in FIFO order.
   *
   * <p>The queue head remains present while {@code onNext} runs, and {@link #draining} gives that
   * caller exclusive use of the outbound observer. This lets synchronous callbacks enqueue more
   * buffered writes, fail the producer, or request a deferred half-close without overlapping gRPC
   * calls. The observer and user futures are always invoked outside {@link #lock}.
   */
  private void drain(@Nullable PreparedSend send) {
    if (send == null) {
      List<CompletableFuture<@Nullable Void>> ready;
      synchronized (lock) {
        if (terminalFailure != null || draining) {
          return;
        }
        send = nextWriteLocked();
        if (send != null) {
          draining = true;
          ready = List.of();
        } else {
          ready = takeAdmissionWaitersLocked();
        }
      }
      if (send == null) {
        completeReady(ready);
        return;
      }
    }

    while (true) {
      writeToTransport(send.request());

      List<CompletableFuture<@Nullable Void>> ready;
      synchronized (lock) {
        if (pendingWrites.peekFirst() == send) {
          pendingWrites.removeFirst();
          if (bufferMemory > 0) {
            bufferedBytes -= send.size();
          }
          lock.notifyAll();
        }
        ready =
            terminalFailure == null && bufferMemory > 0 ? takeAdmissionWaitersLocked() : List.of();
      }
      completeReady(ready);

      boolean halfClose;
      @Nullable PreparedSend next;
      synchronized (lock) {
        // A synchronous transport or readiness callback may have changed the queue.
        next = nextWriteLocked();
        if (next == null) {
          draining = false;
          halfClose = halfClosePending;
          halfClosePending = false;
          ready = terminalFailure == null ? takeAdmissionWaitersLocked() : List.of();
        } else {
          halfClose = false;
          ready = List.of();
        }
      }
      if (halfClose) {
        completeRequestStream();
      }
      completeReady(ready);
      if (next == null) {
        return;
      }
      send = next;
    }
  }

  private @Nullable PreparedSend nextWriteLocked() {
    if (terminalFailure != null || pendingWrites.isEmpty() || budget <= 0) {
      return null;
    }
    ClientCallStreamObserver<IngestionRequest> observer =
        Objects.requireNonNull(callObserver, "gRPC request stream was not initialized");
    if (!observer.isReady()) {
      return null;
    }
    PreparedSend send = pendingWrites.getFirst();
    budget -= send.size();
    return send;
  }

  private List<CompletableFuture<@Nullable Void>> takeAdmissionWaitersLocked() {
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

  private boolean cannotBlockInline() {
    return usageGuard.getHoldCount() > 1 || INLINE_CALLBACK.get() != null;
  }

  private static void runInlineCallbacks(Runnable action) {
    boolean alreadyInline = INLINE_CALLBACK.get() != null;
    if (!alreadyInline) {
      INLINE_CALLBACK.set(true);
    }
    try {
      action.run();
    } finally {
      if (!alreadyInline) {
        INLINE_CALLBACK.remove();
      }
    }
  }

  private void completeReady(List<CompletableFuture<@Nullable Void>> ready) {
    runInlineCallbacks(
        () -> {
          for (CompletableFuture<@Nullable Void> future : ready) {
            @Nullable IntegrationClientException failure;
            synchronized (lock) {
              failure = terminalFailure;
            }
            if (failure == null) {
              future.complete(null);
            } else {
              future.completeExceptionally(failure);
            }
          }
        });
  }

  private void ensureOpenLocked() {
    if (terminalFailure != null) {
      throw new IllegalStateException("producer is closed", terminalFailure);
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
    List<CompletableFuture<Long>> acksToComplete = List.of();
    long watermark = -1;
    boolean drain = false;
    @Nullable Termination termination = null;
    synchronized (lock) {
      if (terminalFailure != null) {
        return;
      }
      if (resp.hasLastCommitted() && resp.getLastCommitted() > lastCommitted) {
        lastCommitted = resp.getLastCommitted();
        watermark = lastCommitted;
        if (!ackWaiters.isEmpty()) {
          Map<Long, CompletableFuture<Long>> head = ackWaiters.headMap(watermark, true);
          acksToComplete = new ArrayList<>(head.values());
          head.clear();
        }
      }
      if (resp.hasError()) {
        IntegrationClientException cause = mapError(resp.getError());
        termination = beginTerminationLocked(cause, false);
      } else if (resp.hasWindowUpdate()) {
        // increment_bytes is a uint32; read it as unsigned.
        budget += Integer.toUnsignedLong(resp.getWindowUpdate().getIncrementBytes());
        drain = true;
      }
    }
    if (termination != null) {
      finishTermination(termination);
    }
    List<CompletableFuture<Long>> completedAcks = acksToComplete;
    long committed = watermark;
    runInlineCallbacks(
        () -> {
          for (CompletableFuture<Long> future : completedAcks) {
            future.complete(committed);
          }
        });
    if (drain) {
      drainFromCallback();
    }
  }

  /** Mark the producer terminally closed and fail every pending future with {@code cause}. */
  private void terminate(IntegrationClientException cause, boolean halfClose) {
    @Nullable Termination termination;
    synchronized (lock) {
      termination = beginTerminationLocked(cause, halfClose);
    }
    if (termination != null) {
      finishTermination(termination);
    }
  }

  private @Nullable Termination beginTerminationLocked(
      IntegrationClientException cause, boolean halfClose) {
    if (terminalFailure != null) {
      return null;
    }
    terminalFailure = cause;

    List<CompletableFuture<@Nullable Void>> capacity = new ArrayList<>(admissionWaiters.size());
    for (AdmissionWaiter waiter : admissionWaiters) {
      capacity.add(waiter.future());
    }
    admissionWaiters.clear();
    pendingWrites.clear();
    bufferedBytes = 0;
    lock.notifyAll();

    List<CompletableFuture<Long>> acks = new ArrayList<>(ackWaiters.values());
    ackWaiters.clear();

    boolean completeStream = halfClose && !draining;
    if (halfClose && draining) {
      halfClosePending = true;
    }
    return new Termination(cause, capacity, acks, completeStream);
  }

  private void finishTermination(Termination termination) {
    if (termination.completeStream()) {
      completeRequestStream();
    }
    runInlineCallbacks(
        () -> {
          for (CompletableFuture<@Nullable Void> future : termination.capacity()) {
            future.completeExceptionally(termination.cause());
          }
          for (CompletableFuture<Long> future : termination.acknowledgements()) {
            future.completeExceptionally(termination.cause());
          }
        });
  }

  private void completeRequestStream() {
    @Nullable ClientCallStreamObserver<IngestionRequest> observer = callObserver;
    if (observer == null) {
      return;
    }
    try {
      observer.onCompleted();
    } catch (RuntimeException ignored) {
      // Already torn down transport-side; nothing to half-close.
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

  private void acquire() {
    if (!usageGuard.tryLock()) {
      throw new ConcurrentModificationException("Producer is not safe for multi-threaded access");
    }
  }

  private void release() {
    usageGuard.unlock();
  }

  private final class ResponseObserver
      implements ClientResponseObserver<IngestionRequest, IngestionResponse> {
    @Override
    public void beforeStart(ClientCallStreamObserver<IngestionRequest> requestStream) {
      callObserver = requestStream;
      requestStream.setOnReadyHandler(ProducerImpl.this::drainFromCallback);
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

  private record PreparedSend(long offset, IngestionRequest request, long size) {}

  private record AcceptedSend(
      CompletableFuture<SendResult> acknowledgement, @Nullable PreparedSend claimedWrite) {}

  private record AdmissionWaiter(long requiredBytes, CompletableFuture<@Nullable Void> future) {}

  private record Termination(
      IntegrationClientException cause,
      List<CompletableFuture<@Nullable Void>> capacity,
      List<CompletableFuture<Long>> acknowledgements,
      boolean completeStream) {}

  private record SendResultImpl(long offset) implements SendResult {}
}
