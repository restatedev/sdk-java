// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import dev.restate.ingestion.v1.IngestionDefaults;
import java.time.Duration;
import java.util.Objects;

/** Configuration shared by at-least-once and exactly-once producers. */
@org.jetbrains.annotations.ApiStatus.Experimental
public final class ProducerOptions {

  /** Kafka-compatible default producer buffer size: 32 MiB. */
  public static final long DEFAULT_BUFFER_MEMORY = 32L * 1024 * 1024;

  /** Kafka-compatible default maximum admission wait: one minute. */
  public static final Duration DEFAULT_MAX_BLOCK_TIME = Duration.ofMinutes(1);

  static final ProducerOptions DEFAULTS = builder().build();

  private final long bufferMemory;
  private final Duration maxBlockTime;
  private final IngestionDefaults defaultMetadata;

  private ProducerOptions(Builder builder) {
    this.bufferMemory = builder.bufferMemory;
    this.maxBlockTime = builder.maxBlockTime;
    this.defaultMetadata =
        builder.defaultMetadata == null
            ? IngestionDefaults.getDefaultInstance()
            : ((InvocationMetadataImpl) builder.defaultMetadata).toDefaults();
  }

  /**
   * Starts building producer options.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Maximum serialized bytes retained while invocations wait to be handed to the transport.
   *
   * @return the local buffer limit in bytes
   */
  public long bufferMemory() {
    return bufferMemory;
  }

  /**
   * Maximum time {@code send} waits for buffer capacity before refusing an invocation.
   *
   * @return the maximum admission wait
   */
  public Duration maxBlockTime() {
    return maxBlockTime;
  }

  IngestionDefaults toDefaults() {
    return defaultMetadata;
  }

  /** Builder for {@link ProducerOptions}. */
  public static final class Builder {
    private long bufferMemory = DEFAULT_BUFFER_MEMORY;
    private Duration maxBlockTime = DEFAULT_MAX_BLOCK_TIME;
    private InvocationMetadata defaultMetadata;

    private Builder() {}

    /**
     * Sets the maximum serialized bytes retained while invocations wait to be handed to the
     * transport.
     *
     * @param bytes a positive byte count
     */
    public Builder bufferMemory(long bytes) {
      if (bytes <= 0) {
        throw new IllegalArgumentException("bufferMemory must be greater than zero");
      }
      this.bufferMemory = bytes;
      return this;
    }

    /**
     * Sets how long {@code send} waits for buffer capacity. {@link Duration#ZERO} makes {@code
     * send} fail immediately when the buffer is full.
     */
    public Builder maxBlockTime(Duration duration) {
      Objects.requireNonNull(duration, "maxBlockTime");
      if (duration.isNegative()) {
        throw new IllegalArgumentException("maxBlockTime must not be negative");
      }
      this.maxBlockTime = duration;
      return this;
    }

    /**
     * Sets invocation fields applied to every record unless overridden by that invocation. The
     * metadata is snapshotted when {@link #build()} is called.
     */
    public Builder defaultMetadata(InvocationMetadata metadata) {
      Objects.requireNonNull(metadata, "defaultMetadata");
      this.defaultMetadata = metadata;
      return this;
    }

    public ProducerOptions build() {
      return new ProducerOptions(this);
    }
  }
}
