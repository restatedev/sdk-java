// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Configuration properties for Restate's retry policy when retrying failed handler invocations.
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
 *
 * @see dev.restate.sdk.endpoint.definition.InvocationRetryPolicy
 */
public class RetryPolicyProperties {

  /** Behavior when retry policy reaches {@link #getMaxAttempts()} attempts. */
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

  @Nullable private Duration initialInterval;
  @Nullable private Double exponentiationFactor;
  @Nullable private Duration maxInterval;
  @Nullable private Integer maxAttempts;
  @Nullable private OnMaxAttempts onMaxAttempts;

  public RetryPolicyProperties() {}

  public RetryPolicyProperties(
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
  public @Nullable Duration getInitialInterval() {
    return initialInterval;
  }

  /**
   * Initial delay before the first retry attempt.
   *
   * <p>If unset, the server default is used.
   */
  public void setInitialInterval(@Nullable Duration initialInterval) {
    this.initialInterval = initialInterval;
  }

  /**
   * Exponential backoff multiplier used to compute the next retry delay.
   *
   * <p>For attempt {@code n}, the next delay is roughly {@code previousDelay *
   * exponentiationFactor}, capped by {@link #getMaxInterval()} if set.
   */
  public @Nullable Double getExponentiationFactor() {
    return exponentiationFactor;
  }

  /**
   * Exponential backoff multiplier used to compute the next retry delay.
   *
   * <p>For attempt {@code n}, the next delay is roughly {@code previousDelay *
   * exponentiationFactor}, capped by {@link #getMaxInterval()} if set.
   */
  public void setExponentiationFactor(@Nullable Double exponentiationFactor) {
    this.exponentiationFactor = exponentiationFactor;
  }

  /**
   * Upper bound for the computed retry delay.
   *
   * <p>If set, any computed delay will not exceed this value.
   */
  public @Nullable Duration getMaxInterval() {
    return maxInterval;
  }

  /**
   * Upper bound for the computed retry delay.
   *
   * <p>If set, any computed delay will not exceed this value.
   */
  public void setMaxInterval(@Nullable Duration maxInterval) {
    this.maxInterval = maxInterval;
  }

  /**
   * Maximum number of attempts before giving up retrying.
   *
   * <p>The initial call counts as the first attempt; retries increment the count by 1. When giving
   * up, the behavior defined with {@link #getOnMaxAttempts()} will be applied.
   *
   * @see OnMaxAttempts
   */
  public @Nullable Integer getMaxAttempts() {
    return maxAttempts;
  }

  /**
   * Maximum number of attempts before giving up retrying.
   *
   * <p>The initial call counts as the first attempt; retries increment the count by 1. When giving
   * up, the behavior defined with {@link #getOnMaxAttempts()} will be applied.
   *
   * @see OnMaxAttempts
   */
  public void setMaxAttempts(@Nullable Integer maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  /**
   * Behavior when the configured {@link #getMaxAttempts()} is reached.
   *
   * @see OnMaxAttempts
   */
  public @Nullable OnMaxAttempts getOnMaxAttempts() {
    return onMaxAttempts;
  }

  /**
   * Behavior when the configured {@link #getMaxAttempts()} is reached.
   *
   * @see OnMaxAttempts
   */
  public void setOnMaxAttempts(@Nullable OnMaxAttempts onMaxAttempts) {
    this.onMaxAttempts = onMaxAttempts;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RetryPolicyProperties that)) return false;
    return Objects.equals(getInitialInterval(), that.getInitialInterval())
        && Objects.equals(getExponentiationFactor(), that.getExponentiationFactor())
        && Objects.equals(getMaxInterval(), that.getMaxInterval())
        && Objects.equals(getMaxAttempts(), that.getMaxAttempts())
        && getOnMaxAttempts() == that.getOnMaxAttempts();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getInitialInterval(),
        getExponentiationFactor(),
        getMaxInterval(),
        getMaxAttempts(),
        getOnMaxAttempts());
  }

  @Override
  public String toString() {
    return "RetryPolicyProperties{"
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
}
