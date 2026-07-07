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
import dev.restate.common.Target;
import dev.restate.common.function.ThrowingRunnable;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.common.reflections.MethodInfo;
import dev.restate.common.reflections.ProxySupport;
import dev.restate.common.reflections.ReflectionUtils;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.annotation.Workflow;
import dev.restate.sdk.common.*;
import dev.restate.serde.Serde;
import dev.restate.serde.TypeTag;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

/**
 * This class exposes the Restate functionalities to Restate services using the reflection-based
 * API. It can be used to interact with other Restate services, record non-deterministic closures,
 * execute timers, and synchronize with external systems.
 *
 * <p>This is the entry point for the new reflection-based API where services are defined using
 * annotations and methods can access Restate features through static methods on this class.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * @Service
 * public class Greeter {
 *
 *   @Handler
 *   public String greet(String input) {
 *     // Use Restate features via static methods
 *     String result = Restate.run(
 *       "external-call",
 *       String.class,
 *       () -> externalService.call(input)
 *     );
 *
 *     return "You said hi to " + req.name + "!";
 *   }
 * }
 * }</pre>
 *
 * <h2>Error handling</h2>
 *
 * All methods of this class throws either {@link TerminalException} or {@link
 * AbortedExecutionException}, where the former can be caught and acted upon, while the latter MUST
 * NOT be caught, but simply propagated for clean up purposes.
 *
 * <h2>Serialization and Deserialization</h2>
 *
 * The methods of this class that need to serialize or deserialize payloads have an overload both
 * accepting {@link Class} or {@link TypeTag}. Depending on your case, you might use the {@link
 * Class} overload for simple types, and {@link dev.restate.serde.TypeRef} for generic types.
 *
 * <p>By default, Jackson Databind will be used for all serialization/deserialization. Check {@link
 * dev.restate.serde.SerdeFactory} for more details on how to customize that.
 *
 * <h2>Thread safety</h2>
 *
 * This class <b>MUST NOT</b> be accessed concurrently since it can lead to different orderings of
 * user actions, corrupting the execution of the invocation.
 *
 * @see Context
 */
@SuppressWarnings("deprecation")
public final class Restate {
  /**
   * @see Context#request()
   */
  public static HandlerRequest request() {
    return Context.current().request();
  }

  /**
   * Returns a deterministic random.
   *
   * @see RestateRandom
   * @see Context#random()
   */
  public static RestateRandom random() {
    return Context.current().random();
  }

  /**
   * Returns the current time as a deterministic {@link Instant}.
   *
   * <p>This method returns the current timestamp in a way that is consistent across replays. The
   * time is captured using {@link Restate#run}, ensuring that the same value is returned during
   * replay as was returned during the original execution.
   *
   * @return the recorded {@link Instant}
   * @see Instant#now()
   */
  public static Instant instantNow() {
    return Context.current().instantNow();
  }

  /**
   * @see Context#invocationHandle(String, TypeTag)
   */
  public static <R> InvocationHandle<R> invocationHandle(
      String invocationId, TypeTag<R> responseTypeTag) {
    return Context.current().invocationHandle(invocationId, responseTypeTag);
  }

  /**
   * Get an {@link InvocationHandle} for an already existing invocation. This will let you interact
   * with a running invocation, for example to cancel it or retrieve its result.
   *
   * @param invocationId The invocation to interact with.
   * @param responseClazz The response class.
   * @see Context#invocationHandle(String, Class)
   */
  public static <R> InvocationHandle<R> invocationHandle(
      String invocationId, Class<R> responseClazz) {
    return Context.current().invocationHandle(invocationId, responseClazz);
  }

  /**
   * Like {@link #invocationHandle(String, Class)}, without providing a response parser
   *
   * @see Context#invocationHandle(String)
   */
  public static InvocationHandle<Slice> invocationHandle(String invocationId) {
    return Context.current().invocationHandle(invocationId);
  }

  /**
   * Causes the current execution of the function invocation to sleep for the given duration.
   *
   * @param duration for which to sleep.
   * @see Context#sleep(Duration)
   */
  public static void sleep(Duration duration) {
    Context.current().sleep(duration);
  }

