// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Retry policy configuration. */
public final class RetryPolicy {

  private Duration initialDelay;
  private float exponentiationFactor;
  private @Nullable Duration maxDelay;
  private @Nullable Integer maxAttempts;
  private @Nullable Duration maxDuration;

  RetryPolicy(Duration initialDelay, float exponentiationFactor) {
    this.initialDelay = initialDelay;
    this.exponentiationFactor = exponentiationFactor;
  }

  /** Initial retry delay for the first retry attempt. */
  public RetryPolicy setInitialDelay(Duration initialDelay) {
    this.initialDelay = initialDelay;
    return this;
  }

  /** Exponentiation factor to use when computing the next retry delay. */
  public RetryPolicy setExponentiationFactor(float exponentiationFactor) {
    this.exponentiationFactor = exponentiationFactor;
    return this;
  }

  /** Maximum delay between retries. */
  public RetryPolicy setMaxDelay(@Nullable Duration maxDelay) {
    this.maxDelay = maxDelay;
    return this;
  }

  /**
   * Maximum number of attempts before giving up retrying.
   *
   * <p>The policy gives up retrying when either at least the given number of attempts is reached,
   * or the {@code maxDuration} specified with {@link #setMaxDuration(Duration)} (if set) is reached
   * first. If both {@link #getMaxAttempts()} and {@link #getMaxDuration()} are {@code null}, the
   * policy will retry indefinitely.
   *
   * <p><b>Note:</b> The number of actual retries may be higher than the provided value. This is due
   * to the nature of the {@code run} operation, which executes the closure on the service and sends
   * the result afterward to Restate.
   */
  public RetryPolicy setMaxAttempts(@Nullable Integer maxAttempts) {
    this.maxAttempts = maxAttempts;
    return this;
  }

  /**
   * Maximum duration of the retry loop.
   *
   * <p>The policy gives up retrying when either the retry loop lasted at least for this given max
   * duration, or the {@code maxAttempts} specified with {@link #setMaxAttempts(Integer)} (if set)
   * is reached first. If both {@link #getMaxAttempts()} and {@link #getMaxDuration()} are {@code
   * null}, the policy will retry indefinitely.
   *
   * <p><b>Note:</b> The real retry loop duration may be higher than the given duration. TThis is
   * due to the nature of the {@code run} operation, which executes the closure on the service and
   * sends the result afterward to Restate.
   */
  public RetryPolicy setMaxDuration(@Nullable Duration maxDuration) {
    this.maxDuration = maxDuration;
    return this;
  }

  /**
   * @see #setInitialDelay(Duration)
   */
  public Duration getInitialDelay() {
    return initialDelay;
  }

  /**
   * @see #setExponentiationFactor(float)
   */
  public float getExponentiationFactor() {
    return exponentiationFactor;
  }

  /**
   * @see #setMaxDelay(Duration)
   */
  public @Nullable Duration getMaxDelay() {
    return maxDelay;
  }

  /**
   * @see #setMaxAttempts(Integer)
   */
  public @Nullable Integer getMaxAttempts() {
    return maxAttempts;
  }

  /**
   * @see #setMaxDuration(Duration)
   */
  public @Nullable Duration getMaxDuration() {
    return maxDuration;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RetryPolicy that)) return false;
    return Float.compare(exponentiationFactor, that.exponentiationFactor) == 0
        && Objects.equals(initialDelay, that.initialDelay)
        && Objects.equals(maxDelay, that.maxDelay)
        && Objects.equals(maxAttempts, that.maxAttempts)
        && Objects.equals(maxDuration, that.maxDuration);
  }

  @Override
  public int hashCode() {
    return Objects.hash(initialDelay, exponentiationFactor, maxDelay, maxAttempts, maxDuration);
  }

  @Override
  public String toString() {
    return "RunRetryPolicy{"
        + "initialDelay="
        + initialDelay
        + ", factor="
        + exponentiationFactor
        + ", maxDelay="
        + maxDelay
        + ", maxAttempts="
        + maxAttempts
        + ", maxDuration="
        + maxDuration
        + '}';
  }

  /**
   * @return a default exponential bounded retry policy
   */
  public static RetryPolicy defaultPolicy() {
    return exponential(Duration.ofMillis(100), 2.0f)
        .setMaxDelay(Duration.ofSeconds(2))
        .setMaxDuration(Duration.ofSeconds(60));
  }

  /**
   * @return an unbounded retry policy, with the given initial delay and factor.
   * @see #setInitialDelay(Duration)
   * @see #setExponentiationFactor(float)
   */
  public static RetryPolicy exponential(Duration initialDelay, float factor) {
    return new RetryPolicy(initialDelay, factor);
  }
}
