// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.function.ThrowingRunnable;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.types.AbortedExecutionException;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.TerminalException;
import dev.restate.common.Target;
import dev.restate.serde.Serde;
import dev.restate.sdk.types.Request;

import java.time.Duration;

/**
 * This interface exposes the Restate functionalities to Restate services. It can be used to
 * interact with other Restate services, record non-deterministic closures, execute timers and
 * synchronize with external systems.
 *
 * <p>All methods of this interface, and related interfaces, throws either {@link TerminalException}
 * or {@link AbortedExecutionException}, where the former can be caught and acted upon, while the
 * latter MUST NOT be caught, but simply propagated for clean up purposes.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 */
public interface Context {

  Request request();

  /**
   * Invoke another Restate service method.
   *
   * @param target the address of the callee
   * @param inputSerde Input serde
   * @param outputSerde Output serde
   * @param parameter the invocation request parameter.
   * @return an {@link Awaitable} that wraps the Restate service method result.
   */
  <T, R> Awaitable<R> call(Target target, Serde<T> inputSerde, Serde<R> outputSerde, T parameter);

  /** Like {@link #call(Target, Serde, Serde, Object)} with raw input/output. */
  default Awaitable<byte[]> call(Target target, byte[] parameter) {
    return call(target, Serde.RAW, Serde.RAW, parameter);
  }

  /**
   * Invoke another Restate service without waiting for the response.
   *
   * @param target the address of the callee
   * @param inputSerde Input serde
   * @param parameter the invocation request parameter.
   */
  <T> void send(Target target, Serde<T> inputSerde, T parameter);

  /** Like {@link #send(Target, Serde, Object)} with bytes input. */
  default void send(Target target, byte[] parameter) {
    send(target, Serde.RAW, parameter);
  }

  /**
   * Invoke another Restate service without waiting for the response after the provided {@code
   * delay} has elapsed.
   *
   * <p>This method returns immediately, as the timer is executed and awaited on Restate.
   *
   * @param target the address of the callee
   * @param inputSerde Input serde
   * @param parameter the invocation request parameter.
   * @param delay time to wait before executing the call.
   */
  <T> void send(Target target, Serde<T> inputSerde, T parameter, Duration delay);

  /** Like {@link #send(Target, Serde, Object, Duration)} with bytes input. */
  default void send(Target target, byte[] parameter, Duration delay) {
    send(target, Serde.RAW, parameter, delay);
  }

  /**
   * Causes the current execution of the function invocation to sleep for the given duration.
   *
   * @param duration for which to sleep.
   */
  default void sleep(Duration duration) {
    timer(duration).await();
  }

  /**
   * Causes the start of a timer for the given duration. You can await on the timer end by invoking
   * {@link Awaitable#await()}.
   *
   * @param duration for which to sleep.
   */
  Awaitable<Void> timer(Duration duration);

  /**
   * Execute a non-deterministic closure, recording the result value in the journal. The result
   * value will be re-played in case of re-invocation (e.g. because of failure recovery or
   * suspension point) without re-executing the closure. Use this feature if you want to perform
   * <b>non-deterministic operations</b>.
   *
   * <p>You can name this closure using the {@code name} parameter. This name will be available in
   * the observability tools.
   *
   * <p>The closure should tolerate retries, that is Restate might re-execute the closure multiple
   * times until it records a result. You can control and limit the amount of retries using {@link
   * #run(String, Serde, RetryPolicy, ThrowingSupplier)}.
   *
   * <p><b>Error handling</b>: Errors occurring within this closure won't be propagated to the
   * caller, unless they are {@link TerminalException}. Consider the following code:
   *
   * <pre>{@code
   * // Bad usage of try-catch outside the run
   * try {
   *     ctx.run(() -> {
   *         throw new IllegalStateException();
   *     });
   * } catch (IllegalStateException e) {
   *     // This will never be executed,
   *     // but the error will be retried by Restate,
   *     // following the invocation retry policy.
   * }
   *
   * // Good usage of try-catch outside the run
   * try {
   *     ctx.run(() -> {
   *         throw new TerminalException("my error");
   *     });
   * } catch (TerminalException e) {
   *     // This is invoked
   * }
   * }</pre>
   *
   * To propagate run failures to the call-site, make sure to wrap them in {@link
   * TerminalException}.
   *
   * @param name name of the side effect.
   * @param serde the type tag of the return value, used to serialize/deserialize it.
   * @param action closure to execute.
   * @param <T> type of the return value.
   * @return value of the run operation.
   */
  default <T> T run(String name, Serde<T> serde, ThrowingSupplier<T> action)
      throws TerminalException {
    return run(name, serde, null, action);
  }

  /**
   * Like {@link #run(String, Serde, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default <T> T run(String name, Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return scheduleRun(name, serde, retryPolicy, action).await();
  }

  /**
   * Like {@link #run(String, ThrowingRunnable)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default void run(String name, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    run(
        name,
        Serde.VOID,
        retryPolicy,
        () -> {
          runnable.run();
          return null;
        });
  }

  /**
   * Like {@link #run(Serde, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default <T> T run(Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return run(null, serde, retryPolicy, action);
  }

  /**
   * Like {@link #run(ThrowingRunnable)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default void run(RetryPolicy retryPolicy, ThrowingRunnable runnable) throws TerminalException {
    run(null, retryPolicy, runnable);
  }

  /** Like {@link #run(String, Serde, ThrowingSupplier)}, but without returning a value. */
  default void run(String name, ThrowingRunnable runnable) throws TerminalException {
    run(
        name,
        Serde.VOID,
        () -> {
          runnable.run();
          return null;
        });
  }

