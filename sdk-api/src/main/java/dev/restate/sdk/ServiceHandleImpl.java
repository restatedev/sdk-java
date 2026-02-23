// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.common.reflections.RestateUtils.toRequest;

import dev.restate.common.InvocationOptions;
import dev.restate.common.reflections.MethodInfo;
import dev.restate.common.reflections.MethodInfoCollector;
import dev.restate.common.reflections.ReflectionUtils;
import dev.restate.serde.Serde;
import dev.restate.serde.TypeTag;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

final class ServiceHandleImpl<SVC> implements ServiceHandle<SVC> {

  private final Class<SVC> clazz;
  private final String serviceName;
  private final @Nullable String key;

  // To use call/send
  private MethodInfoCollector<SVC> methodInfoCollector;

  ServiceHandleImpl(Class<SVC> clazz, @Nullable String key) {
    this(clazz, ReflectionUtils.extractServiceName(clazz), key);
  }

  ServiceHandleImpl(Class<SVC> clazz, String serviceName, @Nullable String key) {
    this.clazz = clazz;
    this.serviceName = serviceName;
    this.key = key;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <I, O> DurableFuture<O> call(
      BiFunction<SVC, I, O> methodReference, I input, InvocationOptions options) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(methodReference, input);
    return Context.current()
        .call(
            toRequest(
                serviceName,
                key,
                methodInfo.getHandlerName(),
                (TypeTag<I>) methodInfo.getInputType(),
                (TypeTag<O>) methodInfo.getOutputType(),
                input,
                options));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <I> DurableFuture<Void> call(
      BiConsumer<SVC, I> methodReference, I input, InvocationOptions options) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(methodReference, input);
    return Context.current()
        .call(
            toRequest(
                serviceName,
                key,
                methodInfo.getHandlerName(),
                (TypeTag<I>) methodInfo.getInputType(),
                Serde.VOID,
                input,
                options));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <O> DurableFuture<O> call(Function<SVC, O> methodReference, InvocationOptions options) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(methodReference);
    return Context.current()
        .call(
            toRequest(
                serviceName,
                key,
                methodInfo.getHandlerName(),
                Serde.VOID,
                (TypeTag<O>) methodInfo.getOutputType(),
                null,
                options));
  }

  @Override
  public DurableFuture<Void> call(Consumer<SVC> methodReference, InvocationOptions options) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(methodReference);
    return Context.current()
        .call(
            toRequest(
                serviceName,
                key,
                methodInfo.getHandlerName(),
                Serde.VOID,
                Serde.VOID,
                null,
                options));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <I, O> InvocationHandle<O> send(
      BiFunction<SVC, I, O> methodReference, I input, Duration delay, InvocationOptions options) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(methodReference, input);
    return Context.current()
        .send(
            toRequest(
                serviceName,
                key,
                methodInfo.getHandlerName(),
                (TypeTag<I>) methodInfo.getInputType(),
                (TypeTag<O>) methodInfo.getOutputType(),
                input,
                options),
            delay);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <I> InvocationHandle<Void> send(
      BiConsumer<SVC, I> methodReference, I input, Duration delay, InvocationOptions options) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(methodReference, input);
    return Context.current()
        .send(
            toRequest(
                serviceName,
                key,
                methodInfo.getHandlerName(),
                (TypeTag<I>) methodInfo.getInputType(),
                Serde.VOID,
                input,
                options),
            delay);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <O> InvocationHandle<O> send(
      Function<SVC, O> methodReference, Duration delay, InvocationOptions options) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(methodReference);
    return Context.current()
        .send(
            toRequest(
                serviceName,
                key,
                methodInfo.getHandlerName(),
                Serde.VOID,
                (TypeTag<O>) methodInfo.getOutputType(),
                null,
                options),
            delay);
  }

  @Override
  public InvocationHandle<Void> send(
      Consumer<SVC> methodReference, Duration delay, InvocationOptions options) {
    MethodInfo methodInfo = getMethodInfoCollector().resolve(methodReference);
    return Context.current()
        .send(
            toRequest(
                serviceName,
                key,
                methodInfo.getHandlerName(),
                Serde.VOID,
                Serde.VOID,
                null,
                options),
            delay);
  }

  private MethodInfoCollector<SVC> getMethodInfoCollector() {
    if (this.methodInfoCollector == null) {
      this.methodInfoCollector = new MethodInfoCollector<>(this.clazz);
    }
    return this.methodInfoCollector;
  }
}
