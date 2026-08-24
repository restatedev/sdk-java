// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import dev.restate.ingestion.v1.IngestionSvcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/** {@link IntegrationClient} backed by a single gRPC {@link ManagedChannel} shared by producers. */
final class IntegrationClientImpl implements IntegrationClient {

  private final ManagedChannel channel;
  private final IngestionSvcGrpc.IngestionSvcStub stub;
  private final String integration;

  private IntegrationClientImpl(
      ManagedChannel channel, IngestionSvcGrpc.IngestionSvcStub stub, String integration) {
    this.channel = channel;
    this.stub = stub;
    this.integration = integration;
  }

  static IntegrationClient create(String target, @Nullable String authToken, String integration) {
    IngressEndpoint endpoint = IngressEndpoint.parse(target);
    ManagedChannelBuilder<?> builder =
        ManagedChannelBuilder.forAddress(endpoint.host, endpoint.port);
    if (endpoint.tls) {
      builder.useTransportSecurity();
    } else {
      builder.usePlaintext();
    }
    ManagedChannel channel = builder.build();

    IngestionSvcGrpc.IngestionSvcStub stub = IngestionSvcGrpc.newStub(channel);
    if (authToken != null && !authToken.isBlank()) {
      stub = stub.withInterceptors(new AuthInterceptor(authToken));
    }
    return new IntegrationClientImpl(channel, stub, integration);
  }

  /** Visible for testing: build a client over an already-created channel (e.g. gRPC in-process). */
  static IntegrationClient forChannel(ManagedChannel channel, String integration) {
    return new IntegrationClientImpl(channel, IngestionSvcGrpc.newStub(channel), integration);
  }

  @Override
  public Producer newProducer(ProducerOptions options) {
    return new ProducerImpl(stub, Objects.requireNonNull(options, "options"), integration);
  }

  @Override
  public ExactlyOnceProducer newExactlyOnceProducer(String producerId, ProducerOptions options) {
    if (producerId == null || producerId.isBlank()) {
      throw new IllegalArgumentException(
          "producerId must be non-empty for an exactly-once producer");
    }
    return new ExactlyOnceProducerImpl(
        stub, producerId, Objects.requireNonNull(options, "options"), integration);
  }

  @Override
  public void close() {
    channel.shutdown();
    try {
      if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
        channel.shutdownNow();
      }
    } catch (InterruptedException e) {
      channel.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