  /** Like {@link #run(String, Serde, ThrowingSupplier)}, but without a name. */
  default <T> T run(Serde<T> serde, ThrowingSupplier<T> action) throws TerminalException {
    return run(null, serde, action);
  }

  /** Like {@link #run(String, ThrowingRunnable)}, but without a name. */
  default void run(ThrowingRunnable runnable) throws TerminalException {
    run((String) null, runnable);
  }

  /**
   * Execute a non-deterministic closure, recording the result value in the journal. The result
   * value will be re-played in case of re-invocation (e.g. because of failure recovery or
   * suspension point) without re-executing the closure. Use this feature if you want to perform
   * <b>non-deterministic operations</b>.
   *
   * <p>You can name this closure using the {@code name} parameter. This name will be available in
   * the observability tools.
   *
   * <p>The closure should tolerate retries, that is Restate might re-execute the closure multiple
   * times until it records a result. You can control and limit the amount of retries using {@link
   * #run(String, Serde, RetryPolicy, ThrowingSupplier)}.
   *
   * <p><b>Error handling</b>: Errors occurring within this closure won't be propagated to the
   * caller, unless they are {@link TerminalException}. Consider the following code:
   *
   * <pre>{@code
   * // Bad usage of try-catch outside the run
   * try {
   *     ctx.run(() -> {
   *         throw new IllegalStateException();
   *     });
   * } catch (IllegalStateException e) {
   *     // This will never be executed,
   *     // but the error will be retried by Restate,
   *     // following the invocation retry policy.
   * }
   *
   * // Good usage of try-catch outside the run
   * try {
   *     ctx.run(() -> {
   *         throw new TerminalException("my error");
   *     });
   * } catch (TerminalException e) {
   *     // This is invoked
   * }
   * }</pre>
   *
   * To propagate run failures to the call-site, make sure to wrap them in {@link
   * TerminalException}.
   *
   * @param name name of the side effect.
   * @param serde the type tag of the return value, used to serialize/deserialize it.
   * @param action closure to execute.
   * @param <T> type of the return value.
   * @return value of the run operation.
   */
  default <T> Awaitable<T> scheduleRun(String name, Serde<T> serde, ThrowingSupplier<T> action)
          throws TerminalException {
    return scheduleRun(name, serde, null, action);
  }

  /**
   * Like {@link #run(String, Serde, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  <T> Awaitable<T> scheduleRun(String name, Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
          throws TerminalException;

  /**
   * Like {@link #run(String, ThrowingRunnable)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default Awaitable<Void> scheduleRun(String name, RetryPolicy retryPolicy, ThrowingRunnable runnable)
          throws TerminalException {
    return scheduleRun(
            name,
            Serde.VOID,
            retryPolicy,
            () -> {
              runnable.run();
              return null;
            });
  }

  /**
   * Like {@link #run(Serde, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default <T> Awaitable<T> scheduleRun(Serde<T> serde, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
          throws TerminalException {
    return scheduleRun(null, serde, retryPolicy, action);
  }

  /**
   * Like {@link #run(ThrowingRunnable)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default Awaitable<Void> scheduleRun(RetryPolicy retryPolicy, ThrowingRunnable runnable) throws TerminalException {
    return scheduleRun(null, retryPolicy, runnable);
  }

  /** Like {@link #run(String, Serde, ThrowingSupplier)}, but without returning a value. */
  default Awaitable<Void> scheduleRun(String name, ThrowingRunnable runnable) throws TerminalException {
    return scheduleRun(
            name,
            Serde.VOID,
            () -> {
              runnable.run();
              return null;
            });
  }

  /** Like {@link #run(String, Serde, ThrowingSupplier)}, but without a name. */
  default <T> Awaitable<T> scheduleRun(Serde<T> serde, ThrowingSupplier<T> action) throws TerminalException {
    return scheduleRun(null, serde, action);
  }

  /** Like {@link #run(String, ThrowingRunnable)}, but without a name. */
  default Awaitable<Void> scheduleRun(ThrowingRunnable runnable) throws TerminalException {
    return scheduleRun((String) null, runnable);
  }

  /**
   * Create an {@link Awakeable}, addressable through {@link Awakeable#id()}.
   *
   * <p>You can use this feature to implement external asynchronous systems interactions, for
   * example you can send a Kafka record including the {@link Awakeable#id()}, and then let another
   * service consume from Kafka the responses of given external system interaction by using {@link
   * #awakeableHandle(String)}.
   *
   * @param serde the response type tag to use for deserializing the {@link Awakeable} result.
   * @return the {@link Awakeable} to await on.
   * @see Awakeable
   */
  <T> Awakeable<T> awakeable(Serde<T> serde);

  /**
   * Create a new {@link AwakeableHandle} for the provided identifier. You can use it to {@link
   * AwakeableHandle#resolve(Serde, Object)} or {@link AwakeableHandle#reject(String)} the linked
   * {@link Awakeable}.
   *
   * @see Awakeable
   */
  AwakeableHandle awakeableHandle(String id);

  /**
   * @see RestateRandom
   */
  RestateRandom random();
}