  /**
   * Causes the start of a timer for the given duration. You can await on the timer end by invoking
   * {@link DurableFuture#await()}.
   *
   * @param name name used for observability
   * @param duration for which to sleep.
   * @see Context#timer(String, Duration)
   */
  public static DurableFuture<Void> timer(String name, Duration duration) {
    return Context.current().timer(name, duration);
  }

  /**
   * Execute a closure, recording the result value in the journal. The result value will be
   * re-played in case of re-invocation (e.g. because of failure recovery or suspension point)
   * without re-executing the closure.
   *
   * <p>If the result type contains generic types, e.g. a {@code List<String>}, you should use
   * {@link #run(String, TypeTag, ThrowingSupplier)}. See {@link Context} for more details about
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
   * caller, unless they are {@link TerminalException}. To propagate run failures to the call-site,
   * make sure to wrap them in {@link TerminalException}.
   *
   * @param name name of the side effect.
   * @param clazz the class of the return value, used to serialize/deserialize it.
   * @param action closure to execute.
   * @param <T> type of the return value.
   * @return value of the run operation.
   * @see Context#run(String, Class, ThrowingSupplier)
   */
  public static <T> T run(String name, Class<T> clazz, ThrowingSupplier<T> action)
      throws TerminalException {
    return Context.current().run(name, clazz, action);
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
   * @see Context#run(String, TypeTag, RetryPolicy, ThrowingSupplier)
   */
  public static <T> T run(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return Context.current().run(name, typeTag, retryPolicy, action);
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
   * @see Context#run(String, Class, RetryPolicy, ThrowingSupplier)
   */
  public static <T> T run(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return Context.current().run(name, clazz, retryPolicy, action);
  }

  /**
   * Like {@link #run(String, Class, ThrowingSupplier)}, but providing a {@link TypeTag}.
   *
   * <p>See {@link Context} for more details about serialization and deserialization.
   *
   * @see #run(String, Class, ThrowingSupplier)
   * @see Context#run(String, TypeTag, ThrowingSupplier)
   */
  public static <T> T run(String name, TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return Context.current().run(name, typeTag, action);
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
   * @see Context#run(String, RetryPolicy, ThrowingRunnable)
   */
  public static void run(String name, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    Context.current().run(name, retryPolicy, runnable);
  }

  /**
   * Like {@link #run(String, Class, ThrowingSupplier)} without output.
   *
   * @see #run(String, Class, ThrowingSupplier)
   * @see Context#run(String, ThrowingRunnable)
   */
  public static void run(String name, ThrowingRunnable runnable) throws TerminalException {
    Context.current().run(name, runnable);
  }

  /**
   * Execute a closure asynchronously. This is like {@link #run(String, Class, ThrowingSupplier)},
   * but it returns a {@link DurableFuture} that you can combine and select.
   *
   * @see #run(String, Class, ThrowingSupplier)
   * @see Context#runAsync(String, Class, ThrowingSupplier)
   */
  public static <T> DurableFuture<T> runAsync(
      String name, Class<T> clazz, ThrowingSupplier<T> action) throws TerminalException {
    return Context.current().runAsync(name, clazz, action);
  }

  /**
   * Like {@link #runAsync(String, Class, ThrowingSupplier)}, but providing a {@link TypeTag}.
   *
   * <p>See {@link Context} for more details about serialization and deserialization.
   *
   * @see #runAsync(String, Class, ThrowingSupplier)
   * @see Context#runAsync(String, TypeTag, ThrowingSupplier)
   */
  public static <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, ThrowingSupplier<T> action) throws TerminalException {
    return Context.current().runAsync(name, typeTag, action);
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
   * @see Context#runAsync(String, Class, RetryPolicy, ThrowingSupplier)
   */
  public static <T> DurableFuture<T> runAsync(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return Context.current().runAsync(name, clazz, retryPolicy, action);
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
   * @see Context#runAsync(String, TypeTag, RetryPolicy, ThrowingSupplier)
   */
  public static <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return Context.current().runAsync(name, typeTag, retryPolicy, action);
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
   * @see Context#runAsync(String, RetryPolicy, ThrowingRunnable)
   */
  public static DurableFuture<Void> runAsync(
      String name, RetryPolicy retryPolicy, ThrowingRunnable runnable) throws TerminalException {
    return Context.current().runAsync(name, retryPolicy, runnable);
  }

  /**
   * Like {@link #runAsync(String, Class, ThrowingSupplier)} without output.
   *
   * @see Context#runAsync(String, ThrowingRunnable)
   */
  public static DurableFuture<Void> runAsync(String name, ThrowingRunnable runnable)
      throws TerminalException {
    return Context.current().runAsync(name, runnable);
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
   * @see Context#awakeable(Class)
   */
  public static <T> Awakeable<T> awakeable(Class<T> clazz) {
    return Context.current().awakeable(clazz);
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
   * @see Context#awakeable(TypeTag)
   */
  public static <T> Awakeable<T> awakeable(TypeTag<T> typeTag) {
    return Context.current().awakeable(typeTag);
  }

  /**
   * Create a new {@link AwakeableHandle} for the provided identifier. You can use it to {@link
   * AwakeableHandle#resolve(TypeTag, Object)} or {@link AwakeableHandle#reject(String)} the linked
   * {@link Awakeable}.
   *
   * @see Awakeable
   * @see Context#awakeableHandle(String)
   */
  public static AwakeableHandle awakeableHandle(String id) {
    return Context.current().awakeableHandle(id);
  }

  /**
   * Create a {@link DurableFuture} waiting on a named signal targeting the current invocation.
   *
   * <p>Signals are identified by {@code (invocationId, name)}. The resolution can arrive before or
   * after the handler starts waiting on the signal — there's no need to pre-register.
   *
   * @param name the signal name.
   * @param clazz the response type to use for deserializing the signal result. When using generic
   *     types, use {@link #signal(String, TypeTag)} instead.
   * @return a {@link DurableFuture} that resolves to the signal value (or rejects with a {@link
   *     TerminalException}).
   * @see Context#signal(String, Class)
   */
  public static <T> DurableFuture<T> signal(String name, Class<T> clazz) {
    return Context.current().signal(name, clazz);
  }

  /**
   * Create a {@link DurableFuture} waiting on a named signal targeting the current invocation.
   *
   * @param name the signal name.
   * @param typeTag the response type tag to use for deserializing the signal result.
   * @return a {@link DurableFuture} that resolves to the signal value (or rejects with a {@link
   *     TerminalException}).
   * @see Context#signal(String, TypeTag)
   */
  public static <T> DurableFuture<T> signal(String name, TypeTag<T> typeTag) {
    return Context.current().signal(name, typeTag);
  }

  /**
   * Causes the start of a timer for the given duration. You can await on the timer end by invoking
   * {@link DurableFuture#await()}.
   *
   * @param duration for which to sleep.
   * @see Context#timer(Duration)
   */
  public static DurableFuture<Void> timer(Duration duration) {
    return Context.current().timer(duration);
  }

  /**
   * Invoke another Restate service method, and await the response.
   *
   * <p>This is the low-level, generic invocation entrypoint accepting a pre-built {@link Request}.
   * For the common case, prefer the type-safe methods {@link #service(Class)}, {@link
   * #serviceHandle(Class)}, {@link #virtualObject(Class, String)}, {@link
   * #virtualObjectHandle(Class, String)}, {@link #workflow(Class, String)} and {@link
   * #workflowHandle(Class, String)}.
   *
   * @param request Request object describing the target, payload and response type.
   * @return a {@link CallDurableFuture} that wraps the Restate service method result.
   * @see #service(Class)
   * @see #serviceHandle(Class)
   */
  public static <T, R> CallDurableFuture<R> call(Request<T, R> request) {
    return Context.current().call(request);
  }

  /**
   * Invoke another Restate service without waiting for the response (fire-and-forget).
   *
   * <p>This is the low-level, generic invocation entrypoint accepting a pre-built {@link Request}.
   * For the common case, prefer the type-safe {@code send(...)} methods on {@link
   * #serviceHandle(Class)}, {@link #virtualObjectHandle(Class, String)} and {@link
   * #workflowHandle(Class, String)}.
   *
   * @param request Request object describing the target, payload and response type.
   * @return an {@link InvocationHandle} that can be used to retrieve the invocation id, cancel the
   *     invocation, or attach to its result.
   * @see #send(Request, Duration)
   * @see #serviceHandle(Class)
   */
  public static <T, R> InvocationHandle<R> send(Request<T, R> request) {
    return Context.current().send(request);
  }

  /**
   * Like {@link #send(Request)}, but scheduling the invocation after the given {@code delay}.
   *
   * @param request Request object describing the target, payload and response type.
   * @param delay the delay after which the request should be executed.
   * @return an {@link InvocationHandle} that can be used to retrieve the invocation id, cancel the
   *     invocation, or attach to its result.
   * @see #send(Request)
   */
  public static <T, R> InvocationHandle<R> send(Request<T, R> request, Duration delay) {
    return Context.current().send(request, delay);
  }

  /**
   * Simple API to invoke a Restate service.
   *
   * <p>Create a proxy client that allows calling service methods directly and synchronously. This
   * is the recommended approach for straightforward request-response interactions.
   *
   * <pre>{@code
   * var greeterProxy = Restate.service(Greeter.class);
   * GreetingResponse response = greeterProxy.greet(new Greeting("Alice"));
   * }</pre>
   *
   * <p>For advanced use cases requiring asynchronous request handling, composable futures, or
   * invocation options (such as idempotency keys), use {@link #serviceHandle(Class)} instead.
   *
   * @param clazz the service class annotated with {@link Service}
   * @return a proxy client to invoke the service
   */
  public static <SVC> SVC service(Class<SVC> clazz) {
    ReflectionUtils.mustHaveServiceAnnotation(clazz);
    String serviceName = ReflectionUtils.extractServiceName(clazz);
    return ProxySupport.createProxy(
        clazz,
        invocation -> {
          var methodInfo = MethodInfo.fromMethod(invocation.getMethod());

          //noinspection unchecked
          return Context.current()
              .call(
                  Request.of(
                      Target.virtualObject(serviceName, null, methodInfo.getHandlerName()),
                      (TypeTag<? super Object>) methodInfo.getInputType(),
                      (TypeTag<? super Object>) methodInfo.getOutputType(),
                      invocation.getArguments().length == 0 ? null : invocation.getArguments()[0]))
              .await();
        });
  }

  /**
   * Advanced API to invoke a Restate service with full control.
   *
   * <p>Create a handle that provides advanced invocation capabilities including:
   *
   * <ul>
   *   <li>Composable futures for asynchronous request handling
   *   <li>Invocation options such as {@link
   *       dev.restate.common.InvocationOptions#idempotencyKey(String)}
   *   <li>Fire-and-forget requests via {@code send()}
   *   <li>Deferred response handling
   * </ul>
   *
   * <pre>{@code
   * // 1. Use call() with method reference and await the result
   * GreetingResponse response = Restate.serviceHandle(Greeter.class)
   *   .call(Greeter::greet, new Greeting("Alice"))
   *   .await();
   *
   * // 2. Use send() for one-way invocation without waiting
   * InvocationHandle<GreetingResponse> handle = Restate.serviceHandle(Greeter.class)
   *   .send(Greeter::greet, new Greeting("Alice"));
   * }</pre>
   *
   * <p>For simple synchronous request-response interactions, consider using {@link #service(Class)}
   * instead.
   *
   * @param clazz the service class annotated with {@link Service}
   * @return a handle to invoke the service with advanced options
   */
  public static <SVC> ServiceHandle<SVC> serviceHandle(Class<SVC> clazz) {
    ReflectionUtils.mustHaveServiceAnnotation(clazz);
    return new ServiceHandleImpl<>(clazz, null);
  }

  /**
   * Simple API to invoke a Restate Virtual Object.
   *
   * <p>Create a proxy client that allows calling virtual object methods directly and synchronously.
   * This is the recommended approach for straightforward request-response interactions.
   *
   * <pre>{@code
   * var counterProxy = Restate.virtualObject(Counter.class, "my-counter");
   * int count = counterProxy.increment();
   * }</pre>
   *
   * <p>For advanced use cases requiring asynchronous request handling, composable futures, or
   * invocation options (such as idempotency keys), use {@link #virtualObjectHandle(Class, String)}
   * instead.
   *
   * @param clazz the virtual object class annotated with {@link VirtualObject}
   * @param key the key identifying the specific virtual object instance
   * @return a proxy client to invoke the virtual object
   */
  public static <SVC> SVC virtualObject(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz);
    String serviceName = ReflectionUtils.extractServiceName(clazz);
    return ProxySupport.createProxy(
        clazz,
        invocation -> {
          var methodInfo = MethodInfo.fromMethod(invocation.getMethod());

          //noinspection unchecked
          return Context.current()
              .call(
                  Request.of(
                      Target.virtualObject(serviceName, key, methodInfo.getHandlerName()),
                      (TypeTag<? super Object>) methodInfo.getInputType(),
                      (TypeTag<? super Object>) methodInfo.getOutputType(),
                      invocation.getArguments().length == 0 ? null : invocation.getArguments()[0]))
              .await();
        });
  }

  /**
   * Advanced API to invoke a Restate Virtual Object with full control.
   *
   * <p>Create a handle that provides advanced invocation capabilities including:
   *
   * <ul>
   *   <li>Composable futures for asynchronous request handling
   *   <li>Invocation options such as {@link
   *       dev.restate.common.InvocationOptions#idempotencyKey(String)}
   *   <li>Fire-and-forget requests via {@code send()}
   *   <li>Deferred response handling
   * </ul>
   *
   * <pre>{@code
   * // 1. Use call() with method reference and await the result
   * int count = Restate.virtualObjectHandle(Counter.class, "my-counter")
   *   .call(Counter::increment)
   *   .await();
   *
   * // 2. Use send() for one-way invocation without waiting
   * InvocationHandle<Integer> handle = Restate.virtualObjectHandle(Counter.class, "my-counter")
   *   .send(Counter::increment);
   * }</pre>
   *
   * <p>For simple synchronous request-response interactions, consider using {@link
   * #virtualObject(Class, String)} instead.
   *
   * @param clazz the virtual object class annotated with {@link VirtualObject}
   * @param key the key identifying the specific virtual object instance
   * @return a handle to invoke the virtual object with advanced options
   */
  public static <SVC> ServiceHandle<SVC> virtualObjectHandle(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz);
    return new ServiceHandleImpl<>(clazz, key);
  }

  /**
   * Simple API to invoke a Restate Workflow.
   *
   * <p>Create a proxy client that allows calling workflow methods directly and synchronously. This
   * is the recommended approach for straightforward request-response interactions.
   *
   * <pre>{@code
   * var workflowProxy = Restate.workflow(OrderWorkflow.class, "order-123");
   * workflowProxy.start(new OrderRequest(...));
   * }</pre>
   *
   * <p>For advanced use cases requiring asynchronous request handling, composable futures, or
   * invocation options (such as idempotency keys), use {@link #workflowHandle(Class, String)}
   * instead.
   *
   * @param clazz the workflow class annotated with {@link Workflow}
   * @param key the key identifying the specific workflow instance
   * @return a proxy client to invoke the workflow
   */
  public static <SVC> SVC workflow(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveWorkflowAnnotation(clazz);
    String serviceName = ReflectionUtils.extractServiceName(clazz);
    return ProxySupport.createProxy(
        clazz,
        invocation -> {
          var methodInfo = MethodInfo.fromMethod(invocation.getMethod());

          //noinspection unchecked
          return Context.current()
              .call(
                  Request.of(
                      Target.virtualObject(serviceName, key, methodInfo.getHandlerName()),
                      (TypeTag<? super Object>) methodInfo.getInputType(),
                      (TypeTag<? super Object>) methodInfo.getOutputType(),
                      invocation.getArguments().length == 0 ? null : invocation.getArguments()[0]))
              .await();
        });
  }

  /**
   * Advanced API to invoke a Restate Workflow with full control.
   *
   * <p>Create a handle that provides advanced invocation capabilities including:
   *
   * <ul>
   *   <li>Composable futures for asynchronous request handling
   *   <li>Invocation options such as {@link
   *       dev.restate.common.InvocationOptions#idempotencyKey(String)}
   *   <li>Fire-and-forget requests via {@code send()}
   *   <li>Deferred response handling
   * </ul>
   *
   * <pre>{@code
   * // 1. Use call() with method reference and await the result
   * Restate.workflowHandle(OrderWorkflow.class, "order-123")
   *   .call(OrderWorkflow::start, new OrderRequest(...))
   *   .await();
   *
   * // 2. Use send() for one-way invocation without waiting
   * InvocationHandle<Void> handle = Restate.workflowHandle(OrderWorkflow.class, "order-123")
   *   .send(OrderWorkflow::start, new OrderRequest(...));
   * }</pre>
   *
   * <p>For simple synchronous request-response interactions, consider using {@link #workflow(Class,
   * String)} instead.
   *
   * @param clazz the workflow class annotated with {@link Workflow}
   * @param key the key identifying the specific workflow instance
   * @return a handle to invoke the workflow with advanced options
   */
  public static <SVC> ServiceHandle<SVC> workflowHandle(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveWorkflowAnnotation(clazz);
    return new ServiceHandleImpl<>(clazz, key);
  }

  /**
   * <b>PREVIEW:</b> Returns a {@link Scope} that routes all outgoing calls within the given scope.
   *
   * <p><b>NOTE:</b> This API is in preview and is not enabled by default. To use it in
   * restate-server 1.7, enable the flow control and protocol v7 experimental features, via {@code
   * RESTATE_EXPERIMENTAL_ENABLE_PROTOCOL_V7=true} and {@code
   * RESTATE_EXPERIMENTAL_ENABLE_VQUEUES=true}. These can be enabled only on <b>new clusters</b>,
   * for more info check out https://docs.restate.dev/services/flow-control#enabling-flow-control.
   * If these experimental features aren't enabled, the call fails with a retryable error and keeps
   * retrying until they are.
   *
   * <p>A scope is a sub-grouping of resources (invocations, virtual object instances, workflow
   * instances, concurrency limits) within the Restate cluster. It becomes part of the target
   * identity tuple:
   *
   * <ul>
   *   <li>{@code scope, service, handler, idempotencyKey?}
   *   <li>{@code scope, virtualObject, objectKey, handler, idempotencyKey?}
   *   <li>{@code scope, workflow, workflowKey, handler}
   * </ul>
   *
   * <p>Under the hood, the scope contributes to the partition key, so all resources in a scope get
   * co-located by the restate-server.
   *
   * <p>Omitting the scope (i.e. using the regular {@link #service(Class)} / {@link #workflow(Class,
   * String)} methods) is equivalent to calling with no scope, which is the existing behavior.
   *
   * <p>The scope key must consist only of {@code [a-zA-Z0-9_.-]} characters, with {@code 1 <=
   * length <= 36} chars.
   *
   * <pre>{@code
   * // Route a call into a named scope
   * Restate.scope("tenant-123").service(MyService.class).process(payload);
   *
   * // Idempotency keys are scoped — "req-1" in "tenant-123" is distinct from "req-1" in "tenant-456"
   * Restate.scope("tenant-123").serviceHandle(MyService.class)
   *     .call(MyService::process, payload, InvocationOptions.idempotencyKey("req-1").build())
   *     .await();
   *
   * // Combine with a limit key to enforce per-scope concurrency limits
   * Restate.scope("tenant-123").workflowHandle(MyWorkflow.class, "wf-key")
   *     .call(MyWorkflow::run, input, InvocationOptions.limitKey("api-key/user42").build())
   *     .await();
   * }</pre>
   *
   * @param scopeKey the scope identifier
   * @return a {@link Scope}
   * @see <a
   *     href="https://docs.restate.dev/services/flow-control">https://docs.restate.dev/services/flow-control</a>
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static Scope scope(String scopeKey) {
    return new Scope(scopeKey);
  }

  /** Interface to interact with this Virtual Object/Workflow state. */
  public interface State {

    /**
     * Gets the state stored under key, deserializing the raw value using the {@link Serde} in the
     * {@link StateKey}.
     *
     * @param key identifying the state to get and its type.
     * @return an {@link Optional} containing the stored state deserialized or an empty {@link
     *     Optional} if not set yet.
     * @throws RuntimeException when the state cannot be deserialized.
     */
    <T> Optional<T> get(StateKey<T> key);

    /**
     * Sets the given value under the given key, serializing the value using the {@link Serde} in
     * the {@link StateKey}.
     *
     * @param key identifying the value to store and its type.
     * @param value to store under the given key. MUST NOT be null.
     * @throws IllegalStateException if called from a Shared handler
     */
    <T> void set(StateKey<T> key, @NonNull T value);

    /**
     * Clears the state stored under key.
     *
     * @param key identifying the state to clear.
     * @throws IllegalStateException if called from a Shared handler
     */
    void clear(StateKey<?> key);

    /**
     * Gets all the known state keys for this virtual object instance.
     *
     * @return the immutable collection of known state keys.
     */
    Collection<String> getAllKeys();

    /**
     * Clears all the state of this virtual object instance key-value state storage
     *
     * @throws IllegalStateException if called from a Shared handler
     */
    void clearAll();
  }

  private static final State STATE_INSTANCE =
      new State() {
        @Override
        public <T> Optional<T> get(StateKey<T> key) {
          return ((SharedObjectContext) Context.current()).get(key);
        }

        @Override
        public <T> void set(StateKey<T> key, @NonNull T value) {
          checkCanWriteState("set");
          ((ObjectContext) Context.current()).set(key, value);
        }

        @Override
        public void clear(StateKey<?> key) {
          checkCanWriteState("clear");
          ((ObjectContext) Context.current()).clear(key);
        }

        @Override
        public Collection<String> getAllKeys() {
          return ((SharedObjectContext) Context.current()).stateKeys();
        }

        @Override
        public void clearAll() {
          checkCanWriteState("clearAll");
          ((ObjectContext) Context.current()).clearAll();
        }

        private void checkCanWriteState(String opName) {
          var handlerContext = HandlerRunner.getHandlerContext();
          if (!handlerContext.canWriteState()) {
            throw new IllegalStateException(
                "State."
                    + opName
                    + "() cannot be used in shared handlers. Check https://docs.restate.dev/develop/java/state for more details.");
          }
        }
      };

  /**
   * @return this Virtual Object/Workflow key
   * @throws IllegalStateException if called from a regular Service handler.
   */
  public static String key() {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (!handlerContext.canReadState()) {
      throw new IllegalStateException(
          "Restate.key() can be used only within Virtual Object or Workflow handlers. Check https://docs.restate.dev/develop/java/state for more details.");
    }

    return ((SharedObjectContext) Context.current()).key();
  }

  /**
   * Access to this Virtual Object/Workflow state.
   *
   * @return {@link State} for this Virtual Object/Workflow
   * @throws IllegalStateException if called from a regular Service handler.
   */
  public static State state() {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (!handlerContext.canReadState()) {
      throw new IllegalStateException(
          "Restate.state() can be used only within Virtual Object or Workflow handlers. Check https://docs.restate.dev/develop/java/state for more details.");
    }
    return STATE_INSTANCE;
  }

  /**
   * Create a {@link DurablePromise} for the given key.
   *
   * <p>You can use this feature to implement interaction between different workflow handlers, e.g.
   * to send a signal from a shared handler to the workflow handler.
   *
   * @return the {@link DurablePromise}.
   * @see DurablePromise
   * @throws IllegalStateException if called from a non-Workflow handler
   */
  public static <T> DurablePromise<T> promise(DurablePromiseKey<T> key) {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (!handlerContext.canReadPromises() || !handlerContext.canWritePromises()) {
      throw new IllegalStateException(
          "Restate.promise(key) can be used only within Workflow handlers. Check https://docs.restate.dev/develop/java/external-events#durable-promises for more details.");
    }

    SharedWorkflowContext ctx = (SharedWorkflowContext) Context.current();
    return ctx.promise(key);
  }

  /**
   * Create a new {@link DurablePromiseHandle} for the provided key. You can use it to {@link
   * DurablePromiseHandle#resolve(Object)} or {@link DurablePromiseHandle#reject(String)} the given
   * {@link DurablePromise}.
   *
   * @see DurablePromise
   * @throws IllegalStateException if called from a non-Workflow handler
   */
  public static <T> DurablePromiseHandle<T> promiseHandle(DurablePromiseKey<T> key) {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (!handlerContext.canReadPromises() || !handlerContext.canWritePromises()) {
      throw new IllegalStateException(
          "Restate.promiseHandle(key) can be used only within Workflow handlers. Check https://docs.restate.dev/develop/java/external-events#durable-promises for more details.");
    }

    SharedWorkflowContext ctx = (SharedWorkflowContext) Context.current();
    return ctx.promiseHandle(key);
  }
}
