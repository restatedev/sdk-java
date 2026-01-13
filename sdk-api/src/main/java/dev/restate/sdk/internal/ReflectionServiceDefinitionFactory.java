// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.internal;

import dev.restate.common.reflections.ReflectionUtils;
import dev.restate.common.reflections.RestateUtils;
import dev.restate.sdk.*;
import dev.restate.sdk.HandlerRunner;
import dev.restate.sdk.annotation.*;
import dev.restate.sdk.endpoint.definition.*;
import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.provider.DefaultSerdeFactoryProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.*;
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

    boolean hasServiceAnnotation = ReflectionUtils.hasServiceAnnotation(serviceClazz);
    boolean hasVirtualObjectAnnotation = ReflectionUtils.hasVirtualObjectAnnotation(serviceClazz);
    boolean hasWorkflowAnnotation = ReflectionUtils.hasWorkflowAnnotation(serviceClazz);

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
                    || ReflectionUtils.findAnnotation(method, Shared.class) != null
                    || ReflectionUtils.findAnnotation(method, Workflow.class) != null
                    || ReflectionUtils.findAnnotation(method, Exclusive.class) != null);
    if (methods.length == 0) {
      throw new MalformedRestateServiceException(serviceName, "No @Handler method found");
    }
    return ServiceDefinition.of(
        serviceName,
        serviceType,
        Arrays.stream(methods)
            .map(
                method ->
                    this.createHandlerDefinition(
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
    var parameterCount = method.getParameterCount();
    validateMethod(method, serviceName);

    if ((parameterCount == 1 || parameterCount == 2)
        && (genericParameterTypes[0].equals(Context.class)
            || genericParameterTypes[0].equals(SharedObjectContext.class)
            || genericParameterTypes[0].equals(ObjectContext.class)
            || genericParameterTypes[0].equals(WorkflowContext.class)
            || genericParameterTypes[0].equals(SharedWorkflowContext.class))) {
      var ctxTypeName = ((Class<?>) genericParameterTypes[0]).getSimpleName();
      var returnTypeName =
          !method.getGenericReturnType().equals(Void.TYPE)
              ? method.getGenericReturnType().toString()
              : null;
      var restateCtxGetter = ctxTypeName.substring(0, 1).toLowerCase() + ctxTypeName.substring(1);
      throw new MalformedRestateServiceException(
          serviceName,
          MessageFormat.format(
              """
                    The service is being loaded with the new Reflection based API, but handler ''{0}'' contains {1} as first parameter. Suggestions:
                    * If you want to use the new Reflection based API, remove {2} from the method definition and use {3} inside the handler:
                      - {4} '{'
                      -   // code
                      - '}'
                    Replace with:
                      + {5} '{'
                      +   var ctx = Restate.{6}();
                      +   // code
                      + '}
                    * If you''re still using the annotation processor based API, make sure the ServiceDefinitionFactory class was correctly generated.""",
              handlerName,
              ctxTypeName,
              ctxTypeName,
              Restate.class.getName(),
              renderSignature(
                  handlerName,
                  parameterCount == 1
                      ? List.of(Map.entry(ctxTypeName, "ctx"))
                      : List.of(
                          Map.entry(ctxTypeName, "ctx"),
                          Map.entry(genericParameterTypes[1].getTypeName(), "input")),
                  returnTypeName),
              renderSignature(
                  handlerName,
                  parameterCount == 1
                      ? List.of()
                      : List.of(Map.entry(genericParameterTypes[1].getTypeName(), "input")),
                  returnTypeName),
              restateCtxGetter));
    }

    if (parameterCount > 1) {
      throw new MalformedRestateServiceException(
          serviceName,
          "More than one parameter found in method "
              + method.getName()
              + ". Only zero or one parameter is supported.");
    }

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

    Serde<Object> inputSerde = resolveInputSerde(method, serdeFactory, serviceName);
    Serde<Object> outputSerde = resolveOutputSerde(method, serdeFactory, serviceName);

    var runner =
        dev.restate.sdk.HandlerRunner.of(
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

    var handlerDefinition =
        HandlerDefinition.of(handlerName, handlerType, inputSerde, outputSerde, runner);

    // Look for the accept annotation
    if (parameterCount > 0) {
      Accept acceptAnnotation = method.getParameters()[0].getAnnotation(Accept.class);
      if (acceptAnnotation != null) {
        handlerDefinition = handlerDefinition.withAcceptContentType(acceptAnnotation.value());
      }
    }

    return handlerDefinition;
  }

  private static void validateMethod(Method method, String serviceName) {
    if (!Modifier.isPublic(method.getModifiers())) {
      throw new MalformedRestateServiceException(
          serviceName,
          "Method '"
              + method.getName()
              + "' MUST be public to be used as Restate handler. Modifiers:"
              + Modifier.toString(method.getModifiers()));
    }
    if (Modifier.isStatic(method.getModifiers())) {
      throw new MalformedRestateServiceException(
          serviceName,
          "Method '" + method.getName() + "' is static, cannot be used as Restate handler");
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Serde<Object> resolveInputSerde(
      Method method, SerdeFactory serdeFactory, String serviceName) {
    if (method.getParameterCount() == 0) {
      return (Serde) Serde.VOID;
    }

    var inputType = method.getGenericParameterTypes()[0];

    var parameter = method.getParameters()[0];
    Raw rawAnnotation = parameter.getAnnotation(Raw.class);
    Json jsonAnnotation = parameter.getAnnotation(Json.class);

    // Validate annotations
    if (rawAnnotation != null && jsonAnnotation != null) {
      throw new MalformedRestateServiceException(
          serviceName,
          "Parameter in method "
              + method.getName()
              + " cannot be annotated with both @Raw and @Json");
    }

    if (rawAnnotation != null) {
      // Validate parameter type is byte[]
      if (!inputType.equals(byte[].class)) {
        throw new MalformedRestateServiceException(
            serviceName,
            "Parameter annotated with @Raw in method "
                + method.getName()
                + " MUST be of type byte[], was "
                + inputType);
      }
      Serde serde = Serde.RAW;
      // Apply content type if not default
      if (!rawAnnotation.contentType().equals("application/octet-stream")) {
        serde = Serde.withContentType(rawAnnotation.contentType(), serde);
      }
      return serde;
    }

    // Use serdeFactory to create serde
    Serde<Object> serde = (Serde<Object>) serdeFactory.create(RestateUtils.typeTag(inputType));

    // Apply custom content-type from @Json if present
    if (jsonAnnotation != null && !jsonAnnotation.contentType().equals("application/json")) {
      serde = Serde.withContentType(jsonAnnotation.contentType(), serde);
    }

    return serde;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Serde<Object> resolveOutputSerde(
      Method method, SerdeFactory serdeFactory, String serviceName) {
    var outputType = method.getGenericReturnType();
    if (outputType.equals(Void.TYPE)) {
      return (Serde) Serde.VOID;
    }

    Raw rawAnnotation = method.getAnnotation(Raw.class);
    Json jsonAnnotation = method.getAnnotation(Json.class);

    // Validate annotations
    if (rawAnnotation != null && jsonAnnotation != null) {
      throw new MalformedRestateServiceException(
          serviceName,
          "Method " + method.getName() + " cannot be annotated with both @Raw and @Json");
    }

    if (rawAnnotation != null) {
      // Validate return type is byte[]
      if (!outputType.equals(byte[].class)) {
        throw new MalformedRestateServiceException(
            serviceName,
            "Method "
                + method.getName()
                + " annotated with @Raw MUST return byte[], was "
                + outputType);
      }
      Serde serde = Serde.RAW;
      // Apply content type if not default
      if (!rawAnnotation.contentType().equals("application/octet-stream")) {
        serde = Serde.withContentType(rawAnnotation.contentType(), serde);
      }
      return serde;
    }

    // Use serdeFactory to create serde
    Serde<Object> serde = (Serde<Object>) serdeFactory.create(RestateUtils.typeTag(outputType));

    // Apply custom content-type from @Json if present
    if (jsonAnnotation != null && !jsonAnnotation.contentType().equals("application/json")) {
      serde = Serde.withContentType(jsonAnnotation.contentType(), serde);
    }

    return serde;
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

  private String renderSignature(
      String name, List<Map.Entry<String, String>> inputTypes, @Nullable String outputType) {
    return Objects.requireNonNullElse(outputType, "void")
        + " "
        + name
        + "("
        + inputTypes.stream()
            .map(e -> e.getKey() + " " + e.getValue())
            .collect(Collectors.joining(", "))
        + ")";
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
