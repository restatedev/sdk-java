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
 * <b>EXPERIMENTAL API:</b> This interface is part of the new reflection-based API and may change
 * in future releases.
 *
 * <p>A reference to a Restate service, virtual object, or workflow that can be invoked from within
 * a handler. Provides three ways to invoke methods:
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
 * <p>Create instances using {@link Restate#service(Class)}, {@link
 * Restate#virtualObject(Class, String)}, or {@link Restate#workflow(Class, String)}.
 *
 * @param <SVC> the service interface type
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public interface ServiceReference<SVC> {
  /**
   * <b>EXPERIMENTAL API:</b> Get a client proxy to call methods directly.
   *
   * <pre>{@code
   * // Get a proxy and call methods on it
   * var greeterProxy = Restate.service(Greeter.class).client();
   * GreetingResponse response = greeterProxy.greet(new Greeting("Alice"));
   * }</pre>
   *
   * @return a proxy instance of the service interface
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  SVC client();

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
   * @param s method reference (e.g., {@code Greeter::greet})
   * @param input the input parameter to pass to the method
   * @return a {@link DurableFuture} wrapping the result
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> DurableFuture<O> call(BiFunction<SVC, I, O> s, I input) {
    return call(s, input, InvocationOptions.DEFAULT);
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
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return call(s, input, options.build());
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #call(BiFunction, Object)}, with invocation options.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <I, O> DurableFuture<O> call(BiFunction<SVC, I, O> s, I input, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #call(BiFunction, Object)}, for methods without a return
   * value.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> DurableFuture<Void> call(BiConsumer<SVC, I> s, I input) {
    return call(s, input, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(BiConsumer, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> DurableFuture<Void> call(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return call(s, input, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(BiConsumer, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <I> DurableFuture<Void> call(BiConsumer<SVC, I> s, I input, InvocationOptions options);

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
  default <O> DurableFuture<O> call(Function<SVC, O> s) {
    return call(s, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(Function)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> DurableFuture<O> call(Function<SVC, O> s, InvocationOptions.Builder options) {
    return call(s, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(Function)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <O> DurableFuture<O> call(Function<SVC, O> s, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #call(BiFunction, Object)}, for methods without input or
   * return value.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default DurableFuture<Void> call(Consumer<SVC> s) {
    return call(s, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(Consumer)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default DurableFuture<Void> call(Consumer<SVC> s, InvocationOptions.Builder options) {
    return call(s, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #call(Consumer)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  DurableFuture<Void> call(Consumer<SVC> s, InvocationOptions options);

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
   * @param s method reference (e.g., {@code Greeter::greet})
   * @param input the input parameter to pass to the method
   * @return an {@link InvocationHandle} for the invocation
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(BiFunction<SVC, I, O> s, I input) {
    return send(s, input, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return send(s, input, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> s, I input, InvocationOptions options) {
    return send(s, input, null, options);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with a delay. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(BiFunction<SVC, I, O> s, I input, Duration delay) {
    return send(s, input, delay, InvocationOptions.DEFAULT);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with a delay and invocation
   * options.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions.Builder options) {
    return send(s, input, delay, options.build());
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, with a delay and invocation
   * options.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, for methods without a return
   * value.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(BiConsumer<SVC, I> s, I input) {
    return send(s, input, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return send(s, input, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> s, I input, InvocationOptions options) {
    return send(s, input, null, options);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with a delay. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(BiConsumer<SVC, I> s, I input, Duration delay) {
    return send(s, input, delay, InvocationOptions.DEFAULT);
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with a delay and invocation
   * options.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions.Builder options) {
    return send(s, input, delay, options.build());
  }

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiConsumer, Object)}, with a delay and invocation
   * options.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, for methods without input.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(Function<SVC, O> s) {
    return send(s, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Function)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(Function<SVC, O> s, InvocationOptions.Builder options) {
    return send(s, options.build());
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
      Function<SVC, O> s, Duration delay, InvocationOptions.Builder options) {
    return send(s, delay, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Function)}, with a delay and invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  <O> InvocationHandle<O> send(Function<SVC, O> s, Duration delay, InvocationOptions options);

  /**
   * <b>EXPERIMENTAL API:</b> Like {@link #send(BiFunction, Object)}, for methods without input or
   * return value.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> s) {
    return send(s, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> s, InvocationOptions.Builder options) {
    return send(s, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> s, InvocationOptions options) {
    return send(s, null, options);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with a delay. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> s, Duration delay) {
    return send(s, delay, InvocationOptions.DEFAULT);
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with a delay and invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(
      Consumer<SVC> s, Duration delay, InvocationOptions.Builder options) {
    return send(s, delay, options.build());
  }

  /** <b>EXPERIMENTAL API:</b> Like {@link #send(Consumer)}, with a delay and invocation options. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  InvocationHandle<Void> send(Consumer<SVC> s, Duration delay, InvocationOptions options);
}
