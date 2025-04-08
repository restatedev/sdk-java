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
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.HandlerRequest;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.serde.Serde;
import dev.restate.serde.TypeTag;
import java.time.Duration;

/**
 * This interface exposes the Restate functionalities to Restate services. It can be used to
 * interact with other Restate services, record non-deterministic closures, execute timers and
 * synchronize with external systems.
 *
 * <h2>Error handling</h2>
 *
 * All methods of this interface, and related interfaces, throws either {@link TerminalException} or
 * {@link AbortedExecutionException}, where the former can be caught and acted upon, while the
 * latter MUST NOT be caught, but simply propagated for clean up purposes.
 *
 * <h2>Serialization and Deserialization</h2>
 *
 * The methods of this interface that need to serialize or deserialize payloads have an overload
 * both accepting {@link Class} or {@link TypeTag}. Depending on your case, you might use the {@link
 * Class} overload for simple types, and {@link dev.restate.serde.TypeRef} for generic types:
 *
 * <pre>{@code
 * String result = ctx.run(
 *    "my-http-request",
 *    String.class,
 *    () -> doHttpRequest().getResult()
 * ).await();
 *
 * List<String> result = ctx.run(
 *    "my-http-request",
 *    new TypeRef<>(){ },
 *    () -> doHttpRequest().getResult()
 * ).await();
 * }</pre>
 *
 * By default, Jackson Databind will be used for all serialization/deserialization. Check {@link
 * dev.restate.serde.SerdeFactory} for more details on how to customize that.
 *
 * <h2>Thread safety</h2>
 *
 * This interface <b>MUST NOT</b> be accessed concurrently since it can lead to different orderings
 * of user actions, corrupting the execution of the invocation.
 */
public interface Context {

  HandlerRequest request();

  /**
   * Invoke another Restate service method.
   *
   * @param request Request object. For each service, a class called {@code
   *     <your_class_name>Handlers} is generated containing the request builders.
   * @return an {@link DurableFuture} that wraps the Restate service method result.
   */
  <T, R> CallDurableFuture<R> call(Request<T, R> request);

  /**
   * Invoke another Restate service without waiting for the response.
   *
   * @param request Request object. For each service, a class called {@code
   *     <your_class_name>Handlers} is generated containing the request builders.
   * @return an {@link InvocationHandle} that can be used to retrieve the invocation id, cancel the
   *     invocation, attach to its result.
   */
  default <T, R> InvocationHandle<R> send(Request<T, R> request) {
    return send(request, null);
  }

