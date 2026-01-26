// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.internal

import dev.restate.common.reflections.ReflectionUtils
import dev.restate.common.reflections.RestateUtils
import dev.restate.sdk.annotation.Accept
import dev.restate.sdk.annotation.CustomSerdeFactory
import dev.restate.sdk.annotation.Exclusive
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Json
import dev.restate.sdk.annotation.Raw
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.Workflow
import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.endpoint.definition.HandlerRunner
import dev.restate.sdk.endpoint.definition.HandlerType
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.endpoint.definition.ServiceDefinitionFactory
import dev.restate.sdk.endpoint.definition.ServiceType
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.SharedObjectContext
import dev.restate.sdk.kotlin.SharedWorkflowContext
import dev.restate.sdk.kotlin.WorkflowContext
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import dev.restate.serde.kotlinx.KotlinSerializationSerdeFactory
import dev.restate.serde.kotlinx.KotlinSerializationSerdeFactory.KtTypeTag
import dev.restate.serde.provider.DefaultSerdeFactoryProvider
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.text.MessageFormat
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.typeOf

internal class ReflectionServiceDefinitionFactory : ServiceDefinitionFactory<Any> {
  @Volatile private var cachedDefaultSerdeFactory: SerdeFactory? = null

  override fun create(
      serviceInstance: Any,
      overrideHandlerOptions: HandlerRunner.Options?,
  ): ServiceDefinition {
    val handlerRunnerOptions: dev.restate.sdk.kotlin.HandlerRunner.Options?
    if (
        overrideHandlerOptions == null ||
            overrideHandlerOptions is dev.restate.sdk.kotlin.HandlerRunner.Options
    ) {
      handlerRunnerOptions = overrideHandlerOptions
    } else {
      throw IllegalArgumentException(
          "The provided options class MUST be instance of dev.restate.sdk.kotlin.HandlerRunner.Options, but was " +
              overrideHandlerOptions.javaClass
      )
    }

    val serviceClazz: Class<*> = serviceInstance.javaClass

    val hasServiceAnnotation = ReflectionUtils.hasServiceAnnotation(serviceClazz)
    val hasVirtualObjectAnnotation = ReflectionUtils.hasVirtualObjectAnnotation(serviceClazz)
    val hasWorkflowAnnotation = ReflectionUtils.hasWorkflowAnnotation(serviceClazz)

    val hasAnyAnnotation =
        hasServiceAnnotation || hasVirtualObjectAnnotation || hasWorkflowAnnotation
    if (!hasAnyAnnotation) {
      throw MalformedRestateServiceException(
          serviceClazz.simpleName,
          "A restate component MUST be annotated with " +
              "exactly one annotation between @Service/@VirtualObject/@Workflow, no annotation was found",
      )
    }
    val hasExactlyOneAnnotation =
        hasServiceAnnotation xor (hasVirtualObjectAnnotation xor hasWorkflowAnnotation)

    if (!hasExactlyOneAnnotation) {
      throw MalformedRestateServiceException(
          serviceClazz.simpleName,
          "A restate component MUST be annotated with " +
              "exactly one annotation between @Service/@VirtualObject/@Workflow, more than one annotation found",
      )
    }

    val serviceName = ReflectionUtils.extractServiceName(serviceClazz)
    val serviceType =
        if (hasServiceAnnotation) ServiceType.SERVICE
        else if (hasVirtualObjectAnnotation) ServiceType.VIRTUAL_OBJECT else ServiceType.WORKFLOW
    val serdeFactory: SerdeFactory = resolveSerdeFactory(serviceClazz)

    val methods =
        ReflectionUtils.getUniqueDeclaredMethods(serviceClazz) { method: Method? ->
          ReflectionUtils.findAnnotation(method, Handler::class.java) != null ||
              ReflectionUtils.findAnnotation(method, Shared::class.java) != null ||
              ReflectionUtils.findAnnotation(method, Workflow::class.java) != null ||
              ReflectionUtils.findAnnotation(method, Exclusive::class.java) != null
        }
    if (methods.isEmpty()) {
      throw MalformedRestateServiceException(serviceName, "No @Handler method found")
    }
    return ServiceDefinition.of(
        serviceName,
        serviceType,
        methods
            .map { method ->
              this.createHandlerDefinition(
                  serviceInstance,
                  method!!,
                  serviceName,
                  serviceType,
                  serdeFactory,
                  handlerRunnerOptions,
              )
            }
            .toList(),
    )
  }

