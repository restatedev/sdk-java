// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Retry policy used by Restate when retrying failed handler invocations.
 *
 * <p>This policy controls an exponential backoff with optional capping and a terminal action:
 *
 * <ul>
 *   <li>{@code initialInterval}: delay before the first retry attempt.
 *   <li>{@code exponentiationFactor}: multiplier applied to the previous delay to compute the next
 *       delay.
 *   <li>{@code maxInterval}: upper bound for any computed delay.
 *   <li>{@code maxAttempts}: maximum number of attempts (initial call counts as the first attempt).
 *   <li>{@code onMaxAttempts}: what to do when {@code maxAttempts} is reached.
 * </ul>
 *
 * <p>Unset fields inherit the corresponding defaults from the Restate server configuration.
 * Implementations may also enforce system-wide minimum/maximum bounds.
 */
public final class InvocationRetryPolicy {

  private final @Nullable Duration initialInterval;
  private final @Nullable Double exponentiationFactor;
  private final @Nullable Duration maxInterval;
  private final @Nullable Integer maxAttempts;
  private final @Nullable OnMaxAttempts onMaxAttempts;

  private InvocationRetryPolicy(
      @Nullable Duration initialInterval,
      @Nullable Double exponentiationFactor,
      @Nullable Duration maxInterval,
      @Nullable Integer maxAttempts,
      @Nullable OnMaxAttempts onMaxAttempts) {
    this.initialInterval = initialInterval;
    this.exponentiationFactor = exponentiationFactor;
    this.maxInterval = maxInterval;
    this.maxAttempts = maxAttempts;
    this.onMaxAttempts = onMaxAttempts;
  }

  /**
   * Initial delay before the first retry attempt.
   *
   * <p>If unset, the server default is used.
   */
  public @Nullable Duration initialInterval() {
    return initialInterval;
  }

  /**
   * Exponential backoff multiplier used to compute the next retry delay.
   *
   * <p>For attempt {@code n}, the next delay is roughly {@code previousDelay *
   * exponentiationFactor}, capped by {@link #maxInterval()} if set.
   */
  public @Nullable Double exponentiationFactor() {
    return exponentiationFactor;
  }

  /**
   * Upper bound for the computed retry delay.
   *
   * <p>If set, any computed delay will not exceed this value.
   */
  public @Nullable Duration maxInterval() {
    return maxInterval;
  }

  /**
   * Maximum number of attempts before giving up retrying.
   *
   * <p>The initial call counts as the first attempt; retries increment the count by 1. When giving
   * up, the behavior defined with {@link #onMaxAttempts()} will be applied.
   *
   * @see OnMaxAttempts
   */
  public @Nullable Integer maxAttempts() {
    return maxAttempts;
  }

  /**
   * Behavior when the configured {@link #maxAttempts()} is reached.
   *
   * @see OnMaxAttempts
   */
  public @Nullable OnMaxAttempts onMaxAttempts() {
    return onMaxAttempts;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof InvocationRetryPolicy that)) return false;
    return Objects.equals(initialInterval, that.initialInterval)
        && Objects.equals(exponentiationFactor, that.exponentiationFactor)
        && Objects.equals(maxInterval, that.maxInterval)
        && Objects.equals(maxAttempts, that.maxAttempts)
        && onMaxAttempts == that.onMaxAttempts;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        initialInterval, exponentiationFactor, maxInterval, maxAttempts, onMaxAttempts);
  }

  @Override
  public String toString() {
    return "InvocationRetryPolicy{"
        + "initialInterval="
        + initialInterval
        + ", exponentiationFactor="
        + exponentiationFactor
        + ", maxInterval="
        + maxInterval
        + ", maxAttempts="
        + maxAttempts
        + ", onMaxAttempts="
        + onMaxAttempts
        + '}';
  }

  /** Behavior when retry policy reaches {@link #maxAttempts()} attempts. */
  public enum OnMaxAttempts {
    /**
     * Pause the invocation once retries are exhausted. The invocation enters the paused state and
     * can be manually resumed from the CLI or UI.
     */
    PAUSE,
    /**
     * Kill the invocation once retries are exhausted. The invocation will be marked as failed and
     * will not be retried unless explicitly re-triggered by the caller.
     */
    KILL
  }

  /**
   * @return a new {@link Builder} to configure an {@link InvocationRetryPolicy}.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private @Nullable Duration initialInterval;
    private @Nullable Double exponentiationFactor;
    private @Nullable Duration maxInterval;
    private @Nullable Integer maxAttempts;
    private @Nullable OnMaxAttempts onMaxAttempts;

    /** Initial delay before the first retry attempt. */
    public @Nullable Duration initialInterval() {
      return initialInterval;
    }

    /** Sets the initial delay before the first retry attempt. */
    public Builder initialInterval(@Nullable Duration initialInterval) {
      this.initialInterval = initialInterval;
      return this;
    }

    /** Exponential backoff multiplier used to compute the next retry delay. */
    public @Nullable Double exponentiationFactor() {
      return exponentiationFactor;
    }

    /** Sets the exponential backoff multiplier used to compute the next retry delay. */
    public Builder exponentiationFactor(@Nullable Double exponentiationFactor) {
      this.exponentiationFactor = exponentiationFactor;
      return this;
    }

    /** Upper bound for the computed retry delay. */
    public @Nullable Duration maxInterval() {
      return maxInterval;
    }

    /** Sets the upper bound for the computed retry delay. */
    public Builder maxInterval(@Nullable Duration maxInterval) {
      this.maxInterval = maxInterval;
      return this;
    }

    /**
     * Maximum number of attempts before giving up retrying.
     *
     * <p>The initial call counts as the first attempt; retries increment the count by 1. When
     * giving up, the behavior defined with {@link #onMaxAttempts()} will be applied.
     *
     * @see OnMaxAttempts
     */
    public @Nullable Integer maxAttempts() {
      return maxAttempts;
    }

    /**
     * Sets the maximum number of attempts before giving up retrying.
     *
     * <p>The initial call counts as the first attempt; retries increment the count by 1.
     */
    public Builder maxAttempts(@Nullable Integer maxAttempts) {
      this.maxAttempts = maxAttempts;
      return this;
    }

    /**
     * Behavior when the configured {@link #maxAttempts()} is reached.
     *
     * @see OnMaxAttempts
     */
    public @Nullable OnMaxAttempts onMaxAttempts() {
      return onMaxAttempts;
    }

    /** Sets the behavior when the configured {@link #maxAttempts()} is reached. */
    public Builder onMaxAttempts(@Nullable OnMaxAttempts onMaxAttempts) {
      this.onMaxAttempts = onMaxAttempts;
      return this;
    }

    public InvocationRetryPolicy build() {
      return new InvocationRetryPolicy(
          initialInterval, exponentiationFactor, maxInterval, maxAttempts, onMaxAttempts);
    }
  }
}
