// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.internal;

import dev.restate.common.function.ThrowingBiFunction;
import dev.restate.common.reflections.ReflectionUtils;
import dev.restate.common.reflections.RestateUtils;
import dev.restate.sdk.Context;
import dev.restate.sdk.HandlerRunner;
import dev.restate.sdk.MalformedRestateServiceException;
import dev.restate.sdk.annotation.*;
import dev.restate.sdk.endpoint.definition.*;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.provider.DefaultSerdeFactoryProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

@org.jetbrains.annotations.ApiStatus.Experimental
@org.jetbrains.annotations.ApiStatus.Internal
public final class ReflectionServiceDefinitionFactory implements ServiceDefinitionFactory<Object> {

  private volatile SerdeFactory cachedDefaultSerdeFactory;

  @Override
  public ServiceDefinition create(
      Object serviceInstance,
      dev.restate.sdk.endpoint.definition.HandlerRunner.Options overrideHandlerOptions) {
    dev.restate.sdk.HandlerRunner.Options handlerRunnerOptions;
    if (overrideHandlerOptions == null
        || overrideHandlerOptions instanceof dev.restate.sdk.HandlerRunner.Options) {
      handlerRunnerOptions = (dev.restate.sdk.HandlerRunner.Options) overrideHandlerOptions;
    } else {
      throw new IllegalArgumentException(
          "The provided options class MUST be instance of dev.restate.sdk.HandlerRunner.Options, but was "
              + overrideHandlerOptions.getClass());
    }

    Class<?> serviceClazz = serviceInstance.getClass();

    boolean hasServiceAnnotation =
        ReflectionUtils.findAnnotation(serviceClazz, Service.class) != null;
    boolean hasVirtualObjectAnnotation =
        ReflectionUtils.findAnnotation(serviceClazz, VirtualObject.class) != null;
    boolean hasWorkflowAnnotation =
        ReflectionUtils.findAnnotation(serviceClazz, Workflow.class) != null;

    boolean hasAnyAnnotation =
        hasServiceAnnotation || hasVirtualObjectAnnotation || hasWorkflowAnnotation;
    if (!hasAnyAnnotation) {
      throw new MalformedRestateServiceException(
          serviceClazz.getSimpleName(),
          "A restate component MUST be annotated with "
              + "exactly one annotation between @Service/@VirtualObject/@Workflow, no annotation was found");
    }
    boolean hasExactlyOneAnnotation =
        Boolean.logicalXor(
            hasServiceAnnotation,
            Boolean.logicalXor(hasVirtualObjectAnnotation, hasWorkflowAnnotation));

    if (!hasExactlyOneAnnotation) {
      throw new MalformedRestateServiceException(
          serviceClazz.getSimpleName(),
          "A restate component MUST be annotated with "
              + "exactly one annotation between @Service/@VirtualObject/@Workflow, more than one annotation found");
    }

    var serviceName = ReflectionUtils.extractServiceName(serviceClazz);
    var serviceType =
        hasServiceAnnotation
            ? ServiceType.SERVICE
            : hasVirtualObjectAnnotation ? ServiceType.VIRTUAL_OBJECT : ServiceType.WORKFLOW;
    var serdeFactory = resolveSerdeFactory(serviceClazz);

    var methods =
        ReflectionUtils.getUniqueDeclaredMethods(
            serviceClazz,
            method ->
                ReflectionUtils.findAnnotation(method, Handler.class) != null
                    || ReflectionUtils.findAnnotation(method, Shared.class) != null);
    if (methods.length == 0) {
      throw new MalformedRestateServiceException(serviceName, "No @Handler method found");
    }
    return ServiceDefinition.of(
        serviceName,
        serviceType,
        Arrays.stream(methods)
            .map(
                method ->
                    createHandlerDefinition(
                        serviceInstance,
                        method,
                        serviceName,
                        serviceType,
                        serdeFactory,
                        handlerRunnerOptions))
            .collect(Collectors.toUnmodifiableList()));
  }

