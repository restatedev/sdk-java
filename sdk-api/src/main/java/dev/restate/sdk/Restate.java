// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.common.reflections.ReflectionUtils.mustHaveAnnotation;

import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingRunnable;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.annotation.Workflow;
import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.HandlerRequest;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.internal.ContextThreadLocal;
import dev.restate.serde.TypeTag;
import java.time.Duration;

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
@org.jetbrains.annotations.ApiStatus.Experimental
public final class Restate {
  /**
   * Get the base {@link Context} for the current handler invocation.
   *
   * <p>This method is safe to call from any Restate handler (Service, Virtual Object, or Workflow).
   *
   * <p>For handlers requiring access to state or promises, prefer using the specialized context
   * getters: {@link #objectContext()}, {@link #sharedObjectContext()}, {@link #workflowContext()},
   * or {@link #sharedWorkflowContext()}.
   *
   * @return the current context
   * @throws IllegalStateException if called outside a Restate handler
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static Context context() {
    return ContextThreadLocal.getContext();
  }

  /**
   * Get the {@link ObjectContext} for the current Virtual Object handler invocation.
   *
   * <p>This context provides access to read and write state operations for Virtual Objects. It is
   * safe to call this method only from exclusive Virtual Object handlers (non-shared handlers).
   *
   * @return the current object context
   * @throws IllegalStateException if called from a shared Virtual Object handler (use {@link
   *     #sharedObjectContext()} instead) or from a non-Virtual Object handler
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static ObjectContext objectContext() {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (handlerContext.canReadState() && handlerContext.canWriteState()) {
      return (ObjectContext) context();
    }
    if (handlerContext.canReadState()) {
      throw new IllegalStateException(
          "Calling objectContext() from a Virtual object shared handler. You must use Restate.sharedObjectContext() instead.");
    }

    throw new IllegalStateException(
        "Calling objectContext() from a non Virtual object handler. You can use Restate.objectContext() only inside a Restate Virtual Object handler.");
  }

  /**
   * Get the {@link SharedObjectContext} for the current Virtual Object shared handler invocation.
   *
   * <p>This context provides read-only access to state operations for Virtual Objects. It is safe
   * to call this method from shared Virtual Object handlers that need to read state but not modify
   * it.
   *
   * @return the current shared object context
   * @throws IllegalStateException if called from a non-Virtual Object handler
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static SharedObjectContext sharedObjectContext() {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (handlerContext.canReadState()) {
      return (SharedObjectContext) context();
    }

    throw new IllegalStateException(
        "Calling objectContext() from a non Virtual object handler. You can use Restate.objectContext() only inside a Restate Virtual Object handler.");
  }

  /**
   * Get the {@link WorkflowContext} for the current Workflow handler invocation.
   *
   * <p>This context provides access to read and write promise operations for Workflows. It is safe
   * to call this method only from exclusive Workflow handlers (non-shared handlers).
   *
   * @return the current workflow context
   * @throws IllegalStateException if called from a shared Workflow handler (use {@link
   *     #sharedWorkflowContext()} instead) or from a non-Workflow handler
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static WorkflowContext workflowContext() {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (handlerContext.canReadPromises() && handlerContext.canWritePromises()) {
      return (WorkflowContext) context();
    }
    if (handlerContext.canReadPromises()) {
      throw new IllegalStateException(
          "Calling workflowContext() from a Workflow shared handler. You must use Restate.sharedWorkflowContext() instead.");
    }

    throw new IllegalStateException(
        "Calling workflowContext() from a non Workflow handler. You can use Restate.workflowContext() only inside a Restate Workflow handler.");
  }

  /**
   * Get the {@link SharedWorkflowContext} for the current Workflow shared handler invocation.
   *
   * <p>This context provides read-only access to promise operations for Workflows. It is safe to
   * call this method from shared Workflow handlers that need to read promises but not modify them.
   *
   * @return the current shared workflow context
   * @throws IllegalStateException if called from a non-Workflow handler
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static SharedWorkflowContext sharedWorkflowContext() {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (handlerContext.canReadPromises()) {
      return (SharedWorkflowContext) context();
    }

    throw new IllegalStateException(
        "Calling workflowContext() from a non Workflow handler. You can use Restate.workflowContext() only inside a Restate Workflow handler.");
  }

  /**
   * Check if the current code is executing inside a Restate handler.
   *
   * @return true if currently inside a handler, false otherwise
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static boolean isInsideHandler() {
    return ContextThreadLocal.CONTEXT_THREAD_LOCAL.get() != null;
  }

  /** @see Context#request() */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static HandlerRequest request() {
    return context().request();
  }

