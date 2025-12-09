// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.fake;

import dev.restate.sdk.Context;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.jackson.JacksonSerdeFactory;
import java.time.Duration;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Expectation configuration for {@code FakeContext}.
 *
 * <p>This record defines the expected behavior and configuration for a fake context used in
 * testing. It controls various aspects including:
 *
 * <ul>
 *   <li>Random seed used by {@link Context#random()}
 *   <li>Expectations for {@link Context#run}
 *   <li>Timers' completion conditions (for {@link Context#timer})
 * </ul>
 *
 * <p>By default, the {@link FakeContext} will execute all {@code ctx.run}.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public record ContextExpectations(
    long randomSeed,
    String invocationId,
    Map<String, String> requestHeaders,
    Map<String, RunExpectation> runExpectations,
    BiPredicate<Duration, String> completeTimerIf,
    SerdeFactory serdeFactory) {

  public ContextExpectations() {
    this(
        1,
        "inv_1aiqX0vFEFNH1Umgre58JiCLgHfTtztYK5",
        Map.of(),
        Map.of(),
        (i1, i2) -> false,
        JacksonSerdeFactory.DEFAULT);
  }

  public enum RunExpectation {
    /**
     * @see ContextExpectations#executeRun
     */
    PASS,
    /**
     * @see ContextExpectations#dontExecuteRun
     */
    DONT_EXECUTE,
    /**
     * @see ContextExpectations#dontRetryRun
     */
    DONT_RETRY,
  }

  /**
   * Set the random seed to be used by {@link Context#random()}.
   *
   * @param randomSeed the random seed to use
   */
  public ContextExpectations withRandomSeed(long randomSeed) {
    return new ContextExpectations(
        randomSeed,
        this.invocationId,
        this.requestHeaders,
        this.runExpectations,
        this.completeTimerIf,
        this.serdeFactory);
  }

  /**
   * Set the invocation id returned by {@code ctx.request().invocationId()}.
   *
   * @param invocationId the invocation ID to use
   */
  public ContextExpectations withInvocationId(String invocationId) {
    return new ContextExpectations(
        this.randomSeed,
        invocationId,
        this.requestHeaders,
        this.runExpectations,
        this.completeTimerIf,
        this.serdeFactory);
  }

  /**
   * Set the request headers returned by {@code ctx.request().headers()}.
   *
   * @param requestHeaders the request headers to use
   */
  public ContextExpectations withRequestHeaders(Map<String, String> requestHeaders) {
    return new ContextExpectations(
        this.randomSeed,
        this.invocationId,
        requestHeaders,
        this.runExpectations,
        this.completeTimerIf,
        this.serdeFactory);
  }

  /**
   * Specify that the run with the given name should be executed.
   *
   * <p>The mocked context will try to execute the run, and in case of a failure, the given
   * exception will <b>be thrown as is</b>.
   *
   * @param runName the name of the run that should be executed
   */
  public ContextExpectations executeRun(String runName) {
    return withRunExpectation(runName, RunExpectation.PASS);
  }

  /**
   * Specify that the run with the given name should not be retried.
   *
   * <p>The mocked context will try to execute the run, and in case of a failure, the given
   * exception will be converted to {@link dev.restate.sdk.common.TerminalException}.
   *
   * <p>This is useful when unit testing a saga, and you want to simulate the "catch" branch.
   *
   * @param runName the name of the run that should not be retried
   */
  public ContextExpectations dontRetryRun(String runName) {
    return withRunExpectation(runName, RunExpectation.DONT_RETRY);
  }

  /**
   * Specify that the run with the given name should not be executed.
   *
   * <p>The mocked context will not execute the run.
   *
   * <p>This is useful when testing a flow where you either want to wait a {@code ctx.run} to
   * complete, or another event (such as timers)
   *
   * @param runName the name of the run that should not be executed
   */
  public ContextExpectations dontExecuteRun(String runName) {
    return withRunExpectation(runName, RunExpectation.DONT_EXECUTE);
  }

  /** Specify that all timers immediately complete. */
  public ContextExpectations completeAllTimersImmediately() {
    return new ContextExpectations(
        this.randomSeed,
        this.invocationId,
        requestHeaders,
        this.runExpectations,
        (i1, i2) -> true,
        this.serdeFactory);
  }

  /** Specify that the timer with the given name complete as soon as they're created. */
  public ContextExpectations completeTimerNamed(String timerName) {
    return completeTimerIf((duration, name) -> timerName.equals(name));
  }

  /**
   * Specify that all timers with duration longer than the given value complete as soon as they're
   * created.
   */
  public ContextExpectations completeTimerLongerOrEqualThan(Duration duration) {
    return completeTimerIf((timerDuration, name) -> timerDuration.compareTo(duration) >= 0);
  }

  private ContextExpectations withRunExpectation(String runName, RunExpectation expectation) {
    return new ContextExpectations(
        this.randomSeed,
        this.invocationId,
        requestHeaders,
        Stream.concat(
                this.runExpectations.entrySet().stream(),
                Stream.of(Map.entry(runName, expectation)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
        this.completeTimerIf,
        this.serdeFactory);
  }

  private ContextExpectations completeTimerIf(BiPredicate<Duration, String> completeTimerIf) {
    return new ContextExpectations(
        this.randomSeed,
        this.invocationId,
        requestHeaders,
        this.runExpectations,
        (d, n) -> this.completeTimerIf.test(d, n) || completeTimerIf.test(d, n),
        this.serdeFactory);
  }
}