  private HandlerDefinition<?, ?> createHandlerDefinition(
      Object serviceInstance,
      Method method,
      String serviceName,
      ServiceType serviceType,
      SerdeFactory serdeFactory,
      HandlerRunner.@Nullable Options overrideHandlerOptions) {
    var handlerInfo = ReflectionUtils.mustHaveHandlerAnnotation(method);
    var handlerName = handlerInfo.name();
    var genericParameterTypes = method.getGenericParameterTypes();
    if (genericParameterTypes.length > 1) {
      throw new MalformedRestateServiceException(
          serviceName,
          "More than one parameter found in method "
              + method.getName()
              + ". Only one parameter is supported.");
    }
    var inputType = genericParameterTypes.length == 0 ? Void.TYPE : genericParameterTypes[0];
    var outputType = method.getGenericReturnType();

    if (serviceType == ServiceType.SERVICE && handlerInfo.shared()) {
      throw new MalformedRestateServiceException(
          serviceName, "@Shared is only supported on virtual objects and workflow handlers");
    }
    var handlerType =
        handlerInfo.shared()
            ? HandlerType.SHARED
            : serviceType == ServiceType.VIRTUAL_OBJECT
                ? HandlerType.EXCLUSIVE
                : serviceType == ServiceType.WORKFLOW ? HandlerType.WORKFLOW : null;

    var parameterCount = method.getParameterCount();

    // TODO here we should add some code to handle handling Context in method definition.
    // This is because we want to make sure people declaring the handlers with the Context in the
    // method works
    // providing a smoother path to transition from code generation
    // Plus plus plus important bit -> we need to validate the input paramters can be one and only
    // one (OBV)!

    var runner =
        dev.restate.sdk.HandlerRunner.of(
            (ThrowingBiFunction<Context, Object, Object>)
                (ctx, in) -> {
                  try {
                    if (parameterCount == 0) {
                      return method.invoke(serviceInstance);
                    } else {
                      return method.invoke(serviceInstance, in);
                    }
                  } catch (InvocationTargetException e) {
                    throw e.getCause();
                  }
                },
            serdeFactory,
            overrideHandlerOptions);

    //noinspection unchecked
    return HandlerDefinition.of(
        handlerName,
        handlerType,
        (Serde<Object>) serdeFactory.create(RestateUtils.typeTag(inputType)),
        (Serde<Object>) serdeFactory.create(RestateUtils.typeTag(outputType)),
        runner);
  }

  private SerdeFactory resolveSerdeFactory(Class<?> serviceClazz) {
    // Check for CustomSerdeFactory annotation
    CustomSerdeFactory customSerdeFactoryAnnotation =
        ReflectionUtils.findAnnotation(serviceClazz, CustomSerdeFactory.class);

    if (customSerdeFactoryAnnotation != null) {
      try {
        return customSerdeFactoryAnnotation.value().getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new MalformedRestateServiceException(
            serviceClazz.getSimpleName(),
            "Failed to instantiate custom SerdeFactory: "
                + customSerdeFactoryAnnotation.value().getName(),
            e);
      }
    }

    // Try DefaultSerdeFactoryProvider -> if there's one, it's an easy pick!
    if (this.cachedDefaultSerdeFactory != null) {
      return this.cachedDefaultSerdeFactory;
    }

    var loadedFactories = ServiceLoader.load(DefaultSerdeFactoryProvider.class).stream().toList();
    if (loadedFactories.size() == 1) {
      this.cachedDefaultSerdeFactory = loadedFactories.get(0).get().create();
      return this.cachedDefaultSerdeFactory;
    }

    // Load Jackson serde factory
    try {
      Class<?> jacksonSerdeFactoryClass =
          Class.forName("dev.restate.serde.jackson.JacksonSerdeFactory");
      Object defaultInstance = jacksonSerdeFactoryClass.getField("DEFAULT").get(null);
      this.cachedDefaultSerdeFactory = (SerdeFactory) defaultInstance;
      return this.cachedDefaultSerdeFactory;
    } catch (Exception e) {
      throw new MalformedRestateServiceException(
          serviceClazz.getSimpleName(),
          "Failed to load JacksonSerdeFactory for Java service. "
              + "Make sure sdk-serde-jackson is on the classpath.",
          e);
    }
  }

  @Override
  public boolean supports(Object serviceObject) {
    return true;
  }

  @Override
  public int priority() {
    // Run last - after code-generated factories
    return LOWEST_PRIORITY;
  }
}
