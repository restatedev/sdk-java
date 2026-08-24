// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import org.jspecify.annotations.Nullable;

/** Entry point for producing invocations to Restate ingress over the ingestion API. */
@org.jetbrains.annotations.ApiStatus.Experimental
public interface IntegrationClient extends AutoCloseable {

  /**
   * Creates at-least-once {@link Producer} with no stream defaults.
   *
   * @return a new producer
   */
  default Producer newProducer() {
    return newProducer(ProducerOptions.DEFAULTS);
  }

  /**
   * Creates at-least-once {@link Producer} with the given stream defaults.
   *
   * @param defaultMetadata invocation fields applied to every record unless overridden per record
   * @return a new producer
   * @throws NullPointerException if {@code defaultMetadata} is {@code null}
   */
  default Producer newProducer(InvocationMetadata defaultMetadata) {
    return newProducer(ProducerOptions.builder().defaultMetadata(defaultMetadata).build());
  }

  /**
   * Creates an at-least-once {@link Producer} with the given options.
   *
   * @param options producer buffering, blocking, and invocation-default options
   * @return a new producer
   * @throws NullPointerException if {@code options} is {@code null}
   */
  Producer newProducer(ProducerOptions options);

  /**
   * Creates an {@link ExactlyOnceProducer} identified by {@code producerId}.
   *
   * @param producerId stable identity of the producer; must be non-empty
   * @return a new exactly-once producer
   * @throws IllegalArgumentException if {@code producerId} is {@code null} or blank
   */
  default ExactlyOnceProducer newExactlyOnceProducer(String producerId) {
    return newExactlyOnceProducer(producerId, ProducerOptions.DEFAULTS);
  }

  /**
   * Creates an {@link ExactlyOnceProducer} identified by {@code producerId} with the given stream
   * defaults.
   *
   * @param producerId stable identity of the producer; must be non-empty
   * @param defaultMetadata invocation fields applied to every record unless overridden per record
   * @return a new exactly-once producer
   * @throws IllegalArgumentException if {@code producerId} is {@code null} or blank
   * @throws NullPointerException if {@code defaultMetadata} is {@code null}
   */
  default ExactlyOnceProducer newExactlyOnceProducer(
      String producerId, InvocationMetadata defaultMetadata) {
    return newExactlyOnceProducer(
        producerId, ProducerOptions.builder().defaultMetadata(defaultMetadata).build());
  }

  /**
   * Creates an {@link ExactlyOnceProducer} identified by {@code producerId} with the given options.
   *
   * @param producerId stable identity of the producer; must be non-empty
   * @param options producer buffering, blocking, and invocation-default options
   * @return a new exactly-once producer
   * @throws IllegalArgumentException if {@code producerId} is {@code null} or blank
   * @throws NullPointerException if {@code options} is {@code null}
   */
  ExactlyOnceProducer newExactlyOnceProducer(String producerId, ProducerOptions options);

  /**
   * Shuts down the underlying client. Flush and close any producers first when their accepted
   * invocations must be durably committed.
   */
  @Override
  void close();

  /** Start building a client that connects to the ingress at {@code target} (an http(s) URL). */
  static Builder builder(String ingressUrl) {
    return new Builder(ingressUrl);
  }

  /** Builder for {@link IntegrationClient}. */
  final class Builder {
    private final String target;
    private @Nullable String authToken;
    private String integration = Version.INTEGRATION;

    private Builder(String target) {
      this.target = target;
    }

    /** Bearer token sent as the {@code Authorization} header on the ingestion stream. */
    public Builder authToken(String authToken) {
      this.authToken = authToken;
      return this;
    }

    /**
     * Identify the integration in the ingestion {@code Start} frame as {@code name/version}. When
     * not set, defaults to this client's own identity ({@link Version#INTEGRATION}).
     */
    public Builder integration(String name, String version) {
      this.integration = name + "/" + version;
      return this;
    }

    public IntegrationClient build() {
      return IntegrationClientImpl.create(target, authToken, integration);
    }
  }
}
