// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.restate.ingestion.v1.DeduplicationMode;
import dev.restate.ingestion.v1.ErrorKind;
import dev.restate.ingestion.v1.IngestionInvocation;
import dev.restate.ingestion.v1.IngestionRequest;
import dev.restate.ingestion.v1.IngestionResponse;
import dev.restate.ingestion.v1.IngestionSvcGrpc;
import dev.restate.ingestion.v1.WindowUpdate;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Drives the producer client against an in-process fake {@code IngestionSvc}. */
class IntegrationClientTest {

  private static final String INTEGRATION = "test-integration/1.0";

  private Server server;
  private ManagedChannel channel;
  private FakeIngestionService fake;
  private IntegrationClient client;

  @BeforeEach
  void setUp() throws IOException {
    String name = InProcessServerBuilder.generateName();
    fake = new FakeIngestionService();
    server = InProcessServerBuilder.forName(name).directExecutor().addService(fake).build().start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    client = IntegrationClientImpl.forChannel(channel, INTEGRATION);
  }

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.close();
    }
    if (server != null) {
      server.shutdownNow();
    }
  }

  @Test
  void producerSendsDisabledDedupHandshake() throws Exception {
    InvocationMetadata defaults = InvocationMetadata.create().setServiceName("Svc");
    client.newProducer(defaults);

    IngestionRequest start = fake.take();
    assertThat(start.hasStart()).isTrue();
    assertThat(start.getStart().getProducerId()).isEmpty();
    assertThat(start.getStart().getIntegration()).isEqualTo(INTEGRATION);
    assertThat(start.getStart().getDeduplicationMode()).isEqualTo(DeduplicationMode.DISABLED);
    assertThat(start.getStart().getDefaults().getService()).isEqualTo("Svc");
  }

  @Test
  void producerOptionsHaveKafkaCompatibleDefaultsAndSnapshotMetadata() throws Exception {
    InvocationMetadata metadata = InvocationMetadata.create().setServiceName("Original");
    ProducerOptions options = ProducerOptions.builder().defaultMetadata(metadata).build();
    metadata.setServiceName("Changed");

    assertThat(options.bufferMemory()).isEqualTo(32L * 1024 * 1024);
    assertThat(options.maxBlockTime()).isEqualTo(Duration.ofMinutes(1));

    client.newProducer(options);
    assertThat(fake.take().getStart().getDefaults().getService()).isEqualTo("Original");
  }

  @Test
  void exactlyOnceProducerAcceptsProducerOptions() throws Exception {
    ProducerOptions options =
        ProducerOptions.builder()
            .defaultMetadata(InvocationMetadata.create().setHandlerName("handle"))
            .build();

    client.newExactlyOnceProducer("producer-1", options);

    IngestionRequest start = fake.take();
    assertThat(start.getStart().getProducerId()).isEqualTo("producer-1");
    assertThat(start.getStart().getDefaults().getHandler()).isEqualTo("handle");
  }

  @Test
  void producerOptionsValidateBufferAndBlockTime() {
    assertThatThrownBy(() -> ProducerOptions.builder().bufferMemory(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProducerOptions.builder().maxBlockTime(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProducerOptions.builder().maxBlockTime(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> ProducerOptions.builder().defaultMetadata(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void exactlyOnceProducerSendsOffsetBasedHandshake() throws Exception {
    client.newExactlyOnceProducer("producer-1");

    IngestionRequest start = fake.take();
    assertThat(start.hasStart()).isTrue();
    assertThat(start.getStart().getProducerId()).isEqualTo("producer-1");
    assertThat(start.getStart().getDeduplicationMode()).isEqualTo(DeduplicationMode.OFFSET_BASED);
  }

  @Test
  void exactlyOnceProducerRequiresProducerId() {
    assertThatThrownBy(() -> client.newExactlyOnceProducer(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.newExactlyOnceProducer(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void producerAssignsMonotonicOffsetsAndFuturesCompleteOnCommit() throws Exception {
    Producer producer = client.newProducer();
    fake.take(); // Start
    fake.grantWindow(10_000);

    assertThat(producer.lastAcknowledgedOffset()).isEqualTo(-1L);

    CompletableFuture<SendResult> a = producer.send(newBody("a"));
    CompletableFuture<SendResult> b = producer.send(newBody("b"));
    CompletableFuture<SendResult> c = producer.send(newBody("c"));
    assertThat(producer.lastSentOffset()).isEqualTo(2L);

    assertThat(fake.take().getInvocation().getOffset()).isEqualTo(0L);
    assertThat(fake.take().getInvocation().getOffset()).isEqualTo(1L);
    assertThat(fake.take().getInvocation().getOffset()).isEqualTo(2L);

    // The send futures resolve on durable commit, each yielding its own offset.
    assertThat(a).isNotDone();
    fake.ack(2L);
    assertThat(get(a).offset()).isEqualTo(0L);
    assertThat(get(b).offset()).isEqualTo(1L);
    assertThat(get(c).offset()).isEqualTo(2L);
    assertThat(producer.lastAcknowledgedOffset()).isEqualTo(2L);
  }

  @Test
  void lastAcknowledgedOffsetRemainsAvailableAfterFailure() throws Exception {
    Producer producer = client.newProducer();
    fake.take(); // Start
    fake.grantWindow(10_000);
    producer.send(newBody("a"));
    producer.send(newBody("b"));

    fake.error(ErrorKind.ERROR_KIND_BAD_REQUEST, "nope", 0L);

    assertThat(producer.lastAcknowledgedOffset()).isEqualTo(0L);
  }

  @Test
  void invocationTypesAreSealedToSdkImplementations() {
    assertThat(Invocation.class.getPermittedSubclasses()).containsExactly(InvocationImpl.class);
    assertThat(InvocationMetadata.class.getPermittedSubclasses())
        .containsExactlyInAnyOrder(Invocation.class, InvocationMetadataImpl.class);
    assertThat(InvocationMetadataImpl.class.getPermittedSubclasses())
        .containsExactly(InvocationImpl.class);
  }

  @Test
  void sendBuffersBeforeInitialWindowGrant() throws Exception {
    Producer producer = client.newProducer();
    fake.take(); // Start

    CompletableFuture<SendResult> acknowledgement = producer.send(newBody("a"));
    assertThat(producer.lastSentOffset()).isEqualTo(0L);
    assertThat(acknowledgement).isNotDone();
    fake.assertNoRequest();

    fake.grantWindow(10_000);
    assertThat(fake.take().getInvocation().getOffset()).isEqualTo(0L);

    fake.ack(0L);
    assertThat(get(acknowledgement).offset()).isEqualTo(0L);
  }

  @Test
  void trySendReportsBackpressureAndSignalsWhenCapacityReturns() throws Exception {
    Producer producer =
        client.newProducer(
            ProducerOptions.builder().bufferMemory(128).maxBlockTime(Duration.ZERO).build());
    fake.take(); // Start

    SendAttempt first = producer.trySend(newBody("a".repeat(80)));
    assertThat(first).isInstanceOf(SendAttempt.Accepted.class);

    SendAttempt second = producer.trySend(newBody("b".repeat(80)));
    assertThat(second).isInstanceOf(SendAttempt.Backpressured.class);
    CompletableFuture<Void> ready = ((SendAttempt.Backpressured) second).ready();
    assertThat(producer.lastSentOffset()).isEqualTo(0L);
    assertThat(ready).isNotDone();

    fake.grantWindow(10_000);
    get(ready);

    SendAttempt retried = producer.trySend(newBody("b".repeat(80)));
    assertThat(retried).isInstanceOf(SendAttempt.Accepted.class);
    assertThat(producer.lastSentOffset()).isEqualTo(1L);
    assertThat(fake.take().getInvocation().getOffset()).isEqualTo(0L);
    assertThat(fake.take().getInvocation().getOffset()).isEqualTo(1L);
  }

  @Test
  void sendBlocksUntilBufferCapacityReturns() throws Exception {
    Producer producer =
        client.newProducer(
            ProducerOptions.builder()
                .bufferMemory(128)
                .maxBlockTime(Duration.ofSeconds(5))
                .build());
    fake.take(); // Start
    producer.send(newBody("a".repeat(80)));

    CountDownLatch attempting = new CountDownLatch(1);
    CompletableFuture<CompletableFuture<SendResult>> blocked =
        CompletableFuture.supplyAsync(
            () -> {
              attempting.countDown();
              return producer.send(newBody("b".repeat(80)));
            });
    assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
    Thread.sleep(50);
    assertThat(blocked).isNotDone();

    fake.grantWindow(10_000);
    get(blocked);
    assertThat(fake.take().getInvocation().getOffset()).isEqualTo(0L);
    assertThat(fake.take().getInvocation().getOffset()).isEqualTo(1L);
  }

  @Test
  void zeroMaxBlockTimeFailsWithoutConsumingOffset() throws Exception {
    Producer producer =
        client.newProducer(
            ProducerOptions.builder().bufferMemory(128).maxBlockTime(Duration.ZERO).build());
    fake.take(); // Start
    producer.send(newBody("a".repeat(80)));

    assertThatThrownBy(() -> producer.send(newBody("b".repeat(80))))
        .isInstanceOf(ProducerBufferExhaustedException.class);
    assertThat(producer.lastSentOffset()).isEqualTo(0L);
  }

  @Test
  void oversizedInvocationIsRejectedWithoutConsumingOffset() throws Exception {
    Producer producer = client.newProducer(ProducerOptions.builder().bufferMemory(64).build());
    fake.take(); // Start

    assertThatThrownBy(() -> producer.send(newBody("a".repeat(100))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeding bufferMemory");
    assertThat(producer.lastSentOffset()).isEqualTo(-1L);
  }

  @Test
  void waitAcknowledgedCompletesAtWatermark() throws Exception {
    Producer producer = client.newProducer();
    fake.take(); // Start
    fake.grantWindow(10_000);
    producer.send(newBody("a")); // offset 0
    producer.send(newBody("b")); // offset 1

    CompletableFuture<Long> acked = producer.waitAcknowledged(1L);
    assertThat(acked).isNotDone();

    fake.ack(0L);
    assertThat(acked).isNotDone();

    fake.ack(1L);
    assertThat(get(acked)).isEqualTo(1L);
  }

  @Test
  void flushAsyncCompletesWhenEverythingSentIsCommitted() throws Exception {
    Producer producer = client.newProducer();
    fake.take(); // Start
    fake.grantWindow(10_000);
    CompletableFuture<SendResult> a = producer.send(newBody("a")); // offset 0
    CompletableFuture<SendResult> b = producer.send(newBody("b")); // offset 1

    CompletableFuture<Long> flushed = producer.flushAsync(); // waits up to the last sent offset (1)
    assertThat(a).isNotDone();
    assertThat(flushed).isNotDone();

    fake.ack(0L);
    assertThat(get(a).offset()).isEqualTo(0L);
    assertThat(b).isNotDone();
    assertThat(flushed).isNotDone();

    fake.ack(1L);
    assertThat(get(b).offset()).isEqualTo(1L);
    assertThat(get(flushed)).isEqualTo(1L); // last durably committed offset
  }

  @Test
  void flushBlocksUntilEverythingSentIsCommitted() throws Exception {
    Producer producer = client.newProducer();
    fake.take(); // Start
    fake.grantWindow(10_000);
    producer.send(newBody("a"));

    CountDownLatch flushing = new CountDownLatch(1);
    CompletableFuture<Long> flushed =
        CompletableFuture.supplyAsync(
            () -> {
              flushing.countDown();
              return producer.flush();
            });
    assertThat(flushing.await(5, TimeUnit.SECONDS)).isTrue();
    Thread.sleep(50);
    assertThat(flushed).isNotDone();

    fake.ack(0L);
    assertThat(get(flushed)).isEqualTo(0L);
  }

  @Test
  void streamErrorFailsPendingFuturesFast() throws Exception {
    Producer producer = client.newProducer();
    fake.take(); // Start
    fake.grantWindow(10_000);

    CompletableFuture<SendResult> pending = producer.send(newBody("a"));
    CompletableFuture<Long> acked = producer.waitAcknowledged(0L);

    fake.error(ErrorKind.ERROR_KIND_BAD_REQUEST, "nope");

    assertThatThrownBy(() -> get(pending))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOf(IntegrationClientException.class)
        .extracting(t -> ((IntegrationClientException) t).getKind())
        .isEqualTo(IntegrationClientException.Kind.BAD_REQUEST);
    assertThatThrownBy(() -> get(acked)).isInstanceOf(ExecutionException.class);

    // Subsequent sends fail fast.
    assertThatThrownBy(() -> producer.send(newBody("b"))).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void streamErrorFailsBufferedAcknowledgementsAndBackpressureWaiters() throws Exception {
    Producer producer =
        client.newProducer(
            ProducerOptions.builder().bufferMemory(128).maxBlockTime(Duration.ZERO).build());
    fake.take(); // Start

    SendAttempt.Accepted accepted =
        (SendAttempt.Accepted) producer.trySend(newBody("a".repeat(80)));
    SendAttempt.Backpressured backpressured =
        (SendAttempt.Backpressured) producer.trySend(newBody("b".repeat(80)));

    fake.error(ErrorKind.ERROR_KIND_BAD_REQUEST, "nope");

    assertThatThrownBy(() -> get(accepted.acknowledgement()))
        .isInstanceOf(ExecutionException.class);
    assertThatThrownBy(() -> get(backpressured.ready())).isInstanceOf(ExecutionException.class);
  }

  @Test
  void exactlyOnceRejectsNonIncreasingOffsets() throws Exception {
    ExactlyOnceProducer producer = client.newExactlyOnceProducer("p1");
    fake.take(); // Start
    fake.grantWindow(10_000);

    producer.send(5L, newBody("a"));
    assertThat(producer.lastSentOffset()).isEqualTo(5L);

    assertThatThrownBy(() -> producer.send(5L, newBody("b")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> producer.send(3L, newBody("c")))
        .isInstanceOf(IllegalArgumentException.class);

    producer.send(6L, newBody("d"));
    assertThat(producer.lastSentOffset()).isEqualTo(6L);
  }

  @Test
  void exactlyOnceTrySendDoesNotConsumeBackpressuredOffset() throws Exception {
    ExactlyOnceProducer producer =
        client.newExactlyOnceProducer(
            "p1", ProducerOptions.builder().bufferMemory(128).maxBlockTime(Duration.ZERO).build());
    fake.take(); // Start

    assertThat(producer.trySend(5L, newBody("a".repeat(80))))
        .isInstanceOf(SendAttempt.Accepted.class);
    SendAttempt rejected = producer.trySend(6L, newBody("b".repeat(80)));
    assertThat(rejected).isInstanceOf(SendAttempt.Backpressured.class);
    assertThat(producer.lastSentOffset()).isEqualTo(5L);

    fake.grantWindow(10_000);
    get(((SendAttempt.Backpressured) rejected).ready());

    assertThat(producer.trySend(6L, newBody("b".repeat(80))))
        .isInstanceOf(SendAttempt.Accepted.class);
    assertThat(producer.lastSentOffset()).isEqualTo(6L);
  }

  @Test
  void invocationFieldsMapToProto() throws Exception {
    Producer producer = client.newProducer();
    fake.take(); // Start
    fake.grantWindow(10_000);

    Invocation inv =
        Invocation.create()
            .setServiceName("Svc")
            .setHandlerName("handle")
            .setKey("k")
            .setIdempotencyKey("idem")
            .putHeader("h1", "v1")
            .setTraceparent("tp")
            .setBody("hello".getBytes(StandardCharsets.UTF_8));
    producer.send(inv);

    IngestionInvocation sent = fake.take().getInvocation();
    assertThat(sent.getService()).isEqualTo("Svc");
    assertThat(sent.getHandler()).isEqualTo("handle");
    assertThat(sent.getKey()).isEqualTo("k");
    assertThat(sent.getIdempotencyKey()).isEqualTo("idem");
    assertThat(sent.getAdditionalHeadersMap()).containsEntry("h1", "v1");
    assertThat(sent.getTraceparent()).isEqualTo("tp");
    assertThat(sent.getPayload().toStringUtf8()).isEqualTo("hello");
  }

  // ---- helpers ----

  private static Invocation newBody(String body) {
    return Invocation.create().setBody(body.getBytes(StandardCharsets.UTF_8));
  }

  private static <T> T get(CompletableFuture<T> f)
      throws InterruptedException, ExecutionException, TimeoutException {
    return f.get(5, TimeUnit.SECONDS);
  }

  /** Fake service capturing requests and scripting responses. */
  private static final class FakeIngestionService extends IngestionSvcGrpc.IngestionSvcImplBase {

    private final BlockingQueue<IngestionRequest> received = new LinkedBlockingQueue<>();
    private volatile StreamObserver<IngestionResponse> responses;

    @Override
    public StreamObserver<IngestionRequest> ingest(
        StreamObserver<IngestionResponse> responseObserver) {
      this.responses = responseObserver;
      return new StreamObserver<>() {
        @Override
        public void onNext(IngestionRequest value) {
          received.add(value);
        }

        @Override
        public void onError(Throwable t) {}

        @Override
        public void onCompleted() {}
      };
    }

    IngestionRequest take() throws InterruptedException {
      IngestionRequest req = received.poll(5, TimeUnit.SECONDS);
      if (req == null) {
        throw new AssertionError("timed out waiting for a request frame");
      }
      return req;
    }

    void assertNoRequest() {
      assertThat(received.poll()).isNull();
    }

    void grantWindow(long bytes) {
      responses.onNext(
          IngestionResponse.newBuilder()
              .setWindowUpdate(WindowUpdate.newBuilder().setIncrementBytes((int) bytes))
              .build());
    }

    void ack(long lastCommitted) {
      responses.onNext(IngestionResponse.newBuilder().setLastCommitted(lastCommitted).build());
    }

    void error(ErrorKind kind, String message) {
      responses.onNext(
          IngestionResponse.newBuilder()
              .setError(
                  dev.restate.ingestion.v1.Error.newBuilder().setKind(kind).setMessage(message))
              .build());
      responses.onCompleted();
    }

    void error(ErrorKind kind, String message, long lastCommitted) {
      responses.onNext(
          IngestionResponse.newBuilder()
              .setLastCommitted(lastCommitted)
              .setError(
                  dev.restate.ingestion.v1.Error.newBuilder().setKind(kind).setMessage(message))
              .build());
      responses.onCompleted();
    }
  }
}