  private fun createHandlerDefinition(
      serviceInstance: Any,
      method: Method,
      serviceName: String,
      serviceType: ServiceType,
      serdeFactory: SerdeFactory,
      overrideHandlerOptions: dev.restate.sdk.kotlin.HandlerRunner.Options?,
  ): HandlerDefinition<*, *> {
    val handlerInfo: ReflectionUtils.HandlerInfo = ReflectionUtils.mustHaveHandlerAnnotation(method)
    val handlerName: String? = handlerInfo.name
    validateMethod(method, serviceName)

    // Check if this is a Kotlin suspend function
    val kFunction = method.kotlinFunction ?: throw MalformedRestateServiceException(
      serviceName,
      "Method '${method.name}' is not a kotlin function.",
    )
    if (!kFunction.isSuspend) {
      throw MalformedRestateServiceException(
          serviceName,
          "Method '${method.name}' is not a suspend function, this is not supported.",
      )
    }

    val parameters = kFunction.valueParameters

    // Check for old-style context parameter
    if (
        (parameters.size == 1 || parameters.size == 2) &&
            (parameters[0] == Context::class.java ||
                parameters[0] == SharedObjectContext::class.java ||
                parameters[0] == ObjectContext::class.java ||
                parameters[0] == WorkflowContext::class.java ||
                parameters[0] == SharedWorkflowContext::class.java)
    ) {
      // TODO fix this
      val ctxTypeName = parameters[0].type.toString()
      val returnTypeName = kFunction.returnType.toString()
      val actualSignature =   if (parameters.size == 1) "ctx: $ctxTypeName"
      else "ctx: $ctxTypeName, input: ${parameters[1].type}"
      val expectedSignature =   if (parameters.isEmpty()) ""
      else "input: ${parameters[1].type}"
      throw MalformedRestateServiceException(
          serviceName,

              """
              The service is being loaded with the new Reflection based API, but handler '${handlerName}' contains $ctxTypeName as first parameter. Suggestions:
              * If you want to use the new Reflection based API, remove $ctxTypeName from the method definition and use the functions from dev.restate.sdk.kotlin inside the handler:
                - suspend fun ${handlerName}(${actualSignature}): $returnTypeName {
                -   // code
                - }
              Replace with:
                + suspend fun ${handlerName}(${expectedSignature}): $returnTypeName {
                +   // Use functions from dev.restate.sdk.kotlin.*
                +   // code
                + }
              * If you''re still using the KSP based API, make sure the ServiceDefinitionFactory class was correctly generated.
              """
                  .trimIndent()
          ,
      )
    }

    if (parameters.size > 1) {
      throw MalformedRestateServiceException(
          serviceName,
        "More than one parameter found in method ${method.name}. Only zero or one parameter is supported.",
      )
    }

    if (serviceType == ServiceType.SERVICE && handlerInfo.shared) {
      throw MalformedRestateServiceException(
          serviceName,
          "@Shared is only supported on virtual objects and workflow handlers",
      )
    }
    val handlerType =
        if (handlerInfo.shared) HandlerType.SHARED
        else if (serviceType == ServiceType.VIRTUAL_OBJECT) HandlerType.EXCLUSIVE
        else if (serviceType == ServiceType.WORKFLOW) HandlerType.WORKFLOW else null

    val inputSerde =
        resolveInputSerde(
            kFunction,
            serdeFactory,
            serviceName,
        )
    val outputSerde = resolveOutputSerde(kFunction, serdeFactory, serviceName)

    val runner =
      createSuspendHandlerRunner(
          serviceInstance,
          method,
          parameters.size,
          serdeFactory,
          overrideHandlerOptions,
      )

    var handlerDefinition: HandlerDefinition<Any?, Any?> =
        HandlerDefinition.of<Any?, Any?>(handlerName, handlerType, inputSerde, outputSerde, runner)

    // Look for the accept annotation
    if (parameters.isNotEmpty()) {
      val acceptAnnotation: Accept? = parameters[0].findAnnotation<Accept>()
      if (acceptAnnotation != null) {
        handlerDefinition = handlerDefinition.withAcceptContentType(acceptAnnotation.value)
      }
    }

    return handlerDefinition
  }

