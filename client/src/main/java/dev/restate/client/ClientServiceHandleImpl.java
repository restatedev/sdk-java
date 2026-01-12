// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import static dev.restate.common.reflections.RestateUtils.toRequest;

import dev.restate.common.InvocationOptions;
import dev.restate.common.reflections.*;
import dev.restate.serde.Serde;
import dev.restate.serde.TypeTag;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

final class ClientServiceHandleImpl<SVC> implements ClientServiceHandle<SVC> {

  private final Client innerClient;

  private final Class<SVC> clazz;
  private final String serviceName;
  private final @Nullable String key;

  private MethodInfoCollector<SVC> methodInfoCollector;

  ClientServiceHandleImpl(Client innerClient, Class<SVC> clazz, @Nullable String key) {
    this.innerClient = innerClient;
    this.clazz = clazz;
    this.serviceName = ReflectionUtils.extractServiceName(clazz);
    this.key = key;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <I, O> CompletableFuture<Response<O>> callAsync(
      BiFunction<SVC, I, O> s, I input, InvocationOptions invocationOptions) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(s, input);
    return innerClient.callAsync(
        toRequest(
            serviceName,
            key,
            methodInfo.getHandlerName(),
            (TypeTag<I>) methodInfo.getInputType(),
            (TypeTag<O>) methodInfo.getOutputType(),
            input,
            invocationOptions));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <I> CompletableFuture<Response<Void>> callAsync(
      BiConsumer<SVC, I> s, I input, InvocationOptions invocationOptions) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(s, input);
    return innerClient.callAsync(
        toRequest(
            serviceName,
            key,
            methodInfo.getHandlerName(),
            (TypeTag<I>) methodInfo.getInputType(),
            Serde.VOID,
            input,
            invocationOptions));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <O> CompletableFuture<Response<O>> callAsync(
      Function<SVC, O> s, InvocationOptions invocationOptions) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(s);
    return innerClient.callAsync(
        toRequest(
            serviceName,
            key,
            methodInfo.getHandlerName(),
            Serde.VOID,
            (TypeTag<O>) methodInfo.getOutputType(),
            null,
            invocationOptions));
  }

  @Override
  public CompletableFuture<Response<Void>> callAsync(
      Consumer<SVC> s, InvocationOptions invocationOptions) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(s);
    return innerClient.callAsync(
        toRequest(
            serviceName,
            key,
            methodInfo.getHandlerName(),
            Serde.VOID,
            Serde.VOID,
            null,
            invocationOptions));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <I, O> CompletableFuture<SendResponse<O>> sendAsync(
      BiFunction<SVC, I, O> s, I input, Duration delay, InvocationOptions invocationOptions) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(s, input);
    return innerClient.sendAsync(
        toRequest(
            serviceName,
            key,
            methodInfo.getHandlerName(),
            (TypeTag<I>) methodInfo.getInputType(),
            (TypeTag<O>) methodInfo.getOutputType(),
            input,
            invocationOptions),
        delay);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <I> CompletableFuture<SendResponse<Void>> sendAsync(
      BiConsumer<SVC, I> s, I input, Duration delay, InvocationOptions invocationOptions) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(s, input);
    return innerClient.sendAsync(
        toRequest(
            serviceName,
            key,
            methodInfo.getHandlerName(),
            (TypeTag<I>) methodInfo.getInputType(),
            Serde.VOID,
            input,
            invocationOptions),
        delay);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <O> CompletableFuture<SendResponse<O>> sendAsync(
      Function<SVC, O> s, Duration delay, InvocationOptions invocationOptions) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(s);
    return innerClient.sendAsync(
        toRequest(
            serviceName,
            key,
            methodInfo.getHandlerName(),
            Serde.VOID,
            (TypeTag<O>) methodInfo.getOutputType(),
            null,
            invocationOptions),
        delay);
  }

  @Override
  public CompletableFuture<SendResponse<Void>> sendAsync(
      Consumer<SVC> s, Duration delay, InvocationOptions invocationOptions) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(s);
    return innerClient.sendAsync(
        toRequest(
            serviceName,
            key,
            methodInfo.getHandlerName(),
            Serde.VOID,
            Serde.VOID,
            null,
            invocationOptions),
        delay);
  }

  private MethodInfoCollector<SVC> getMethodInfoCollector() {
    if (this.methodInfoCollector == null) {
      this.methodInfoCollector = new MethodInfoCollector<>(this.clazz);
    }
    return this.methodInfoCollector;
  }
}
