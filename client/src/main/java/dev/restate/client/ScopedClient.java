// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import dev.restate.common.Request;
import dev.restate.common.Target;
import dev.restate.common.reflections.MethodInfo;
import dev.restate.common.reflections.ProxySupport;
import dev.restate.common.reflections.ReflectionUtils;
import dev.restate.serde.TypeTag;

/**
 * Scope client communication, to send requests to services, virtual objects and workflows within a
 * scope. Requires Restate >= 1.7.
 *
 * <p>Obtain an instance via {@link Client#scope(String)}.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public final class ScopedClient {

  private final Client client;
  private final String scope;

  ScopedClient(Client client, String scope) {
    this.client = client;
    this.scope = scope;
  }

  /**
   * @see Client#service(Class)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> SVC service(Class<SVC> clazz) {
    ReflectionUtils.mustHaveServiceAnnotation(clazz);
    if (ReflectionUtils.isKotlinClass(clazz)) {
      throw new IllegalArgumentException("Using Kotlin classes with Java's API is not supported");
    }
    var serviceName = ReflectionUtils.extractServiceName(clazz);
    return ProxySupport.createProxy(
        clazz,
        invocation -> {
          var methodInfo = MethodInfo.fromMethod(invocation.getMethod());

          //noinspection unchecked
          return client
              .call(
                  Request.of(
                      Target.virtualObject(scope, serviceName, null, methodInfo.getHandlerName()),
                      (TypeTag<? super Object>) methodInfo.getInputType(),
                      (TypeTag<? super Object>) methodInfo.getOutputType(),
                      invocation.getArguments().length == 0 ? null : invocation.getArguments()[0]))
              .response();
        });
  }

  /**
   * @see Client#serviceHandle(Class)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> ClientServiceHandle<SVC> serviceHandle(Class<SVC> clazz) {
    ReflectionUtils.mustHaveServiceAnnotation(clazz);
    if (ReflectionUtils.isKotlinClass(clazz)) {
      throw new IllegalArgumentException("Using Kotlin classes with Java's API is not supported");
    }
    return new ClientServiceHandleImpl<>(client, clazz, null, scope);
  }

  /**
   * @see Client#virtualObject(Class, String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> SVC virtualObject(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz);
    if (ReflectionUtils.isKotlinClass(clazz)) {
      throw new IllegalArgumentException("Using Kotlin classes with Java's API is not supported");
    }
    var serviceName = ReflectionUtils.extractServiceName(clazz);
    return ProxySupport.createProxy(
        clazz,
        invocation -> {
          var methodInfo = MethodInfo.fromMethod(invocation.getMethod());

          //noinspection unchecked
          return client
              .call(
                  Request.of(
                      Target.virtualObject(scope, serviceName, key, methodInfo.getHandlerName()),
                      (TypeTag<? super Object>) methodInfo.getInputType(),
                      (TypeTag<? super Object>) methodInfo.getOutputType(),
                      invocation.getArguments().length == 0 ? null : invocation.getArguments()[0]))
              .response();
        });
  }

  /**
   * @see Client#virtualObjectHandle(Class, String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> ClientServiceHandle<SVC> virtualObjectHandle(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz);
    if (ReflectionUtils.isKotlinClass(clazz)) {
      throw new IllegalArgumentException("Using Kotlin classes with Java's API is not supported");
    }
    return new ClientServiceHandleImpl<>(client, clazz, key, scope);
  }

  /**
   * @see Client#workflow(Class, String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> SVC workflow(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveWorkflowAnnotation(clazz);
    if (ReflectionUtils.isKotlinClass(clazz)) {
      throw new IllegalArgumentException("Using Kotlin classes with Java's API is not supported");
    }
    var serviceName = ReflectionUtils.extractServiceName(clazz);
    return ProxySupport.createProxy(
        clazz,
        invocation -> {
          var methodInfo = MethodInfo.fromMethod(invocation.getMethod());

          //noinspection unchecked
          return client
              .call(
                  Request.of(
                      Target.virtualObject(scope, serviceName, key, methodInfo.getHandlerName()),
                      (TypeTag<? super Object>) methodInfo.getInputType(),
                      (TypeTag<? super Object>) methodInfo.getOutputType(),
                      invocation.getArguments().length == 0 ? null : invocation.getArguments()[0]))
              .response();
        });
  }

  /**
   * @see Client#workflowHandle(Class, String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> ClientServiceHandle<SVC> workflowHandle(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveWorkflowAnnotation(clazz);
    if (ReflectionUtils.isKotlinClass(clazz)) {
      throw new IllegalArgumentException("Using Kotlin classes with Java's API is not supported");
    }
    return new ClientServiceHandleImpl<>(client, clazz, key, scope);
  }
}