  private fun createSuspendHandlerRunner(
      serviceInstance: Any,
      method: Method,
      parameterCount: Int,
      serdeFactory: SerdeFactory,
      overrideHandlerOptions: dev.restate.sdk.kotlin.HandlerRunner.Options?,
  ): dev.restate.sdk.kotlin.HandlerRunner<Any?, Any?, Context> {
    return dev.restate.sdk.kotlin.HandlerRunner.of(
        serdeFactory,
        overrideHandlerOptions ?: dev.restate.sdk.kotlin.HandlerRunner.Options.DEFAULT,
    ) { _, input ->
      invokeSuspendFunction(method, serviceInstance, input, parameterCount)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun invokeSuspendFunction(
      method: Method,
      instance: Any,
      input: Any?,
      effectiveParameterCount: Int,
  ): Any? {
    return suspendCoroutineUninterceptedOrReturn { continuation ->
      try {
        if (effectiveParameterCount == 0) {
          method.invoke(instance, continuation)
        } else {
          method.invoke(instance, input, continuation)
        }
      } catch (e: InvocationTargetException) {
        throw e.cause ?: e
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun resolveInputSerde(
      kFunction: KFunction<*>,
      serdeFactory: SerdeFactory,
      serviceName: String,
  ): Serde<Any?> {
    if (kFunction.valueParameters.isEmpty()) {
      return KotlinSerializationSerdeFactory.UNIT as Serde<Any?>
    }

    val parameter = kFunction.valueParameters[0]

    val rawAnnotation = parameter.findAnnotation<Raw>()
    val jsonAnnotation = parameter.findAnnotation<Json>()

    // Validate annotations
    if (rawAnnotation != null && jsonAnnotation != null) {
      throw MalformedRestateServiceException(
          serviceName,
        "Parameter in method ${kFunction.name} cannot be annotated with both @Raw and @Json",
      )
    }

    if (rawAnnotation != null) {
      // Validate parameter type is byte[]
      if (parameter.type.jvmErasure != ByteArray::class) {
        throw MalformedRestateServiceException(
            serviceName,
          "Parameter annotated with @Raw in method ${kFunction.name} MUST be of type ByteArray, was ${parameter.type}",
        )
      }
      var serde: Serde<Any?> = Serde.RAW as Serde<Any?>
      // Apply content type if not default
      if (rawAnnotation.contentType != "application/octet-stream") {
        serde = Serde.withContentType(rawAnnotation.contentType, serde)
      }
      return serde
    }

    // Use serdeFactory to create serde
    var serde = serdeFactory.create<Any?>(
      KtTypeTag(parameter.type.jvmErasure, parameter.type)
    ) as Serde<Any?>

    // Apply custom content-type from @Json if present
    if (jsonAnnotation != null && jsonAnnotation.contentType != "application/json") {
      serde = Serde.withContentType(jsonAnnotation.contentType, serde)
    }

    return serde
  }

  @Suppress("UNCHECKED_CAST")
  private fun resolveOutputSerde(
    kFunction: KFunction<*>,
      serdeFactory: SerdeFactory,
      serviceName: String,
  ): Serde<Any?> {
    val outputType =
     kFunction.returnType

    // Handle Unit type (Kotlin void equivalent)
    if (
        outputType == Void.TYPE ||
            outputType.jvmErasure == Unit::class
    ) {
      return KotlinSerializationSerdeFactory.UNIT as Serde<Any?>
    }

    val rawAnnotation = kFunction.findAnnotation<Raw>()
    val jsonAnnotation = kFunction.findAnnotation<Json>()

    // Validate annotations
    if (rawAnnotation != null && jsonAnnotation != null) {
      throw MalformedRestateServiceException(
          serviceName,
        "Method ${kFunction.name} cannot be annotated with both @Raw and @Json",
      )
    }

    if (rawAnnotation != null) {
      // Validate return type is byte[]
      if (outputType.jvmErasure != ByteArray::class) {
        throw MalformedRestateServiceException(
            serviceName,
          "Method ${kFunction.name} annotated with @Raw MUST return byte[], was $outputType",
        )
      }
      var serde: Serde<Any?> = Serde.RAW as Serde<Any?>
      // Apply content type if not default
      if (rawAnnotation.contentType != "application/octet-stream") {
        serde = Serde.withContentType(rawAnnotation.contentType, serde)
      }
      return serde
    }

    // Use serdeFactory to create serde
    var serde = serdeFactory.create<Any?>(
      KtTypeTag(outputType.jvmErasure, outputType)
    ) as Serde<Any?>

    // Apply custom content-type from @Json if present
    if (jsonAnnotation != null && jsonAnnotation.contentType != "application/json") {
      serde = Serde.withContentType(jsonAnnotation.contentType, serde)
    }

    return serde
  }

  private fun resolveSerdeFactory(serviceClazz: Class<*>): SerdeFactory {
    // Check for CustomSerdeFactory annotation
    val customSerdeFactoryAnnotation: CustomSerdeFactory? =
        ReflectionUtils.findAnnotation(
            serviceClazz,
            CustomSerdeFactory::class.java,
        )

    if (customSerdeFactoryAnnotation != null) {
      try {
        return customSerdeFactoryAnnotation.value.java.getDeclaredConstructor().newInstance()
      } catch (e: Exception) {
        throw MalformedRestateServiceException(
            serviceClazz.simpleName,
          "Failed to instantiate custom SerdeFactory: ${customSerdeFactoryAnnotation.value.java.name}",
            e,
        )
      }
    }

    // Try DefaultSerdeFactoryProvider -> if there's one, it's an easy pick!
    if (this.cachedDefaultSerdeFactory != null) {
      return this.cachedDefaultSerdeFactory!!
    }

    val loadedFactories: MutableList<ServiceLoader.Provider<DefaultSerdeFactoryProvider?>?> =
        ServiceLoader.load(DefaultSerdeFactoryProvider::class.java)
            .stream()
            .toList()
    if (loadedFactories.size == 1) {
      this.cachedDefaultSerdeFactory = loadedFactories[0]!!.get()!!.create()
      return this.cachedDefaultSerdeFactory!!
    }

    // Load kotlinx serde factory
    try {
      val jacksonSerdeFactoryClass = Class.forName("dev.restate.serde.kotlinx.KotlinSerializationSerdeFactory")
      val defaultInstance = jacksonSerdeFactoryClass.getConstructor().newInstance()
      this.cachedDefaultSerdeFactory = defaultInstance as SerdeFactory?
      return this.cachedDefaultSerdeFactory!!
    } catch (e: Exception) {
      throw MalformedRestateServiceException(
          serviceClazz.simpleName,
          "Failed to load KotlinSerializationSerdeFactory for Kotlin service. " +
              "Make sure sdk-serde-kotlinx is on the classpath.",
          e,
      )
    }
  }

  override fun supports(serviceObject: Any?): Boolean {
    return serviceObject?.javaClass?.let { ReflectionUtils.isKotlinClass(it) } ?: false
  }

  override fun priority(): Int {
    // Run before last - after code-generated factories, before java
    return ServiceDefinitionFactory.LOWEST_PRIORITY - 1
  }

  companion object {
    private fun validateMethod(method: Method, serviceName: String) {
      if (!Modifier.isPublic(method.modifiers)) {
        throw MalformedRestateServiceException(
            serviceName,
            "Method '" +
                method.name +
                "' MUST be public to be used as Restate handler. Modifiers:" +
                Modifier.toString(method.modifiers),
        )
      }
      if (Modifier.isStatic(method.modifiers)) {
        throw MalformedRestateServiceException(
            serviceName,
            "Method '" + method.name + "' is static, cannot be used as Restate handler",
        )
      }
    }
  }
}
