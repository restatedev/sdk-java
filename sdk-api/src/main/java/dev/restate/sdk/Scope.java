// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.Request;
import dev.restate.common.Target;
import dev.restate.common.reflections.MethodInfo;
import dev.restate.common.reflections.ProxySupport;
import dev.restate.common.reflections.ReflectionUtils;
import dev.restate.serde.TypeTag;

/**
 * Scope service-to-service communication, to send requests to services, virtual objects and
 * workflows within a scope. Requires Restate >= 1.7.
 *
 * <p>Obtain an instance via {@link Restate#scope(String)}.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public final class Scope {

  private final String scope;

  Scope(String scope) {
    this.scope = scope;
  }

  /**
   * @see Restate#service(Class)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> SVC service(Class<SVC> clazz) {
    ReflectionUtils.mustHaveServiceAnnotation(clazz);
    String serviceName = ReflectionUtils.extractServiceName(clazz);
    return ProxySupport.createProxy(
        clazz,
        invocation -> {
          var methodInfo = MethodInfo.fromMethod(invocation.getMethod());

          //noinspection unchecked
          return Context.current()
              .call(
                  Request.of(
                      Target.virtualObject(scope, serviceName, null, methodInfo.getHandlerName()),
                      (TypeTag<? super Object>) methodInfo.getInputType(),
                      (TypeTag<? super Object>) methodInfo.getOutputType(),
                      invocation.getArguments().length == 0 ? null : invocation.getArguments()[0]))
              .await();
        });
  }

  /**
   * @see Restate#serviceHandle(Class)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> ServiceHandle<SVC> serviceHandle(Class<SVC> clazz) {
    ReflectionUtils.mustHaveServiceAnnotation(clazz);
    return new ServiceHandleImpl<>(clazz, null, scope);
  }

  /**
   * @see Restate#virtualObject(Class, String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> SVC virtualObject(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz);
    String serviceName = ReflectionUtils.extractServiceName(clazz);
    return ProxySupport.createProxy(
        clazz,
        invocation -> {
          var methodInfo = MethodInfo.fromMethod(invocation.getMethod());

          //noinspection unchecked
          return Context.current()
              .call(
                  Request.of(
                      Target.virtualObject(scope, serviceName, key, methodInfo.getHandlerName()),
                      (TypeTag<? super Object>) methodInfo.getInputType(),
                      (TypeTag<? super Object>) methodInfo.getOutputType(),
                      invocation.getArguments().length == 0 ? null : invocation.getArguments()[0]))
              .await();
        });
  }

  /**
   * @see Restate#virtualObjectHandle(Class, String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> ServiceHandle<SVC> virtualObjectHandle(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz);
    return new ServiceHandleImpl<>(clazz, key, scope);
  }

  /**
   * @see Restate#workflow(Class, String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> SVC workflow(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveWorkflowAnnotation(clazz);
    String serviceName = ReflectionUtils.extractServiceName(clazz);
    return ProxySupport.createProxy(
        clazz,
        invocation -> {
          var methodInfo = MethodInfo.fromMethod(invocation.getMethod());

          //noinspection unchecked
          return Context.current()
              .call(
                  Request.of(
                      Target.virtualObject(scope, serviceName, key, methodInfo.getHandlerName()),
                      (TypeTag<? super Object>) methodInfo.getInputType(),
                      (TypeTag<? super Object>) methodInfo.getOutputType(),
                      invocation.getArguments().length == 0 ? null : invocation.getArguments()[0]))
              .await();
        });
  }

  /**
   * @see Restate#workflowHandle(Class, String)
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  public <SVC> ServiceHandle<SVC> workflowHandle(Class<SVC> clazz, String key) {
    ReflectionUtils.mustHaveWorkflowAnnotation(clazz);
    return new ServiceHandleImpl<>(clazz, key, scope);
  }
}
