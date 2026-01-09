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
import dev.restate.sdk.common.HandlerRequest;
import dev.restate.sdk.common.RetryPolicy;
import dev.restate.sdk.common.TerminalException;
import dev.restate.serde.TypeTag;
import java.time.Duration;

@org.jetbrains.annotations.ApiStatus.Experimental
public final class Restate {
  @org.jetbrains.annotations.ApiStatus.Experimental
  public static Context get() {
    return RestateThreadLocalContext.getContext();
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static boolean isInsideHandler() {
    return RestateThreadLocalContext.CONTEXT_THREAD_LOCAL.get() != null;
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static HandlerRequest request() {
    return get().request();
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static RestateRandom random() {
    return get().random();
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <R> InvocationHandle<R> invocationHandle(
      String invocationId, TypeTag<R> responseTypeTag) {
    return get().invocationHandle(invocationId, responseTypeTag);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <R> InvocationHandle<R> invocationHandle(
      String invocationId, Class<R> responseClazz) {
    return get().invocationHandle(invocationId, responseClazz);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static InvocationHandle<Slice> invocationHandle(String invocationId) {
    return get().invocationHandle(invocationId);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static void sleep(Duration duration) {
    get().sleep(duration);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static DurableFuture<Void> timer(String name, Duration duration) {
    return get().timer(name, duration);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(String name, Class<T> clazz, ThrowingSupplier<T> action)
      throws TerminalException {
    return get().run(name, clazz, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return get().run(name, typeTag, retryPolicy, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return get().run(name, clazz, retryPolicy, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(String name, TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return get().run(name, typeTag, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static void run(String name, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    get().run(name, retryPolicy, runnable);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static void run(String name, ThrowingRunnable runnable) throws TerminalException {
    get().run(name, runnable);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, Class<T> clazz, ThrowingSupplier<T> action) throws TerminalException {
    return get().runAsync(name, clazz, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, ThrowingSupplier<T> action) throws TerminalException {
    return get().runAsync(name, typeTag, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return get().runAsync(name, clazz, retryPolicy, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return get().runAsync(name, typeTag, retryPolicy, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static DurableFuture<Void> runAsync(
      String name, RetryPolicy retryPolicy, ThrowingRunnable runnable) throws TerminalException {
    return get().runAsync(name, retryPolicy, runnable);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static DurableFuture<Void> runAsync(String name, ThrowingRunnable runnable)
      throws TerminalException {
    return get().runAsync(name, runnable);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> Awakeable<T> awakeable(Class<T> clazz) {
    return get().awakeable(clazz);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> Awakeable<T> awakeable(TypeTag<T> typeTag) {
    return get().awakeable(typeTag);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static AwakeableHandle awakeableHandle(String id) {
    return get().awakeableHandle(id);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <SVC> ServiceReference<SVC> service(Class<SVC> clazz) {
    mustHaveAnnotation(clazz, Service.class);
    return new ServiceReferenceImpl<>(clazz, null);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <SVC> ServiceReference<SVC> virtualObject(Class<SVC> clazz, String key) {
    mustHaveAnnotation(clazz, VirtualObject.class);
    return new ServiceReferenceImpl<>(clazz, key);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <SVC> ServiceReference<SVC> workflow(Class<SVC> clazz, String key) {
    mustHaveAnnotation(clazz, Workflow.class);
    return new ServiceReferenceImpl<>(clazz, key);
  }
}