  /**
   * Invoke another Restate service without waiting for the response.
   *
   * @param request Request object. For each service, a class called {@code
   *     <your_class_name>Handlers} is generated containing the request builders.
   * @param delay the delay to send the request
   * @return an {@link InvocationHandle} that can be used to retrieve the invocation id, cancel the
   *     invocation, attach to its result.
   */
  <T, R> InvocationHandle<R> send(Request<T, R> request, Duration delay);

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
   * {@link DurableFuture#await()}.
   *
   * @param duration for which to sleep.
   */
  default DurableFuture<Void> timer(Duration duration) {
    return timer(null, duration);
  }

  /**
   * Causes the start of a timer for the given duration. You can await on the timer end by invoking
   * {@link DurableFuture#await()}.
   *
   * @param name name used for observability
   * @param duration for which to sleep.
   */
  DurableFuture<Void> timer(String name, Duration duration);

  /**
   * Execute a non-deterministic closure, recording the result value in the journal. The result
   * value will be re-played in case of re-invocation (e.g. because of failure recovery or
   * suspension point) without re-executing the closure. Use this feature if you want to perform
   * <b>non-deterministic operations</b>.
   *
   * <pre>{@code
   * String result = ctx.run(
   *    "my-http-request",
   *    String.class,
   *    () -> doHttpRequest().getResult()
   * ).await();
   * }</pre>
   *
   * If the result type contains generic types, e.g. a {@code List<String>}, you should use {@link
   * #run(String, TypeTag, ThrowingSupplier)}. See {@link Context} for more details about
   * serialization and deserialization.
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

  /**
   * Like {@link #run(String, TypeTag, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see #run(String, Class, ThrowingSupplier)
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
   * @see #run(String, Class, ThrowingSupplier)
   * @see RetryPolicy
   */
  default <T> T run(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return run(name, TypeTag.of(clazz), retryPolicy, action);
  }

  /**
   * Like {@link #run(String, Class, ThrowingSupplier)}, but providing a {@link TypeTag}.
   *
   * <p>See {@link Context} for more details about serialization and deserialization.
   *
   * @see #run(String, Class, ThrowingSupplier)
   */
  default <T> T run(String name, TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return run(name, typeTag, null, action);
  }

  /**
   * Like {@link #run(String, TypeTag, ThrowingSupplier)}, without a name
   *
   * @see #run(String, Class, ThrowingSupplier)
   */
  default <T> T run(TypeTag<T> typeTag, ThrowingSupplier<T> action) throws TerminalException {
    return run(null, typeTag, null, action);
  }

  /**
   * Like {@link #run(String, Class, ThrowingSupplier)}, without a name
   *
   * @see #run(String, Class, ThrowingSupplier)
   */
  default <T> T run(Class<T> clazz, ThrowingSupplier<T> action) throws TerminalException {
    return run(TypeTag.of(clazz), action);
  }

  /**
   * Like {@link #run(String, ThrowingRunnable)}, but without a return value and using a custom
   * retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see #run(String, Class, ThrowingSupplier)
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
   * Like {@link #run(String, Class, ThrowingSupplier)} without output.
   *
   * @see #run(String, Class, ThrowingSupplier)
   */
  default void run(String name, ThrowingRunnable runnable) throws TerminalException {
    run(name, null, runnable);
  }

  /**
   * Like {@link #run(Class, ThrowingSupplier)} without output.
   *
   * @see #run(String, Class, ThrowingSupplier)
   */
  default void run(ThrowingRunnable runnable) throws TerminalException {
    run(null, runnable);
  }

  /**
   * Execute a non-deterministic action asynchronously. This is like {@link #run(String, Class,
   * ThrowingSupplier)}, but it returns a {@link DurableFuture} that you can combine and select.
   *
   * <pre>{@code
   * // Fan-out
   * var resultFutures = subTasks.stream()
   *         .map(task ->
   *            ctx.runAsync(
   *                 task.description(),
   *                 String.class,
   *                 () -> task.execute()
   *            )
   *         )
   *         .toList();
   *
   * // Await all of them
   * DurableFuture.all(resultFutures).await();
   *
   * // Fan in - Aggregate the results
   * var results = resultFutures.stream()
   *         .map(future -> future.await())
   *         .toList();
   * }</pre>
   *
   * @see #run(String, Class, ThrowingSupplier)
   */
  default <T> DurableFuture<T> runAsync(String name, Class<T> clazz, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(name, TypeTag.of(clazz), action);
  }

  /**
   * Like {@link #runAsync(String, Class, ThrowingSupplier)}, but providing a {@link TypeTag}.
   *
   * <p>See {@link Context} for more details about serialization and deserialization.
   *
   * @see #runAsync(String, Class, ThrowingSupplier)
   */
  default <T> DurableFuture<T> runAsync(String name, TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(name, typeTag, null, action);
  }

  /**
   * Like {@link #runAsync(String, Class, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see #runAsync(String, Class, ThrowingSupplier)
   * @see RetryPolicy
   */
  default <T> DurableFuture<T> runAsync(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(name, TypeTag.of(clazz), retryPolicy, action);
  }

  /**
   * Like {@link #runAsync(String, TypeTag, ThrowingSupplier)}, but using a custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see #runAsync(String, Class, ThrowingSupplier)
   * @see RetryPolicy
   */
  <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException;

  /**
   * Like {@link #runAsync(String, TypeTag, ThrowingSupplier)}, without a name
   *
   * @see #runAsync(String, Class, ThrowingSupplier)
   */
  default <T> DurableFuture<T> runAsync(TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(null, typeTag, null, action);
  }

  /**
   * Like {@link #runAsync(String, Class, ThrowingSupplier)}, without a name
   *
   * @see #runAsync(String, Class, ThrowingSupplier)
   */
  default <T> DurableFuture<T> runAsync(Class<T> clazz, ThrowingSupplier<T> action)
      throws TerminalException {
    return runAsync(TypeTag.of(clazz), action);
  }

  /**
   * Like {@link #runAsync(String, Class, ThrowingSupplier)}, but without an output and using a
   * custom retry policy.
   *
   * <p>When a retry policy is not specified, the {@code run} will be retried using the <a
   * href="https://docs.restate.dev/operate/configuration/server">Restate invoker retry policy</a>,
   * which by default retries indefinitely.
   *
   * @see #runAsync(String, Class, ThrowingSupplier)
   * @see RetryPolicy
   */
  default DurableFuture<Void> runAsync(
      String name, RetryPolicy retryPolicy, ThrowingRunnable runnable) throws TerminalException {
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
  default DurableFuture<Void> runAsync(String name, ThrowingRunnable runnable)
      throws TerminalException {
    return runAsync(name, null, runnable);
  }

  /** Like {@link #runAsync(String, Class, ThrowingSupplier)} without output. */
  default DurableFuture<Void> runAsync(ThrowingRunnable runnable) throws TerminalException {
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
   * Returns a deterministic random.
   *
   * @see RestateRandom
   */
  RestateRandom random();
}
