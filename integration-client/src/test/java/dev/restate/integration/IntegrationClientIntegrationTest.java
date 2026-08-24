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

import dev.restate.client.Client;
import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Name;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.testing.BindService;
import dev.restate.sdk.testing.RestateClient;
import dev.restate.sdk.testing.RestateTest;
import dev.restate.sdk.testing.RestateURL;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@RestateTest(containerImage = "ghcr.io/restatedev/restate:pr5026")
@Timeout(value = 30)
class IntegrationClientIntegrationTest {

  private static final String SERVICE = "IntegrationClientCounter";

  @BindService private final IntegrationClientCounter counter = new IntegrationClientCounterImpl();

  @BufferMemoryTest
  void producerDeliversAndAcknowledges(
      long bufferMemory, @RestateURL String ingressUrl, @RestateClient Client ingressClient)
      throws Exception {
    String key = UUID.randomUUID().toString();

    try (IntegrationClient client = IntegrationClient.builder(ingressUrl).build();
        Producer producer = client.newProducer(producerOptions(key, bufferMemory))) {
      SendResult result = producer.send(invocation(1)).get(10, TimeUnit.SECONDS);

      assertThat(result.offset()).isZero();
      assertThat(producer.lastAcknowledgedOffset()).isZero();
    }

    assertThat(ingressClient.virtualObject(IntegrationClientCounter.class, key).get())
        .isEqualTo(1L);
  }

  @BufferMemoryTest
  void exactlyOnceProducerDeduplicatesAcrossStreams(
      long bufferMemory, @RestateURL String ingressUrl, @RestateClient Client ingressClient) {
    String key = UUID.randomUUID().toString();
    String producerId = "integration-test/" + UUID.randomUUID();
    ProducerOptions options = producerOptions(key, bufferMemory);

    sendExactlyOnce(ingressUrl, producerId, options, record(0, 1));
    sendExactlyOnce(ingressUrl, producerId, options, record(0, 100));

    assertThat(ingressClient.virtualObject(IntegrationClientCounter.class, key).get())
        .isEqualTo(1L);
  }

  @BufferMemoryTest
  void exactlyOnceProducerReplaysCommittedPrefixAndAcceptsNewOffsets(
      long bufferMemory, @RestateURL String ingressUrl, @RestateClient Client ingressClient) {
    String key = UUID.randomUUID().toString();
    String producerId = "integration-test/" + UUID.randomUUID();
    ProducerOptions options = producerOptions(key, bufferMemory);

    sendExactlyOnce(ingressUrl, producerId, options, record(0, 1), record(1, 10));
    sendExactlyOnce(
        ingressUrl, producerId, options, record(0, 100), record(1, 1_000), record(2, 10_000));

    assertThat(ingressClient.virtualObject(IntegrationClientCounter.class, key).get())
        .isEqualTo(10_011L);
  }

  @BufferMemoryTest
  void exactlyOnceProducerDropsOffsetsBelowCommittedWatermark(
      long bufferMemory, @RestateURL String ingressUrl, @RestateClient Client ingressClient) {
    String key = UUID.randomUUID().toString();
    String producerId = "integration-test/" + UUID.randomUUID();
    ProducerOptions options = producerOptions(key, bufferMemory);

    sendExactlyOnce(ingressUrl, producerId, options, record(10, 1));
    sendExactlyOnce(
        ingressUrl, producerId, options, record(0, 100), record(9, 1_000), record(11, 10));

    assertThat(ingressClient.virtualObject(IntegrationClientCounter.class, key).get())
        .isEqualTo(11L);
  }

  @BufferMemoryTest
  void exactlyOnceDeduplicationIsScopedByProducerId(
      long bufferMemory, @RestateURL String ingressUrl, @RestateClient Client ingressClient) {
    String key = UUID.randomUUID().toString();
    ProducerOptions options = producerOptions(key, bufferMemory);

    sendExactlyOnce(ingressUrl, "integration-test/" + UUID.randomUUID(), options, record(0, 1));
    sendExactlyOnce(ingressUrl, "integration-test/" + UUID.randomUUID(), options, record(0, 10));

    assertThat(ingressClient.virtualObject(IntegrationClientCounter.class, key).get())
        .isEqualTo(11L);
  }

  @BufferMemoryTest
  void atLeastOnceProducerDoesNotDeduplicateAcrossStreams(
      long bufferMemory, @RestateURL String ingressUrl, @RestateClient Client ingressClient)
      throws Exception {
    String key = UUID.randomUUID().toString();
    ProducerOptions options = producerOptions(key, bufferMemory);

    sendAtLeastOnce(ingressUrl, options, 1);
    sendAtLeastOnce(ingressUrl, options, 10);

    assertThat(ingressClient.virtualObject(IntegrationClientCounter.class, key).get())
        .isEqualTo(11L);
  }

  private static void sendExactlyOnce(
      String ingressUrl, String producerId, ProducerOptions options, TestRecord... records) {
    try (IntegrationClient client = IntegrationClient.builder(ingressUrl).build();
        ExactlyOnceProducer producer = client.newExactlyOnceProducer(producerId, options)) {
      for (TestRecord record : records) {
        producer.send(record.offset(), invocation(record.value()));
      }

      long lastOffset = records[records.length - 1].offset();
      assertThat(producer.flush()).isEqualTo(lastOffset);
      assertThat(producer.lastAcknowledgedOffset()).isEqualTo(lastOffset);
    }
  }

  private static void sendAtLeastOnce(String ingressUrl, ProducerOptions options, long value)
      throws Exception {
    try (IntegrationClient client = IntegrationClient.builder(ingressUrl).build();
        Producer producer = client.newProducer(options)) {
      SendResult result = producer.send(invocation(value)).get(10, TimeUnit.SECONDS);
      assertThat(result.offset()).isZero();
    }
  }

  private static TestRecord record(long offset, long value) {
    return new TestRecord(offset, value);
  }

  private static Invocation invocation(long value) {
    return Invocation.create().setBody(Long.toString(value).getBytes(StandardCharsets.UTF_8));
  }

  private static ProducerOptions producerOptions(String key, long bufferMemory) {
    return ProducerOptions.builder()
        .bufferMemory(bufferMemory)
        .maxBlockTime(Duration.ofSeconds(10))
        .defaultMetadata(counterMetadata(key))
        .build();
  }

  private static InvocationMetadata counterMetadata(String key) {
    return InvocationMetadata.create()
        .setServiceName(SERVICE)
        .setHandlerName("add")
        .setKey(key)
        .putHeader("content-type", "application/json");
  }

  private record TestRecord(long offset, long value) {}

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @ParameterizedTest(name = "{displayName}: bufferMemory={0}")
  @ValueSource(longs = {0L, ProducerOptions.DEFAULT_BUFFER_MEMORY})
  private @interface BufferMemoryTest {}

  @VirtualObject
  @Name(SERVICE)
  public interface IntegrationClientCounter {

    @Handler
    void add(long value);

    @Handler
    long get();
  }

  public static final class IntegrationClientCounterImpl implements IntegrationClientCounter {

    private static final StateKey<Long> COUNT = StateKey.of("count", Long.class);

    @Override
    public void add(long value) {
      Restate.State state = Restate.state();
      state.set(COUNT, state.get(COUNT).orElse(0L) + value);
    }

    @Override
    public long get() {
      return Restate.state().get(COUNT).orElse(0L);
    }
  }
}
