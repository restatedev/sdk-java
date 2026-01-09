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
  public static Context context() {
    return HandlerRunner.getContext();
  }

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

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static SharedObjectContext sharedObjectContext() {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (handlerContext.canReadState()) {
      return (SharedObjectContext) context();
    }

    throw new IllegalStateException(
        "Calling objectContext() from a non Virtual object handler. You can use Restate.objectContext() only inside a Restate Virtual Object handler.");
  }

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

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static SharedWorkflowContext sharedWorkflowContext() {
    var handlerContext = HandlerRunner.getHandlerContext();

    if (handlerContext.canReadPromises()) {
      return (SharedWorkflowContext) context();
    }

    throw new IllegalStateException(
        "Calling workflowContext() from a non Workflow handler. You can use Restate.workflowContext() only inside a Restate Workflow handler.");
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static boolean isInsideHandler() {
    return HandlerRunner.CONTEXT_THREAD_LOCAL.get() != null;
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static HandlerRequest request() {
    return context().request();
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static RestateRandom random() {
    return context().random();
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <R> InvocationHandle<R> invocationHandle(
      String invocationId, TypeTag<R> responseTypeTag) {
    return context().invocationHandle(invocationId, responseTypeTag);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <R> InvocationHandle<R> invocationHandle(
      String invocationId, Class<R> responseClazz) {
    return context().invocationHandle(invocationId, responseClazz);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static InvocationHandle<Slice> invocationHandle(String invocationId) {
    return context().invocationHandle(invocationId);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static void sleep(Duration duration) {
    context().sleep(duration);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static DurableFuture<Void> timer(String name, Duration duration) {
    return context().timer(name, duration);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(String name, Class<T> clazz, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().run(name, clazz, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().run(name, typeTag, retryPolicy, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().run(name, clazz, retryPolicy, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> T run(String name, TypeTag<T> typeTag, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().run(name, typeTag, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static void run(String name, RetryPolicy retryPolicy, ThrowingRunnable runnable)
      throws TerminalException {
    context().run(name, retryPolicy, runnable);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static void run(String name, ThrowingRunnable runnable) throws TerminalException {
    context().run(name, runnable);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, Class<T> clazz, ThrowingSupplier<T> action) throws TerminalException {
    return context().runAsync(name, clazz, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, ThrowingSupplier<T> action) throws TerminalException {
    return context().runAsync(name, typeTag, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, Class<T> clazz, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().runAsync(name, clazz, retryPolicy, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> DurableFuture<T> runAsync(
      String name, TypeTag<T> typeTag, RetryPolicy retryPolicy, ThrowingSupplier<T> action)
      throws TerminalException {
    return context().runAsync(name, typeTag, retryPolicy, action);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static DurableFuture<Void> runAsync(
      String name, RetryPolicy retryPolicy, ThrowingRunnable runnable) throws TerminalException {
    return context().runAsync(name, retryPolicy, runnable);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static DurableFuture<Void> runAsync(String name, ThrowingRunnable runnable)
      throws TerminalException {
    return context().runAsync(name, runnable);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> Awakeable<T> awakeable(Class<T> clazz) {
    return context().awakeable(clazz);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static <T> Awakeable<T> awakeable(TypeTag<T> typeTag) {
    return context().awakeable(typeTag);
  }

  @org.jetbrains.annotations.ApiStatus.Experimental
  public static AwakeableHandle awakeableHandle(String id) {
    return context().awakeableHandle(id);
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
