// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import dev.restate.common.InvocationOptions;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Advanced API handle for invoking Restate services, virtual objects, or workflows from the ingress
 * (outside of a handler). This handle provides advanced invocation capabilities including:
 *
 * <ul>
 *   <li>Async request handling with {@link CompletableFuture}
 *   <li>Invocation options such as idempotency keys
 *   <li>Fire-and-forget requests via {@code send()}
 *   <li>Access to full {@link Response} metadata
 * </ul>
 *
 * <p>Use this handle to perform requests with method references:
 *
 * <pre>{@code
 * Client client = Client.connect("http://localhost:8080");
 *
 * // 1. Use call() with method reference and wait for the result
 * Response<GreetingResponse> response = client.toService(Greeter.class)
 *   .call(Greeter::greet, new Greeting("Alice"));
 *
 * // 2. Use send() for one-way invocation without waiting
 * SendResponse<GreetingResponse> sendResponse = client.toService(Greeter.class)
 *   .send(Greeter::greet, new Greeting("Alice"));
 * }</pre>
 *
 * <p>Create instances using {@link Client#toService(Class)}, {@link Client#toVirtualObject(Class,
 * String)}, or {@link Client#toWorkflow(Class, String)}.
 *
 * <p>For simple synchronous request-response interactions returning just the output, consider using
 * the simple proxy API instead: {@link Client#service(Class)}, {@link Client#virtualObject(Class,
 * String)}, or {@link Client#workflow(Class, String)}.
 *
 * @param <SVC> the service interface type
 */
public interface ClientServiceHandle<SVC> {
  /**
   * Invoke a service method with input and wait for the response.
   *
   * <pre>{@code
   * // Call with method reference and input
   * Response<GreetingResponse> response = client.service(Greeter.class)
   *   .call(Greeter::greet, new Greeting("Alice"));
   * }</pre>
   *
   * @param s method reference (e.g., {@code Greeter::greet})
   * @param input the input parameter to pass to the method
   * @return a {@link Response} wrapping the result
   */
  default <I, O> Response<O> call(BiFunction<SVC, I, O> s, I input) {
    return call(s, input, InvocationOptions.DEFAULT);
  }

  default <I, O> Response<O> call(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return call(s, input, options.build());
  }

  default <I, O> Response<O> call(
      BiFunction<SVC, I, O> s, I input, InvocationOptions invocationOptions) {
    try {
      return callAsync(s, input, invocationOptions).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  // call - BiConsumer variants
  default <I> Response<Void> call(BiConsumer<SVC, I> s, I input) {
    return call(s, input, InvocationOptions.DEFAULT);
  }

  default <I> Response<Void> call(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return call(s, input, options.build());
  }

  default <I> Response<Void> call(
      BiConsumer<SVC, I> s, I input, InvocationOptions invocationOptions) {
    try {
      return callAsync(s, input, invocationOptions).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  // call - Function variants
  default <O> Response<O> call(Function<SVC, O> s) {
    return call(s, InvocationOptions.DEFAULT);
  }

  default <O> Response<O> call(Function<SVC, O> s, InvocationOptions.Builder options) {
    return call(s, options.build());
  }

  default <O> Response<O> call(Function<SVC, O> s, InvocationOptions invocationOptions) {
    try {
      return callAsync(s, invocationOptions).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  // call - Consumer variants
  default Response<Void> call(Consumer<SVC> s) {
    return call(s, InvocationOptions.DEFAULT);
  }

  default Response<Void> call(Consumer<SVC> s, InvocationOptions.Builder options) {
    return call(s, options.build());
  }

  default Response<Void> call(Consumer<SVC> s, InvocationOptions invocationOptions) {
    try {
      return callAsync(s, invocationOptions).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  // callAsync - BiFunction variants
  default <I, O> CompletableFuture<Response<O>> callAsync(BiFunction<SVC, I, O> s, I input) {
    return callAsync(s, input, InvocationOptions.DEFAULT);
  }

  default <I, O> CompletableFuture<Response<O>> callAsync(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return callAsync(s, input, options.build());
  }

  <I, O> CompletableFuture<Response<O>> callAsync(
      BiFunction<SVC, I, O> s, I input, InvocationOptions invocationOptions);

  // callAsync - BiConsumer variants
  default <I> CompletableFuture<Response<Void>> callAsync(BiConsumer<SVC, I> s, I input) {
    return callAsync(s, input, InvocationOptions.DEFAULT);
  }

  default <I> CompletableFuture<Response<Void>> callAsync(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return callAsync(s, input, options.build());
  }

  <I> CompletableFuture<Response<Void>> callAsync(
      BiConsumer<SVC, I> s, I input, InvocationOptions invocationOptions);

  // callAsync - Function variants
  default <O> CompletableFuture<Response<O>> callAsync(Function<SVC, O> s) {
    return callAsync(s, InvocationOptions.DEFAULT);
  }

  default <O> CompletableFuture<Response<O>> callAsync(
      Function<SVC, O> s, InvocationOptions.Builder options) {
    return callAsync(s, options.build());
  }

  <O> CompletableFuture<Response<O>> callAsync(
      Function<SVC, O> s, InvocationOptions invocationOptions);

  // callAsync - Consumer variants
  default CompletableFuture<Response<Void>> callAsync(Consumer<SVC> s) {
    return callAsync(s, InvocationOptions.DEFAULT);
  }

  default CompletableFuture<Response<Void>> callAsync(
      Consumer<SVC> s, InvocationOptions.Builder options) {
    return callAsync(s, options.build());
  }

  CompletableFuture<Response<Void>> callAsync(Consumer<SVC> s, InvocationOptions invocationOptions);

  // send - BiFunction variants
  /**
   * Send a one-way invocation without waiting for the response.
   *
   * <pre>{@code
   * // Send without waiting for response
   * SendResponse<GreetingResponse> sendResponse = client.service(Greeter.class)
   *   .send(Greeter::greet, new Greeting("Alice"));
   * }</pre>
   */
  default <I, O> SendResponse<O> send(BiFunction<SVC, I, O> s, I input) {
    return send(s, input, InvocationOptions.DEFAULT);
  }

  /** Like {@link #send(BiFunction, Object)}, with invocation options. */
  default <I, O> SendResponse<O> send(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return send(s, input, options.build());
  }

  /** Like {@link #send(BiFunction, Object)}, with invocation options. */
  default <I, O> SendResponse<O> send(
      BiFunction<SVC, I, O> s, I input, InvocationOptions invocationOptions) {
    return send(s, input, null, invocationOptions);
  }

  /** Like {@link #send(BiFunction, Object)}, with a delay. */
  default <I, O> SendResponse<O> send(BiFunction<SVC, I, O> s, I input, Duration delay) {
    return send(s, input, delay, InvocationOptions.DEFAULT);
  }

  /** Like {@link #send(BiFunction, Object)}, with a delay and invocation options. */
  default <I, O> SendResponse<O> send(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions.Builder options) {
    return send(s, input, delay, options.build());
  }

  /** Like {@link #send(BiFunction, Object)}, with a delay and invocation options. */
  default <I, O> SendResponse<O> send(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions invocationOptions) {
    try {
      return sendAsync(s, input, delay, invocationOptions).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  // send - BiConsumer variants
  /** Like {@link #send(BiFunction, Object)}, for methods without a return value. */
  default <I> SendResponse<Void> send(BiConsumer<SVC, I> s, I input) {
    return send(s, input, InvocationOptions.DEFAULT);
  }

  /** Like {@link #send(BiConsumer, Object)}, with invocation options. */
  default <I> SendResponse<Void> send(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return send(s, input, options.build());
  }

  /** Like {@link #send(BiConsumer, Object)}, with invocation options. */
  default <I> SendResponse<Void> send(
      BiConsumer<SVC, I> s, I input, InvocationOptions invocationOptions) {
    return send(s, input, null, invocationOptions);
  }

  /** Like {@link #send(BiConsumer, Object)}, with a delay. */
  default <I> SendResponse<Void> send(BiConsumer<SVC, I> s, I input, Duration delay) {
    return send(s, input, delay, InvocationOptions.DEFAULT);
  }

  /** Like {@link #send(BiConsumer, Object)}, with a delay and invocation options. */
  default <I> SendResponse<Void> send(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions.Builder options) {
    return send(s, input, delay, options.build());
  }

  /** Like {@link #send(BiConsumer, Object)}, with a delay and invocation options. */
  default <I> SendResponse<Void> send(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions invocationOptions) {
    try {
      return sendAsync(s, input, delay, invocationOptions).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  // send - Function variants
  /** Like {@link #send(BiFunction, Object)}, for methods without input. */
  default <O> SendResponse<O> send(Function<SVC, O> s) {
    return send(s, InvocationOptions.DEFAULT);
  }

  /** Like {@link #send(Function)}, with invocation options. */
  default <O> SendResponse<O> send(Function<SVC, O> s, InvocationOptions.Builder options) {
    return send(s, options.build());
  }

  /** Like {@link #send(Function)}, with invocation options. */
  default <O> SendResponse<O> send(Function<SVC, O> s, InvocationOptions invocationOptions) {
    return send(s, null, invocationOptions);
  }

  /** Like {@link #send(Function)}, with a delay. */
  default <O> SendResponse<O> send(Function<SVC, O> s, Duration delay) {
    return send(s, delay, InvocationOptions.DEFAULT);
  }

  /** Like {@link #send(Function)}, with a delay and invocation options. */
  default <O> SendResponse<O> send(
      Function<SVC, O> s, Duration delay, InvocationOptions.Builder options) {
    return send(s, delay, options.build());
  }

  /** Like {@link #send(Function)}, with a delay and invocation options. */
  default <O> SendResponse<O> send(
      Function<SVC, O> s, Duration delay, InvocationOptions invocationOptions) {
    try {
      return sendAsync(s, delay, invocationOptions).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  // send - Consumer variants
  /** Like {@link #send(BiFunction, Object)}, for methods without input or return value. */
  default SendResponse<Void> send(Consumer<SVC> s) {
    return send(s, InvocationOptions.DEFAULT);
  }

  /** Like {@link #send(Consumer)}, with invocation options. */
  default SendResponse<Void> send(Consumer<SVC> s, InvocationOptions.Builder options) {
    return send(s, options.build());
  }

  /** Like {@link #send(Consumer)}, with invocation options. */
  default SendResponse<Void> send(Consumer<SVC> s, InvocationOptions invocationOptions) {
    return send(s, null, invocationOptions);
  }

  /** Like {@link #send(Consumer)}, with a delay. */
  default SendResponse<Void> send(Consumer<SVC> s, Duration delay) {
    return send(s, delay, InvocationOptions.DEFAULT);
  }

  /** Like {@link #send(Consumer)}, with a delay and invocation options. */
  default SendResponse<Void> send(
      Consumer<SVC> s, Duration delay, InvocationOptions.Builder options) {
    return send(s, delay, options.build());
  }

  /** Like {@link #send(Consumer)}, with a delay and invocation options. */
  default SendResponse<Void> send(
      Consumer<SVC> s, Duration delay, InvocationOptions invocationOptions) {
    try {
      return sendAsync(s, delay, invocationOptions).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    }
  }

  // sendAsync - BiFunction variants
  /** Async version of {@link #send(BiFunction, Object)}. */
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(BiFunction<SVC, I, O> s, I input) {
    return sendAsync(s, input, InvocationOptions.DEFAULT);
  }

  /** Like {@link #sendAsync(BiFunction, Object)}, with options. */
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return sendAsync(s, input, options.build());
  }

  /** Like {@link #sendAsync(BiFunction, Object)}, with options. */
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, InvocationOptions invocationOptions) {
    return sendAsync(s, input, null, invocationOptions);
  }

  /** Like {@link #sendAsync(BiFunction, Object)}, with a delay. */
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, Duration delay) {
    return sendAsync(s, input, delay, InvocationOptions.DEFAULT);
  }

  /** Like {@link #sendAsync(BiFunction, Object)}, with delay and options. */
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions.Builder options) {
    return sendAsync(s, input, delay, options.build());
  }

  /** Like {@link #sendAsync(BiFunction, Object)}, with delay and options. */
  <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions invocationOptions);

  // sendAsync - BiConsumer variants
  /** Like {@link #sendAsync(BiFunction, Object)}, for void methods. */
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(BiConsumer<SVC, I> s, I input) {
    return sendAsync(s, input, InvocationOptions.DEFAULT);
  }

  /** Like {@link #sendAsync(BiConsumer, Object)}, with options. */
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return sendAsync(s, input, options.build());
  }

  /** Like {@link #sendAsync(BiConsumer, Object)}, with options. */
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, InvocationOptions invocationOptions) {
    return sendAsync(s, input, null, invocationOptions);
  }

  /** Like {@link #sendAsync(BiConsumer, Object)}, with a delay. */
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, Duration delay) {
    return sendAsync(s, input, delay, InvocationOptions.DEFAULT);
  }

  /** Like {@link #sendAsync(BiConsumer, Object)}, with delay and options. */
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions.Builder options) {
    return sendAsync(s, input, delay, options.build());
  }

  /** Like {@link #sendAsync(BiConsumer, Object)}, with delay and options. */
  <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions invocationOptions);

  // sendAsync - Function variants
  /** Like {@link #sendAsync(BiFunction, Object)}, for no-input methods. */
  default <O> CompletableFuture<SendResponse<O>> sendAsync(Function<SVC, O> s) {
    return sendAsync(s, InvocationOptions.DEFAULT);
  }

  /** Like {@link #sendAsync(Function)}, with options. */
  default <O> CompletableFuture<SendResponse<O>> sendAsync(
      Function<SVC, O> s, InvocationOptions.Builder options) {
    return sendAsync(s, options.build());
  }

  /** Like {@link #sendAsync(Function)}, with options. */
  default <O> CompletableFuture<SendResponse<O>> sendAsync(
      Function<SVC, O> s, InvocationOptions invocationOptions) {
    return sendAsync(s, null, invocationOptions);
  }

  /** Like {@link #sendAsync(Function)}, with a delay. */
  default <O> CompletableFuture<SendResponse<O>> sendAsync(Function<SVC, O> s, Duration delay) {
    return sendAsync(s, delay, InvocationOptions.DEFAULT);
  }

  /** Like {@link #sendAsync(Function)}, with delay and options. */
  default <O> CompletableFuture<SendResponse<O>> sendAsync(
      Function<SVC, O> s, Duration delay, InvocationOptions.Builder options) {
    return sendAsync(s, delay, options.build());
  }

  /** Like {@link #sendAsync(Function)}, with delay and options. */
  <O> CompletableFuture<SendResponse<O>> sendAsync(
      Function<SVC, O> s, Duration delay, InvocationOptions invocationOptions);

  // sendAsync - Consumer variants
  /** Like {@link #sendAsync(BiFunction, Object)}, for no-input/void methods. */
  default CompletableFuture<SendResponse<Void>> sendAsync(Consumer<SVC> s) {
    return sendAsync(s, InvocationOptions.DEFAULT);
  }

  /** Like {@link #sendAsync(Consumer)}, with options. */
  default CompletableFuture<SendResponse<Void>> sendAsync(
      Consumer<SVC> s, InvocationOptions.Builder options) {
    return sendAsync(s, options.build());
  }

  /** Like {@link #sendAsync(Consumer)}, with options. */
  default CompletableFuture<SendResponse<Void>> sendAsync(
      Consumer<SVC> s, InvocationOptions invocationOptions) {
    return sendAsync(s, null, invocationOptions);
  }

  /** Like {@link #sendAsync(Consumer)}, with a delay. */
  default CompletableFuture<SendResponse<Void>> sendAsync(Consumer<SVC> s, Duration delay) {
    return sendAsync(s, delay, InvocationOptions.DEFAULT);
  }

  /** Like {@link #sendAsync(Consumer)}, with delay and options. */
  default CompletableFuture<SendResponse<Void>> sendAsync(
      Consumer<SVC> s, Duration delay, InvocationOptions.Builder options) {
    return sendAsync(s, delay, options.build());
  }

  /** Like {@link #sendAsync(Consumer)}, with delay and options. */
  CompletableFuture<SendResponse<Void>> sendAsync(
      Consumer<SVC> s, Duration delay, InvocationOptions invocationOptions);
}
