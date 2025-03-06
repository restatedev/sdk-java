// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.Request;
import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingRunnable;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.types.AbortedExecutionException;
import dev.restate.sdk.types.HandlerRequest;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.TerminalException;
import dev.restate.serde.Serde;
import dev.restate.serde.TypeTag;
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

  HandlerRequest request();

  /**
   * Invoke another Restate service method.
   *
   * @param request request
   * @return an {@link Awaitable} that wraps the Restate service method result.
   */
  <T, R> CallAwaitable<R> call(Request<T, R> request);

  /** Like {@link #call(Request)} */
  default <T, R> CallAwaitable<R> call(Request.Builder<T, R> requestBuilder) {
    return call(requestBuilder.build());
  }

  /**
   * Invoke another Restate service without waiting for the response.
   *
   * @param request request
   * @return an {@link InvocationHandle} that can be used to retrieve the invocation id, cancel the
   *     invocation, attach to its result.
   */
  <T, R> InvocationHandle<R> send(Request<T, R> request);

  /** Like {@link #send(Request)} */
  default <T, R> InvocationHandle<R> send(Request.Builder<T, R> requestBuilder) {
    return send(requestBuilder.asSend());
  }

  /** Like {@link #invocationHandle(String, Class)} */
  <R> InvocationHandle<R> invocationHandle(String invocationId, TypeTag<R> responseTypeTag);

  /**
   * Get an {@link InvocationHandle} for an already existing invocation. This will let you interact
   * with a running invocation, for example to cancel it or retrieve its result.
   *
   * @param invocationId The invocation to interact with.
   * @param responseClazz The response class.
   */
  default <R> InvocationHandle<R> invocationHandle(String invocationId, Class<R> responseClazz) {
    return invocationHandle(invocationId, TypeTag.of(responseClazz));
  }

  /** Like {@link #invocationHandle(String, Class)}, without providing a response parser */
  default InvocationHandle<Slice> invocationHandle(String invocationId) {
    return invocationHandle(invocationId, Serde.SLICE);
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
  default Awaitable<Void> timer(Duration duration) {
    return timer(null, duration);
  }

  /**
   * Causes the start of a timer for the given duration. You can await on the timer end by invoking
   * {@link Awaitable#await()}.
   *
   * @param name name used for observability
   * @param duration for which to sleep.
   */
  Awaitable<Void> timer(String name, Duration duration);

  /**
   * Like {@link #run(String, TypeTag, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default <T> T run(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(name, typeTag, retryPolicy, action).await();
  }

  /**
   * Like {@link #run(String, Class, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default <T> T run(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return run(name, TypeTag.of(clazz), retryPolicy, action);
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
   * #run(String, TypeTag, RetryPolicy, ThrowingSupplier)}.
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
   * @param typeTag the type tag of the return value, used to serialize/deserialize it.
   * @param action closure to execute.
   * @param <T> type of the return value.
   * @return value of the run operation.
   */
  default <T> T run(String name, TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return run(name, typeTag, null, action);
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
   * #run(String, Class, RetryPolicy, ThrowingSupplier)}.
   *
   * <p><b>Error handling</b>: Errors occurring within this closure won't be propagated to the
   * caller, unless they are {@link TerminalException}. Consider the following code:
   *
   * <pre>{@code
   * // Bad usage of try-catch outside the run
   * try {
   *     ctx.run(() -> {
   *         throw new IllegalStateException();
   *     }).await();
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
   *     }).await();
   * } catch (TerminalException e) {
   *     // This is invoked
   * }
   * }</pre>
   *
   * To propagate run failures to the call-site, make sure to wrap them in {@link
   * TerminalException}.
   *
   * @param name name of the side effect.
   * @param clazz the class of the return value, used to serialize/deserialize it.
   * @param action closure to execute.
   * @param <T> type of the return value.
   * @return value of the run operation.
   */
  default <T> T run(String name, Class<T> clazz, ThrowingSupplier<T> action)
      throws TerminalException {
    return run(name, TypeTag.of(clazz), action);
  }

  default <T> T run(TypeTag<T> typeTag, ThrowingSupplier<T> action) throws TerminalException {
    return run(null, typeTag, null, action);
  }

  default <T> T run(Class<T> clazz, ThrowingSupplier<T> action) throws TerminalException {
    return run(TypeTag.of(clazz), action);
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

  /** Like {@link #run(String, Class, ThrowingSupplier)} without output. */
  default void run(String name, ThrowingRunnable runnable) throws TerminalException {
    run(name, null, runnable);
  }

  /** Like {@link #run(Class, ThrowingSupplier)} without output. */
  default void run(ThrowingRunnable runnable) throws TerminalException {
    run(null, runnable);
  }

  /**
   * Like {@link #runAsync(String, TypeTag, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  <T> Awaitable<T> runAsync(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException;

  /**
   * Like {@link #runAsync(String, Class, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default <T> Awaitable<T> runAsync(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(name, TypeTag.of(clazz), retryPolicy, action);
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
   * #runAsync(String, TypeTag, RetryPolicy, ThrowingSupplier)}.
   *
   * <p><b>Error handling</b>: Errors occurring within this closure won't be propagated to the
   * caller, unless they are {@link TerminalException}. Consider the following code:
   *
   * <pre>{@code
   * // Bad usage of try-catch outside the run
   * try {
   *     ctx.runAsync(() -> {
   *         throw new IllegalStateException();
   *     }).await();
   * } catch (IllegalStateException e) {
   *     // This will never be executed,
   *     // but the error will be retried by Restate,
   *     // following the invocation retry policy.
   * }
   *
   * // Good usage of try-catch outside the run
   * try {
   *     ctx.runAsync(() -> {
   *         throw new TerminalException("my error");
   *     }).await();
   * } catch (TerminalException e) {
   *     // This is invoked
   * }
   * }</pre>
   *
   * To propagate run failures to the call-site, make sure to wrap them in {@link
   * TerminalException}.
   *
   * @param name name of the side effect.
   * @param typeTag the type tag of the return value, used to serialize/deserialize it.
   * @param action closure to execute.
   * @param <T> type of the return value.
   * @return value of the run operation.
   */
  default <T> Awaitable<T> runAsync(String name, TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(name, typeTag, null, action);
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
   * #runAsync(String, Class, RetryPolicy, ThrowingSupplier)}.
   *
   * <p><b>Error handling</b>: Errors occurring within this closure won't be propagated to the
   * caller, unless they are {@link TerminalException}. Consider the following code:
   *
   * <pre>{@code
   * // Bad usage of try-catch outside the run
   * try {
   *     ctx.runAsync(() -> {
   *         throw new IllegalStateException();
   *     }).await();
   * } catch (IllegalStateException e) {
   *     // This will never be executed,
   *     // but the error will be retried by Restate,
   *     // following the invocation retry policy.
   * }
   *
   * // Good usage of try-catch outside the run
   * try {
   *     ctx.runAsync(() -> {
   *         throw new TerminalException("my error");
   *     }).await();
   * } catch (TerminalException e) {
   *     // This is invoked
   * }
   * }</pre>
   *
   * To propagate run failures to the call-site, make sure to wrap them in {@link
   * TerminalException}.
   *
   * @param name name of the side effect.
   * @param clazz the class of the return value, used to serialize/deserialize it.
   * @param action closure to execute.
   * @param <T> type of the return value.
   * @return value of the run operation.
   */
  default <T> Awaitable<T> runAsync(String name, Class<T> clazz, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(name, TypeTag.of(clazz), action);
  }

  default <T> Awaitable<T> runAsync(TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(null, typeTag, null, action);
  }

  default <T> Awaitable<T> runAsync(Class<T> clazz, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(TypeTag.of(clazz), action);
  }

  /**
   * Like {@link #runAsync(String, ThrowingRunnable)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see RetryPolicy
   */
  default Awaitable<Void> runAsync(String name, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    return runAsync(
        name,
        Serde.VOID,
        retryPolicy,
        () -> {
          runnable.run();
          return null;
        });
  }

  /** Like {@link #runAsync(String, Class, ThrowingSupplier)} without output. */
  default Awaitable<Void> runAsync(String name, ThrowingRunnable runnable)
      throws TerminalException {
    return runAsync(name, null, runnable);
  }

  /** Like {@link #runAsync(Class, ThrowingSupplier)} without output. */
  default Awaitable<Void> runAsync(ThrowingRunnable runnable) throws TerminalException {
    return runAsync(null, runnable);
  }

  /**
   * Create an {@link Awakeable}, addressable through {@link Awakeable#id()}.
   *
   * <p>You can use this feature to implement external asynchronous systems interactions, for
   * example you can send a Kafka record including the {@link Awakeable#id()}, and then let another
   * service consume from Kafka the responses of given external system interaction by using {@link
   * #awakeableHandle(String)}.
   *
   * @param clazz the response type to use for deserializing the {@link Awakeable} result. When
   *     using generic types, use {@link #awakeable(TypeTag)} instead.
   * @return the {@link Awakeable} to await on.
   * @see Awakeable
   */
  default <T> Awakeable<T> awakeable(Class<T> clazz) {
    return awakeable(TypeTag.of(clazz));
  }

  /**
   * Create an {@link Awakeable}, addressable through {@link Awakeable#id()}.
   *
   * <p>You can use this feature to implement external asynchronous systems interactions, for
   * example you can send a Kafka record including the {@link Awakeable#id()}, and then let another
   * service consume from Kafka the responses of given external system interaction by using {@link
   * #awakeableHandle(String)}.
   *
   * @param typeTag the response type tag to use for deserializing the {@link Awakeable} result.
   * @return the {@link Awakeable} to await on.
   * @see Awakeable
   */
  <T> Awakeable<T> awakeable(TypeTag<T> typeTag);

  /**
   * Create a new {@link AwakeableHandle} for the provided identifier. You can use it to {@link
   * AwakeableHandle#resolve(TypeTag, Object)} or {@link AwakeableHandle#reject(String)} the linked
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
