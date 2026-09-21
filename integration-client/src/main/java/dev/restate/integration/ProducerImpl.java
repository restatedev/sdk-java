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
  private static final ReadinessObservation BUFFERED_READY = new ReadinessObservation(true, -1);

  // The protocol guarantees a hard-coded minimum send window of 32 KiB. The client assumes this as
  // its initial credit and starts sending without waiting for the server's first WindowUpdate; the
  // server never sends an initial one, never lowers the window below this floor, and only grows it
  // via additive increments.
  private static final long INITIAL_WINDOW = 32L * 1024;

  private final Object lock = new Object();
  // Held across each actual outbound observer call. Terminal state is always recorded under
  // `lock` before waiting for this gate, so a terminal callback cannot starve behind the drain.
  private final ReentrantLock outboundLock = new ReentrantLock();

  // Set once, synchronously, in beforeStart() before the constructor sends the Start frame.
  private volatile @Nullable ClientCallStreamObserver<IngestionRequest> callObserver;

  // Reentrant because completing a future can synchronously call back into this producer.
  private final ReentrantLock usageGuard = new ReentrantLock();

  // ---- state guarded by `lock` ----
  // remaining Restate send window, in bytes; starts at the protocol's hard-coded 32 KiB minimum and
  // may go one message negative
  private long budget = INITIAL_WINDOW;
  private long lastCommitted = -1; // ack watermark; -1 == nothing committed yet
  private final ArrayDeque<PreparedSend> pendingWrites = new ArrayDeque<>();
  private long bufferedBytes = 0;
  private final List<AdmissionWaiter> admissionWaiters = new ArrayList<>();
  private final TreeMap<Long, CompletableFuture<Long>> ackWaiters = new TreeMap<>();
  private @Nullable IntegrationClientException terminalFailure;
  // Exactly one thread at a time may call the non-thread-safe outbound observer.
  private boolean draining = false;
  private boolean outboundWriteActive = false;
  private boolean outboundTerminated = false;
  private OutboundAction pendingOutboundAction = OutboundAction.NONE;
  private long readinessEpoch = 0;

  // The mandatory Start handshake frame, written to the transport before any invocation. It is
  // written lazily, on the first transport-ready signal, rather than eagerly in the constructor:
  // some transports (e.g. the Vert.x gRPC bridge) deliver the initial onReady synchronously while
  // the request is still being set up, which would otherwise let the onReady-driven drain flush
  // buffered invocations ahead of an eagerly-queued Start. `startWritten` gates invocation writes
  // so
  // none can precede the Start regardless of when onReady fires.
  private @Nullable IngestionRequest pendingStart;
  private boolean startWritten = false;

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
    // Mandatory Start handshake: the first frame on the stream (not flow-controlled). It is written
    // lazily by writeStartIfNeeded() on the first transport-ready signal, ahead of any invocation;
    // see the pendingStart/startWritten fields. Stage it before opening the call so a transport
    // that
    // delivers onReady synchronously during stub.ingest() still finds it.
    synchronized (lock) {
      pendingStart =
          IngestionRequest.newBuilder()
              .setStart(
                  IngestionStart.newBuilder()
                      .setProducerId(producerId)
                      .setIntegration(integration)
                      .setDeduplicationMode(deduplicationMode)
                      .setDefaults(options.toDefaults()))
              .build();
    }
    // Opening the call invokes beforeStart() synchronously, wiring callObserver + the ready
    // handler.
    stub.ingest(new ResponseObserver());
    // If the transport is already writable, write the Start now: some transports report readiness
    // without ever emitting an onReady callback, so we cannot wait for one. Transports that are not
    // yet ready (e.g. a connection still being established) instead write it from onTransportReady.
    ClientCallStreamObserver<IngestionRequest> observer = callObserver;
    if (observer != null && observer.isReady()) {
      writeStartIfNeeded();
    }
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
          OutboundAction.HALF_CLOSE);
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
    while (true) {
      ReadinessObservation readiness =
          bufferMemory == 0 ? observeTransportReadiness() : BUFFERED_READY;
      synchronized (lock) {
        ensureOpenLocked();
        if (bufferMemory == 0 && readiness.epoch() != readinessEpoch) {
          continue;
        }
        if (canAdmitLocked(prepared.size(), readiness.ready())) {
          accepted = acceptLocked(prepared);
          break;
        }
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
    while (true) {
      ReadinessObservation readiness =
          bufferMemory == 0 ? observeTransportReadiness() : BUFFERED_READY;
      synchronized (lock) {
        ensureOpenLocked();
        if (bufferMemory == 0 && readiness.epoch() != readinessEpoch) {
          continue;
        }
        if (canAdmitLocked(prepared.size(), readiness.ready())) {
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
        break;
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

  private boolean canAdmitLocked(long requiredBytes, boolean transportReady) {
    if (bufferMemory == 0) {
      // startWritten: a direct write may only proceed once the Start frame is on the wire.
      return terminalFailure == null
          && startWritten
          && !draining
          && pendingWrites.isEmpty()
          && budget > 0
          && transportReady;
    }
    return requiredBytes <= bufferMemory - bufferedBytes;
  }

  private ReadinessObservation observeTransportReadiness() {
    long observedEpoch;
    synchronized (lock) {
      observedEpoch = readinessEpoch;
      if (terminalFailure != null || outboundTerminated || outboundWriteActive) {
        return new ReadinessObservation(false, observedEpoch);
      }
    }
    if (!outboundLock.tryLock()) {
      return new ReadinessObservation(false, observedEpoch);
    }
    boolean reservationAcquired = false;
    boolean noReadinessSignalBeforeRelease = false;
    long completedEpoch = observedEpoch;
    boolean ready = false;
    try {
      ClientCallStreamObserver<IngestionRequest> observer;
      synchronized (lock) {
        observedEpoch = readinessEpoch;
        if (terminalFailure != null || outboundTerminated || outboundWriteActive) {
          return new ReadinessObservation(false, observedEpoch);
        }
        outboundWriteActive = true;
        reservationAcquired = true;
        observer = Objects.requireNonNull(callObserver, "gRPC request stream was not initialized");
      }

      try {
        ready = observer.isReady();
      } finally {
        OutboundAction action;
        synchronized (lock) {
          noReadinessSignalBeforeRelease = readinessEpoch == observedEpoch;
          outboundWriteActive = false;
          action = claimPendingOutboundActionLocked();
        }
        performOutboundAction(action);
      }
    } finally {
      outboundLock.unlock();
      if (reservationAcquired) {
        completedEpoch = publishOutboundAvailability(observedEpoch, noReadinessSignalBeforeRelease);
      }
    }
    return new ReadinessObservation(ready, completedEpoch);
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
    long acceptedOffset = prepared.offset();
    return new AcceptedSend(
        committed.thenApply(ignored -> new SendResultImpl(acceptedOffset)), claimedWrite);
  }

  /**
   * Writes one request without holding {@link #lock}, terminating the producer on failure.
   *
   * @return whether the request was written; {@code false} means a terminal callback won before the
   *     write began
   */
  private boolean writeToTransport(IngestionRequest request) {
    ClientCallStreamObserver<IngestionRequest> observer =
        Objects.requireNonNull(callObserver, "gRPC request stream was not initialized");
    @Nullable Termination termination = null;
    @Nullable Throwable thrown = null;
    @Nullable RuntimeException runtimeFailure = null;
    boolean reservationAcquired = false;
    outboundLock.lock();
    try {
      synchronized (lock) {
        if (terminalFailure != null || outboundTerminated) {
          return false;
        }
        outboundWriteActive = true;
        reservationAcquired = true;
      }

      try {
        runInlineCallbacks(() -> observer.onNext(request));
      } catch (RuntimeException | Error t) {
        thrown = t;
      }

      OutboundAction outboundAction;
      synchronized (lock) {
        if (thrown != null) {
          termination = beginTerminationLocked(transportWriteFailure(thrown));
          draining = false;
          scheduleOutboundActionLocked(OutboundAction.CANCEL);
          if (thrown instanceof RuntimeException) {
            runtimeFailure = Objects.requireNonNull(terminalFailure);
          }
        }
        outboundWriteActive = false;
        outboundAction = claimPendingOutboundActionLocked();
      }
      performOutboundAction(outboundAction);
    } finally {
      outboundLock.unlock();
      if (reservationAcquired) {
        publishOutboundAvailability();
      }
    }

    if (termination != null) {
      finishTermination(termination);
    }
    if (thrown instanceof Error error) {
      throw error;
    }
    if (runtimeFailure != null) {
      throw runtimeFailure;
    }
    return true;
  }

  private static IntegrationClientException transportWriteFailure(Throwable cause) {
    return new IntegrationClientException(
        IntegrationClientException.Kind.UNKNOWN,
        "failed to write invocation to the ingestion stream",
        cause);
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

  private void onTransportReady() {
    synchronized (lock) {
      readinessEpoch++;
      lock.notifyAll();
    }
    // Write the Start frame before draining any invocation, so it is always the first frame on the
    // wire even when this ready signal is delivered synchronously while the request is being set
    // up.
    writeStartIfNeeded();
    drainFromCallback();
  }

  /**
   * Writes the pending Start handshake frame exactly once, on the first transport-ready signal.
   * Invocation writes are gated on {@link #startWritten} (see {@link #nextWriteLocked} and {@link
   * #canAdmitLocked}), so this guarantees the Start is the first frame regardless of whether the
   * transport delivers onReady synchronously or asynchronously.
   */
  private void writeStartIfNeeded() {
    IngestionRequest start;
    synchronized (lock) {
      if (pendingStart == null || terminalFailure != null || outboundTerminated) {
        return;
      }
      start = pendingStart;
      pendingStart = null;
      // startWritten stays false across the write below. Publishing it before the Start is actually
      // handed to the outbound observer would let a concurrent sender admit an invocation and race
      // its write ahead of the Start on the transport (both writes contend on `outboundLock`).
    }
    writeToTransport(start);
    synchronized (lock) {
      startWritten = true;
      // Wake any sender blocked in doSend() waiting on the Start; trySend admission waiters and
      // buffered records are handled by the drain that follows this call.
      lock.notifyAll();
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
        // Claim drain ownership before sampling readiness so a concurrent sender cannot create a
        // second sampler and then lose its wake-up to this one.
        draining = true;
      }
      while (true) {
        ReadinessObservation readiness = observeTransportReadiness();
        synchronized (lock) {
          if (terminalFailure != null) {
            draining = false;
            return;
          }
          if (readiness.epoch() != readinessEpoch) {
            continue;
          }
          send = nextWriteLocked(readiness.ready());
          if (send == null) {
            draining = false;
            ready = takeAdmissionWaitersLocked(readiness.ready());
          } else {
            ready = List.of();
          }
          break;
        }
      }
      if (send == null) {
        completeReady(ready);
        return;
      }
    }

    while (true) {
      if (!writeToTransport(send.request())) {
        synchronized (lock) {
          draining = false;
          lock.notifyAll();
        }
        return;
      }

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
            terminalFailure == null && bufferMemory > 0
                ? takeAdmissionWaitersLocked(false)
                : List.of();
      }
      completeReady(ready);

      @Nullable PreparedSend next;
      while (true) {
        ReadinessObservation readiness = observeTransportReadiness();
        synchronized (lock) {
          if (readiness.epoch() != readinessEpoch) {
            continue;
          }
          next = nextWriteLocked(readiness.ready());
          if (next == null) {
            draining = false;
            ready =
                terminalFailure == null ? takeAdmissionWaitersLocked(readiness.ready()) : List.of();
          } else {
            ready = List.of();
          }
          break;
        }
      }
      completeReady(ready);
      if (next == null) {
        return;
      }
      send = next;
    }
  }

  private @Nullable PreparedSend nextWriteLocked(boolean transportReady) {
    // startWritten: no invocation may be written before the Start handshake frame.
    if (!startWritten
        || terminalFailure != null
        || pendingWrites.isEmpty()
        || budget <= 0
        || !transportReady) {
      return null;
    }
    PreparedSend send = pendingWrites.getFirst();
    budget -= send.size();
    return send;
  }

  private List<CompletableFuture<@Nullable Void>> takeAdmissionWaitersLocked(
      boolean transportReady) {
    if (bufferMemory == 0) {
      if (!canAdmitLocked(0, transportReady)) {
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
    if (resp.hasError()) {
      onErrorResponse(resp);
      return;
    }

    Acknowledgements acknowledgements;
    boolean drain = false;
    synchronized (lock) {
      if (terminalFailure != null) {
        return;
      }
      acknowledgements = advanceCommittedLocked(resp);
      if (resp.hasWindowUpdate()) {
        // increment_bytes is a uint32; read it as unsigned.
        long increment = Integer.toUnsignedLong(resp.getWindowUpdate().getIncrementBytes());
        budget = addSaturated(budget, increment);
        drain = true;
      }
    }
    completeAcknowledgements(acknowledgements);
    if (drain) {
      drainFromCallback();
    }
  }

  private void onErrorResponse(IngestionResponse resp) {
    Acknowledgements acknowledgements = new Acknowledgements(-1, List.of());
    @Nullable Termination termination = null;
    synchronized (lock) {
      if (terminalFailure == null) {
        acknowledgements = advanceCommittedLocked(resp);
        termination = beginTerminationLocked(mapError(resp.getError()));
      }
      scheduleOutboundActionLocked(OutboundAction.CANCEL);
    }

    // Shut down the transport before invoking user continuations: a continuation may block, but it
    // must not prevent an Error response from cancelling the request stream. A response carrying
    // both fields still completes the acknowledged records before failing the rest.
    try {
      flushPendingOutboundAction();
    } finally {
      completeAcknowledgements(acknowledgements);
      if (termination != null) {
        finishTermination(termination);
      }
    }
  }

  private Acknowledgements advanceCommittedLocked(IngestionResponse resp) {
    if (!resp.hasLastCommitted()) {
      return new Acknowledgements(lastCommitted, List.of());
    }

    long candidate = resp.getLastCommitted();
    // last_committed is uint64 on the wire. Java exposes values above Long.MAX_VALUE as negative;
    // every representable producer offset is necessarily covered by such a watermark.
    if (candidate < 0) {
      candidate = Long.MAX_VALUE;
    }
    if (candidate <= lastCommitted) {
      return new Acknowledgements(lastCommitted, List.of());
    }

    lastCommitted = candidate;
    List<CompletableFuture<Long>> completed = List.of();
    if (!ackWaiters.isEmpty()) {
      Map<Long, CompletableFuture<Long>> head = ackWaiters.headMap(lastCommitted, true);
      completed = new ArrayList<>(head.values());
      head.clear();
    }
    return new Acknowledgements(lastCommitted, completed);
  }

  private void completeAcknowledgements(Acknowledgements acknowledgements) {
    runInlineCallbacks(
        () -> {
          for (CompletableFuture<Long> future : acknowledgements.futures()) {
            future.complete(acknowledgements.watermark());
          }
        });
  }

  /** Mark the producer terminally closed and fail every pending future with {@code cause}. */
  private void terminate(IntegrationClientException cause, OutboundAction outboundAction) {
    @Nullable Termination termination;
    synchronized (lock) {
      termination = beginTerminationLocked(cause);
      if (termination == null) {
        return;
      }
      scheduleOutboundActionLocked(outboundAction);
    }
    // User continuations run inline when their futures complete. End the request stream first so a
    // blocking continuation cannot delay close/cancellation, while still guaranteeing completion
    // if the transport observer throws an Error.
    try {
      flushPendingOutboundAction();
    } finally {
      finishTermination(termination);
    }
  }

  private @Nullable Termination beginTerminationLocked(IntegrationClientException cause) {
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
    return new Termination(cause, capacity, acks);
  }

  private void finishTermination(Termination termination) {
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

  private void scheduleOutboundActionLocked(OutboundAction action) {
    if (!outboundTerminated && action.ordinal() > pendingOutboundAction.ordinal()) {
      pendingOutboundAction = action;
    }
  }

  /** Publish that a readiness query or write has released the outbound observer gate. */
  private void publishOutboundAvailability() {
    synchronized (lock) {
      readinessEpoch++;
      lock.notifyAll();
    }
  }

  /** Publish a completed readiness observation and return the epoch its caller may consume. */
  private long publishOutboundAvailability(
      long observedEpoch, boolean noReadinessSignalBeforeRelease) {
    synchronized (lock) {
      boolean observationIsCurrent =
          noReadinessSignalBeforeRelease && readinessEpoch == observedEpoch;
      readinessEpoch++;
      lock.notifyAll();
      // If onReady ran at any point during the query, deliberately return a stale epoch so the
      // caller samples again. Otherwise it may consume the post-release publication.
      return observationIsCurrent ? readinessEpoch : observedEpoch;
    }
  }

  private OutboundAction claimPendingOutboundActionLocked() {
    if (outboundWriteActive || outboundTerminated || pendingOutboundAction == OutboundAction.NONE) {
      return OutboundAction.NONE;
    }

    OutboundAction action = pendingOutboundAction;
    pendingOutboundAction = OutboundAction.NONE;
    outboundTerminated = true;
    return action;
  }

  private void performOutboundAction(OutboundAction action) {
    if (action == OutboundAction.NONE) {
      return;
    }
    @Nullable ClientCallStreamObserver<IngestionRequest> observer = callObserver;
    if (observer == null) {
      return;
    }

    if (action == OutboundAction.CANCEL) {
      IntegrationClientException cause;
      synchronized (lock) {
        cause = Objects.requireNonNull(terminalFailure);
      }
      cancelTransport(observer, cause);
    } else {
      try {
        observer.onCompleted();
      } catch (RuntimeException ignored) {
        // Already torn down transport-side; nothing to half-close.
      }
    }
  }

  private void flushPendingOutboundAction() {
    outboundLock.lock();
    try {
      OutboundAction action;
      synchronized (lock) {
        action = claimPendingOutboundActionLocked();
      }
      performOutboundAction(action);
    } finally {
      outboundLock.unlock();
    }
  }

  private void terminateFromTransport(IntegrationClientException cause) {
    @Nullable Termination termination;
    synchronized (lock) {
      // The peer has already ended the RPC, so suppress any deferred local terminal action.
      outboundTerminated = true;
      pendingOutboundAction = OutboundAction.NONE;
      termination = beginTerminationLocked(cause);
    }
    // If a writer reserved the outbound gate first, do not return the peer terminal callback until
    // that write either observes the terminal state or finishes its already-started onNext.
    outboundLock.lock();
    outboundLock.unlock();
    if (termination != null) {
      finishTermination(termination);
    }
  }

  private static long addSaturated(long value, long increment) {
    return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
  }

  private static IntegrationClientException mapError(dev.restate.ingestion.v1.Error error) {
    String detail =
        error.hasInvocationOffset()
            ? "[offset="
                + Long.toUnsignedString(error.getInvocationOffset())
                + "] "
                + error.getMessage()
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
      requestStream.setOnReadyHandler(ProducerImpl.this::onTransportReady);
    }

    @Override
    public void onNext(IngestionResponse value) {
      onResponse(value);
    }

    @Override
    public void onError(Throwable t) {
      terminateFromTransport(
          new IntegrationClientException(
              IntegrationClientException.Kind.UNKNOWN,
              "ingestion stream failed: " + t.getMessage(),
              t));
    }

    @Override
    public void onCompleted() {
      terminateFromTransport(
          new IntegrationClientException(
              IntegrationClientException.Kind.UNKNOWN, "ingestion stream closed by server"));
    }
  }

  private record PreparedSend(long offset, IngestionRequest request, long size) {}

  private record AcceptedSend(
      CompletableFuture<SendResult> acknowledgement, @Nullable PreparedSend claimedWrite) {}

  private record AdmissionWaiter(long requiredBytes, CompletableFuture<@Nullable Void> future) {}

  private record Termination(
      IntegrationClientException cause,
      List<CompletableFuture<@Nullable Void>> capacity,
      List<CompletableFuture<Long>> acknowledgements) {}

  private record Acknowledgements(long watermark, List<CompletableFuture<Long>> futures) {}

  private record ReadinessObservation(boolean ready, long epoch) {}

  private enum OutboundAction {
    NONE,
    HALF_CLOSE,
    CANCEL
  }

  private record SendResultImpl(long offset) implements SendResult {}
}
