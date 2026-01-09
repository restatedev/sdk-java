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

@org.jetbrains.annotations.ApiStatus.Experimental
public interface ClientServiceReference<SVC> {
  @org.jetbrains.annotations.ApiStatus.Experimental
  SVC client();

  // call - BiFunction variants
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> Response<O> call(BiFunction<SVC, I, O> s, I input) {
    return call(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> Response<O> call(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return call(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> Response<Void> call(BiConsumer<SVC, I> s, I input) {
    return call(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> Response<Void> call(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return call(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> Response<O> call(Function<SVC, O> s) {
    return call(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> Response<O> call(Function<SVC, O> s, InvocationOptions.Builder options) {
    return call(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  default Response<Void> call(Consumer<SVC> s) {
    return call(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default Response<Void> call(Consumer<SVC> s, InvocationOptions.Builder options) {
    return call(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> CompletableFuture<Response<O>> callAsync(BiFunction<SVC, I, O> s, I input) {
    return callAsync(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> CompletableFuture<Response<O>> callAsync(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return callAsync(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <I, O> CompletableFuture<Response<O>> callAsync(
      BiFunction<SVC, I, O> s, I input, InvocationOptions invocationOptions);

  // callAsync - BiConsumer variants
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> CompletableFuture<Response<Void>> callAsync(BiConsumer<SVC, I> s, I input) {
    return callAsync(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> CompletableFuture<Response<Void>> callAsync(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return callAsync(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <I> CompletableFuture<Response<Void>> callAsync(
      BiConsumer<SVC, I> s, I input, InvocationOptions invocationOptions);

  // callAsync - Function variants
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> CompletableFuture<Response<O>> callAsync(Function<SVC, O> s) {
    return callAsync(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> CompletableFuture<Response<O>> callAsync(
      Function<SVC, O> s, InvocationOptions.Builder options) {
    return callAsync(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <O> CompletableFuture<Response<O>> callAsync(
      Function<SVC, O> s, InvocationOptions invocationOptions);

  // callAsync - Consumer variants
  @org.jetbrains.annotations.ApiStatus.Experimental
  default CompletableFuture<Response<Void>> callAsync(Consumer<SVC> s) {
    return callAsync(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default CompletableFuture<Response<Void>> callAsync(
      Consumer<SVC> s, InvocationOptions.Builder options) {
    return callAsync(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  CompletableFuture<Response<Void>> callAsync(Consumer<SVC> s, InvocationOptions invocationOptions);

  // send - BiFunction variants
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> SendResponse<O> send(BiFunction<SVC, I, O> s, I input) {
    return send(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> SendResponse<O> send(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return send(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> SendResponse<O> send(
      BiFunction<SVC, I, O> s, I input, InvocationOptions invocationOptions) {
    return send(s, input, null, invocationOptions);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> SendResponse<O> send(BiFunction<SVC, I, O> s, I input, Duration delay) {
    return send(s, input, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> SendResponse<O> send(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions.Builder options) {
    return send(s, input, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> SendResponse<Void> send(BiConsumer<SVC, I> s, I input) {
    return send(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> SendResponse<Void> send(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return send(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> SendResponse<Void> send(
      BiConsumer<SVC, I> s, I input, InvocationOptions invocationOptions) {
    return send(s, input, null, invocationOptions);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> SendResponse<Void> send(BiConsumer<SVC, I> s, I input, Duration delay) {
    return send(s, input, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> SendResponse<Void> send(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions.Builder options) {
    return send(s, input, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> SendResponse<O> send(Function<SVC, O> s) {
    return send(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> SendResponse<O> send(Function<SVC, O> s, InvocationOptions.Builder options) {
    return send(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> SendResponse<O> send(Function<SVC, O> s, InvocationOptions invocationOptions) {
    return send(s, null, invocationOptions);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> SendResponse<O> send(Function<SVC, O> s, Duration delay) {
    return send(s, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> SendResponse<O> send(
      Function<SVC, O> s, Duration delay, InvocationOptions.Builder options) {
    return send(s, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  default SendResponse<Void> send(Consumer<SVC> s) {
    return send(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default SendResponse<Void> send(Consumer<SVC> s, InvocationOptions.Builder options) {
    return send(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default SendResponse<Void> send(Consumer<SVC> s, InvocationOptions invocationOptions) {
    return send(s, null, invocationOptions);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default SendResponse<Void> send(Consumer<SVC> s, Duration delay) {
    return send(s, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default SendResponse<Void> send(
      Consumer<SVC> s, Duration delay, InvocationOptions.Builder options) {
    return send(s, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
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
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(BiFunction<SVC, I, O> s, I input) {
    return sendAsync(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return sendAsync(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, InvocationOptions invocationOptions) {
    return sendAsync(s, input, null, invocationOptions);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, Duration delay) {
    return sendAsync(s, input, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions.Builder options) {
    return sendAsync(s, input, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions invocationOptions);

  // sendAsync - BiConsumer variants
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(BiConsumer<SVC, I> s, I input) {
    return sendAsync(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return sendAsync(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, InvocationOptions invocationOptions) {
    return sendAsync(s, input, null, invocationOptions);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, Duration delay) {
    return sendAsync(s, input, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions.Builder options) {
    return sendAsync(s, input, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions invocationOptions);

  // sendAsync - Function variants
  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> CompletableFuture<SendResponse<O>> sendAsync(Function<SVC, O> s) {
    return sendAsync(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> CompletableFuture<SendResponse<O>> sendAsync(
      Function<SVC, O> s, InvocationOptions.Builder options) {
    return sendAsync(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> CompletableFuture<SendResponse<O>> sendAsync(
      Function<SVC, O> s, InvocationOptions invocationOptions) {
    return sendAsync(s, null, invocationOptions);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> CompletableFuture<SendResponse<O>> sendAsync(Function<SVC, O> s, Duration delay) {
    return sendAsync(s, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> CompletableFuture<SendResponse<O>> sendAsync(
      Function<SVC, O> s, Duration delay, InvocationOptions.Builder options) {
    return sendAsync(s, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <O> CompletableFuture<SendResponse<O>> sendAsync(
      Function<SVC, O> s, Duration delay, InvocationOptions invocationOptions);

  // sendAsync - Consumer variants
  @org.jetbrains.annotations.ApiStatus.Experimental
  default CompletableFuture<SendResponse<Void>> sendAsync(Consumer<SVC> s) {
    return sendAsync(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default CompletableFuture<SendResponse<Void>> sendAsync(
      Consumer<SVC> s, InvocationOptions.Builder options) {
    return sendAsync(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default CompletableFuture<SendResponse<Void>> sendAsync(
      Consumer<SVC> s, InvocationOptions invocationOptions) {
    return sendAsync(s, null, invocationOptions);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default CompletableFuture<SendResponse<Void>> sendAsync(Consumer<SVC> s, Duration delay) {
    return sendAsync(s, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default CompletableFuture<SendResponse<Void>> sendAsync(
      Consumer<SVC> s, Duration delay, InvocationOptions.Builder options) {
    return sendAsync(s, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  CompletableFuture<SendResponse<Void>> sendAsync(
      Consumer<SVC> s, Duration delay, InvocationOptions invocationOptions);
}