  /**
   * Returns a deterministic random.
   *
   * @see RestateRandom
   * @see Context#random()
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static RestateRandom random() {
    return context().random();
  }

  /** @see Context#invocationHandle(String, TypeTag) */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <R> InvocationHandle<R> invocationHandle(
      String invocationId, TypeTag<R> responseTypeTag) {
    return context().invocationHandle(invocationId, responseTypeTag);
  }

  /**
   * Get an {@link InvocationHandle} for an already existing invocation. This will let you interact
   * with a running invocation, for example to cancel it or retrieve its result.
   *
   * @param invocationId The invocation to interact with.
   * @param responseClazz The response class.
   * @see Context#invocationHandle(String, Class)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <R> InvocationHandle<R> invocationHandle(
      String invocationId, Class<R> responseClazz) {
    return context().invocationHandle(invocationId, responseClazz);
  }

  /**
   * Like {@link #invocationHandle(String, Class)}, without providing a response parser
   *
   * @see Context#invocationHandle(String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static InvocationHandle<Slice> invocationHandle(String invocationId) {
    return context().invocationHandle(invocationId);
  }

  /**
   * Causes the current execution of the function invocation to sleep for the given duration.
   *
   * @param duration for which to sleep.
   * @see Context#sleep(Duration)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static void sleep(Duration duration) {
    context().sleep(duration);
  }

  /**
   * Causes the start of a timer for the given duration. You can await on the timer end by invoking
   * {@link DurableFuture#await()}.
   *
   * @param name name used for observability
   * @param duration for which to sleep.
   * @see Context#timer(String, Duration)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static DurableFuture<Void> timer(String name, Duration duration) {
    return context().timer(name, duration);
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(String name, Class<T> clazz, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().run(name, clazz, action);
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().run(name, typeTag, retryPolicy, action);
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().run(name, clazz, retryPolicy, action);
  }

  /**
   * Like {@link #run(String, Class, ThrowingSupplier)}, but providing a {@link TypeTag}.
   *
   * <p>See {@link Context} for more details about serialization and deserialization.
   *
   * @see #run(String, Class, ThrowingSupplier)
   * @see Context#run(String, TypeTag, ThrowingSupplier)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(String name, TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().run(name, typeTag, action);
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static void run(String name, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    context().run(name, retryPolicy, runnable);
  }

  /**
   * Like {@link #run(String, Class, ThrowingSupplier)} without output.
   *
   * @see #run(String, Class, ThrowingSupplier)
   * @see Context#run(String, ThrowingRunnable)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static void run(String name, ThrowingRunnable runnable) throws TerminalException {
    context().run(name, runnable);
  }

  /**
   * Execute a closure asynchronously. This is like {@link #run(String, Class, ThrowingSupplier)},
   * but it returns a {@link DurableFuture} that you can combine and select.
   *
   * @see #run(String, Class, ThrowingSupplier)
   * @see Context#runAsync(String, Class, ThrowingSupplier)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, Class<T> clazz, ThrowingSupplier<T> action) throws TerminalException {
    return context().runAsync(name, clazz, action);
  }

  /**
   * Like {@link #runAsync(String, Class, ThrowingSupplier)}, but providing a {@link TypeTag}.
   *
   * <p>See {@link Context} for more details about serialization and deserialization.
   *
   * @see #runAsync(String, Class, ThrowingSupplier)
   * @see Context#runAsync(String, TypeTag, ThrowingSupplier)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, ThrowingSupplier<T> action) throws TerminalException {
    return context().runAsync(name, typeTag, action);
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().runAsync(name, clazz, retryPolicy, action);
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().runAsync(name, typeTag, retryPolicy, action);
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static DurableFuture<Void> runAsync(
      String name, RetryPolicy retryPolicy, ThrowingRunnable runnable) throws TerminalException {
    return context().runAsync(name, retryPolicy, runnable);
  }

  /**
   * Like {@link #runAsync(String, Class, ThrowingSupplier)} without output.
   *
   * @see Context#runAsync(String, ThrowingRunnable)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static DurableFuture<Void> runAsync(String name, ThrowingRunnable runnable)
      throws TerminalException {
    return context().runAsync(name, runnable);
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> Awakeable<T> awakeable(Class<T> clazz) {
    return context().awakeable(clazz);
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> Awakeable<T> awakeable(TypeTag<T> typeTag) {
    return context().awakeable(typeTag);
  }

  /**
   * Create a new {@link AwakeableHandle} for the provided identifier. You can use it to {@link
   * AwakeableHandle#resolve(TypeTag, Object)} or {@link AwakeableHandle#reject(String)} the linked
   * {@link Awakeable}.
   *
   * @see Awakeable
   * @see Context#awakeableHandle(String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static AwakeableHandle awakeableHandle(String id) {
    return context().awakeableHandle(id);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Create a reference to invoke a Restate service.
   *
   * <p>You can invoke the service in three ways:
   *
   * <pre>{@code
   * // 1. Create a client proxy and call it directly
   * var greeterProxy = Restate.service(Greeter.class).client();
   * GreetingResponse response = greeterProxy.greet(new Greeting("Alice"));
   *
   * // 2. Use call() with method reference and await the result
   * GreetingResponse response = Restate.service(Greeter.class)
   *   .call(Greeter::greet, new Greeting("Alice"))
   *   .await();
   *
   * // 3. Use send() for one-way invocation without waiting
   * InvocationHandle<GreetingResponse> handle = Restate.service(Greeter.class)
   *   .send(Greeter::greet, new Greeting("Alice"));
   * }</pre>
   *
   * @param clazz the service class annotated with {@link Service}
   * @return a reference to invoke the service
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <SVC> ServiceReference<SVC> service(Class<SVC> clazz) {
    mustHaveAnnotation(clazz, Service.class);
    return new ServiceReferenceImpl<>(clazz, null);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Create a reference to invoke a Restate Virtual Object.
   *
   * <p>You can invoke the virtual object in three ways:
   *
   * <pre>{@code
   * // 1. Create a client proxy and call it directly
   * var counterProxy = Restate.virtualObject(Counter.class, "my-counter").client();
   * int count = counterProxy.increment();
   *
   * // 2. Use call() with method reference and await the result
   * int count = Restate.virtualObject(Counter.class, "my-counter")
   *   .call(Counter::increment)
   *   .await();
   *
   * // 3. Use send() for one-way invocation without waiting
   * InvocationHandle<Integer> handle = Restate.virtualObject(Counter.class, "my-counter")
   *   .send(Counter::increment);
   * }</pre>
   *
   * @param clazz the virtual object class annotated with {@link VirtualObject}
   * @param key the key identifying the specific virtual object instance
   * @return a reference to invoke the virtual object
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <SVC> ServiceReference<SVC> virtualObject(Class<SVC> clazz, String key) {
    mustHaveAnnotation(clazz, VirtualObject.class);
    return new ServiceReferenceImpl<>(clazz, key);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Create a reference to invoke a Restate Workflow.
   *
   * <p>You can invoke the workflow in three ways:
   *
   * <pre>{@code
   * // 1. Create a client proxy and call it directly
   * var workflowProxy = Restate.workflow(OrderWorkflow.class, "order-123").client();
   * workflowProxy.start(new OrderRequest(...));
   *
   * // 2. Use call() with method reference and await the result
   * Restate.workflow(OrderWorkflow.class, "order-123")
   *   .call(OrderWorkflow::start, new OrderRequest(...))
   *   .await();
   *
   * // 3. Use send() for one-way invocation without waiting
   * InvocationHandle<Void> handle = Restate.workflow(OrderWorkflow.class, "order-123")
   *   .send(OrderWorkflow::start, new OrderRequest(...));
   * }</pre>
   *
   * @param clazz the workflow class annotated with {@link Workflow}
   * @param key the key identifying the specific workflow instance
   * @return a reference to invoke the workflow
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <SVC> ServiceReference<SVC> workflow(Class<SVC> clazz, String key) {
    mustHaveAnnotation(clazz, Workflow.class);
    return new ServiceReferenceImpl<>(clazz, key);
  }
}
