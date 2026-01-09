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

@org.jetbrains.annotations.ApiStatus.Experimental
public interface ServiceReference<SVC> {
  @org.jetbrains.annotations.ApiStatus.Experimental
  SVC client();

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> DurableFuture<O> call(BiFunction<SVC, I, O> s, I input) {
    return call(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> DurableFuture<O> call(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return call(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <I, O> DurableFuture<O> call(BiFunction<SVC, I, O> s, I input, InvocationOptions options);

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> DurableFuture<Void> call(BiConsumer<SVC, I> s, I input) {
    return call(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> DurableFuture<Void> call(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return call(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <I> DurableFuture<Void> call(BiConsumer<SVC, I> s, I input, InvocationOptions options);

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> DurableFuture<O> call(Function<SVC, O> s) {
    return call(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> DurableFuture<O> call(Function<SVC, O> s, InvocationOptions.Builder options) {
    return call(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <O> DurableFuture<O> call(Function<SVC, O> s, InvocationOptions options);

  @org.jetbrains.annotations.ApiStatus.Experimental
  default DurableFuture<Void> call(Consumer<SVC> s) {
    return call(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default DurableFuture<Void> call(Consumer<SVC> s, InvocationOptions.Builder options) {
    return call(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  DurableFuture<Void> call(Consumer<SVC> s, InvocationOptions options);

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(BiFunction<SVC, I, O> s, I input) {
    return send(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> s, I input, InvocationOptions.Builder options) {
    return send(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> s, I input, InvocationOptions options) {
    return send(s, input, null, options);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(BiFunction<SVC, I, O> s, I input, Duration delay) {
    return send(s, input, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions.Builder options) {
    return send(s, input, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions options);

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(BiConsumer<SVC, I> s, I input) {
    return send(s, input, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> s, I input, InvocationOptions.Builder options) {
    return send(s, input, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> s, I input, InvocationOptions options) {
    return send(s, input, null, options);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(BiConsumer<SVC, I> s, I input, Duration delay) {
    return send(s, input, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions.Builder options) {
    return send(s, input, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions options);

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(Function<SVC, O> s) {
    return send(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(Function<SVC, O> s, InvocationOptions.Builder options) {
    return send(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(Function<SVC, O> s, InvocationOptions options) {
    return send(s, null, options);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(Function<SVC, O> s, Duration delay) {
    return send(s, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default <O> InvocationHandle<O> send(
      Function<SVC, O> s, Duration delay, InvocationOptions.Builder options) {
    return send(s, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  <O> InvocationHandle<O> send(Function<SVC, O> s, Duration delay, InvocationOptions options);

  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> s) {
    return send(s, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> s, InvocationOptions.Builder options) {
    return send(s, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> s, InvocationOptions options) {
    return send(s, null, options);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(Consumer<SVC> s, Duration delay) {
    return send(s, delay, InvocationOptions.DEFAULT);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  default InvocationHandle<Void> send(
      Consumer<SVC> s, Duration delay, InvocationOptions.Builder options) {
    return send(s, delay, options.build());
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  InvocationHandle<Void> send(Consumer<SVC> s, Duration delay, InvocationOptions options);
}
