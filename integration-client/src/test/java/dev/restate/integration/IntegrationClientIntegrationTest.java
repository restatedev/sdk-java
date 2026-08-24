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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@RestateTest(containerImage = "ghcr.io/restatedev/restate:pr5026")
@Timeout(value = 30)
class IntegrationClientIntegrationTest {

  private static final String SERVICE = "IntegrationClientCounter";
  private static final byte[] ONE = "1".getBytes(StandardCharsets.UTF_8);

  @BindService private final IntegrationClientCounter counter = new IntegrationClientCounterImpl();

  @Test
  void zeroBufferProducerDeliversAndAcknowledges(
      @RestateURL String ingressUrl, @RestateClient Client ingressClient) throws Exception {
    String key = UUID.randomUUID().toString();

    try (IntegrationClient client = IntegrationClient.builder(ingressUrl).build();
        Producer producer =
            client.newProducer(
                ProducerOptions.builder()
                    .bufferMemory(0)
                    .maxBlockTime(Duration.ofSeconds(10))
                    .defaultMetadata(counterMetadata(key))
                    .build())) {
      SendResult result = producer.send(Invocation.create().setBody(ONE)).get(10, TimeUnit.SECONDS);

      assertThat(result.offset()).isZero();
      assertThat(producer.lastAcknowledgedOffset()).isZero();
    }

    assertThat(ingressClient.virtualObject(IntegrationClientCounter.class, key).get())
        .isEqualTo(1L);
  }

  @Test
  void exactlyOnceProducerDeduplicatesAcrossStreams(
      @RestateURL String ingressUrl, @RestateClient Client ingressClient) throws Exception {
    String key = UUID.randomUUID().toString();
    String producerId = "integration-test/" + UUID.randomUUID();
    ProducerOptions options =
        ProducerOptions.builder()
            .bufferMemory(0)
            .maxBlockTime(Duration.ofSeconds(10))
            .defaultMetadata(counterMetadata(key))
            .build();

    sendExactlyOnce(ingressUrl, producerId, options);
    sendExactlyOnce(ingressUrl, producerId, options);

    assertThat(ingressClient.virtualObject(IntegrationClientCounter.class, key).get())
        .isEqualTo(1L);
  }

  private static void sendExactlyOnce(String ingressUrl, String producerId, ProducerOptions options)
      throws Exception {
    try (IntegrationClient client = IntegrationClient.builder(ingressUrl).build();
        ExactlyOnceProducer producer = client.newExactlyOnceProducer(producerId, options)) {
      SendResult result =
          producer.send(0, Invocation.create().setBody(ONE)).get(10, TimeUnit.SECONDS);
      assertThat(result.offset()).isZero();
    }
  }

  private static InvocationMetadata counterMetadata(String key) {
    return InvocationMetadata.create()
        .setServiceName(SERVICE)
        .setHandlerName("add")
        .setKey(key)
        .putHeader("content-type", "application/json");
  }

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
