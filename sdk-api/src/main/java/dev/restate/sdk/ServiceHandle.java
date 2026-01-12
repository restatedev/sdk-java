// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.InvocationOptions;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <b>EXPERIMENTAL API:</b> This interface is part of the new reflection-based API and may change in
 * future releases.
 *
 * <p>Advanced API handle for invoking Restate services, virtual objects, or workflows with full
 * control. This handle provides advanced invocation capabilities including:
 *
 * <ul>
 *   <li>Composable futures for asynchronous request handling
 *   <li>Invocation options such as idempotency keys
 *   <li>Fire-and-forget requests via {@code send()}
 *   <li>Deferred response handling
 * </ul>
 *
 * <p>Use this handle to perform requests with method references:
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
 * <p>Create instances using {@link Restate#serviceHandle(Class)}, {@link
 * Restate#virtualObjectHandle(Class, String)}, or {@link Restate#workflowHandle(Class, String)}.
 *
 * <p>For simple synchronous request-response interactions, consider using the simple proxy API
 * instead: {@link Restate#service(Class)}, {@link Restate#virtualObject(Class, String)}, or {@link
 * Restate#workflow(Class, String)}.
 *
 * @param <SVC> the service interface type
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public interface ServiceHandle<SVC> {
  /**
   * <b>EXPERIMENTAL API:</b> Invoke a service method with input and return a future for the result.
   *
   * <pre>{@code
   * // Call with method reference and input
   * GreetingResponse response = Restate.service(Greeter.class)
   *   .call(Greeter::greet, new Greeting("Alice"))
   *   .await();
   * }</pre>
   *
   * @param methodReference method reference (e.g., {@code Greeter::greet})
   * @param input the input parameter to pass to the method
   * @return a {@link DurableFuture} wrapping the result
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> DurableFuture<O> call(BiFunction<SVC, I, O> methodReference, I input) {
    return call(methodReference, input, InvocationOptions.DEFAULT);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #call(BiFunction, Object)}, with invocation options.
   *
   * <pre>{@code
   * // Call with custom options
   * var options = InvocationOptions.builder()
   *   .idempotencyKey("unique-key")
   *   .build();
   * GreetingResponse response = Restate.service(Greeter.class)
   *   .call(Greeter::greet, new Greeting("Alice"), options)
   *   .await();
   * }</pre>
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> DurableFuture<O> call(
      BiFunction<SVC, I, O> methodReference, I input, InvocationOptions.Builder options) {
    return call(methodReference, input, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(BiFunction, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <I, O> DurableFuture<O> call(
      BiFunction<SVC, I, O> methodReference, I input, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #call(BiFunction, Object)}, for methods without a return
   * value.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> DurableFuture<Void> call(BiConsumer<SVC, I> methodReference, I input) {
    return call(methodReference, input, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(BiConsumer, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> DurableFuture<Void> call(
      BiConsumer<SVC, I> methodReference, I input, InvocationOptions.Builder options) {
    return call(methodReference, input, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(BiConsumer, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <I> DurableFuture<Void> call(
      BiConsumer<SVC, I> methodReference, I input, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Invoke a service method without input and return a future for the
   * result.
   *
   * <pre>{@code
   * // Call method without input
   * int count = Restate.virtualObject(Counter.class, "my-counter")
   *   .call(Counter::get)
   *   .await();
   * }</pre>
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> DurableFuture<O> call(Function<SVC, O> methodReference) {
    return call(methodReference, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(Function)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> DurableFuture<O> call(
      Function<SVC, O> methodReference, InvocationOptions.Builder options) {
    return call(methodReference, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(Function)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <O> DurableFuture<O> call(Function<SVC, O> methodReference, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #call(BiFunction, Object)}, for methods without input or
   * return value.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default DurableFuture<Void> call(Consumer<SVC> methodReference) {
    return call(methodReference, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(Consumer)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default DurableFuture<Void> call(
      Consumer<SVC> methodReference, InvocationOptions.Builder options) {
    return call(methodReference, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(Consumer)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  DurableFuture<Void> call(Consumer<SVC> methodReference, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Send a one-way invocation without waiting for the response.
   *
   * <pre>{@code
   * // Send without waiting for response
   * InvocationHandle<GreetingResponse> handle = Restate.service(Greeter.class)
   *   .send(Greeter::greet, new Greeting("Alice"));
   * String invocationId = handle.invocationId();
   *
   * // Send with a delay
   * InvocationHandle<GreetingResponse> handle = Restate.service(Greeter.class)
   *   .send(Greeter::greet, new Greeting("Alice"), Duration.ofMinutes(5));
   * }</pre>
   *
   * @param methodReference method reference (e.g., {@code Greeter::greet})
   * @param input the input parameter to pass to the method
   * @return an {@link InvocationHandle} for the invocation
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(BiFunction<SVC, I, O> methodReference, I input) {
    return send(methodReference, input, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> methodReference, I input, InvocationOptions.Builder options) {
    return send(methodReference, input, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> methodReference, I input, InvocationOptions options) {
    return send(methodReference, input, null, options);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with a delay. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> methodReference, I input, Duration delay) {
    return send(methodReference, input, delay, InvocationOptions.DEFAULT);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with a delay and invocation
   * options.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> methodReference,
      I input,
      Duration delay,
      InvocationOptions.Builder options) {
    return send(methodReference, input, delay, options.build());
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with a delay and invocation
   * options.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> methodReference, I input, Duration delay, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, for methods without a return
   * value.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(BiConsumer<SVC, I> methodReference, I input) {
    return send(methodReference, input, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> methodReference, I input, InvocationOptions.Builder options) {
    return send(methodReference, input, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> methodReference, I input, InvocationOptions options) {
    return send(methodReference, input, null, options);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with a delay. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> methodReference, I input, Duration delay) {
    return send(methodReference, input, delay, InvocationOptions.DEFAULT);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with a delay and invocation
   * options.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> methodReference,
      I input,
      Duration delay,
      InvocationOptions.Builder options) {
    return send(methodReference, input, delay, options.build());
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with a delay and invocation
   * options.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> methodReference, I input, Duration delay, InvocationOptions options);

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, for methods without input. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(Function<SVC, O> s) {
    return send(s, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Function)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(
      Function<SVC, O> methodReference, InvocationOptions.Builder options) {
    return send(methodReference, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Function)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(Function<SVC, O> s, InvocationOptions options) {
    return send(s, null, options);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Function)}, with a delay. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(Function<SVC, O> s, Duration delay) {
    return send(s, delay, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Function)}, with a delay and invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(
      Function<SVC, O> methodReference, Duration delay, InvocationOptions.Builder options) {
    return send(methodReference, delay, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Function)}, with a delay and invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <O> InvocationHandle<O> send(
      Function<SVC, O> methodReference, Duration delay, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, for methods without input or
   * return value.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> methodReference) {
    return send(methodReference, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(
      Consumer<SVC> methodReference, InvocationOptions.Builder options) {
    return send(methodReference, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> methodReference, InvocationOptions options) {
    return send(methodReference, null, options);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with a delay. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> methodReference, Duration delay) {
    return send(methodReference, delay, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with a delay and invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(
      Consumer<SVC> methodReference, Duration delay, InvocationOptions.Builder options) {
    return send(methodReference, delay, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with a delay and invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  InvocationHandle<Void> send(
      Consumer<SVC> methodReference, Duration delay, InvocationOptions options);
}
