// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.common.InvocationOptions
import dev.restate.common.Request
import dev.restate.common.Target
import dev.restate.common.reflections.ProxySupport
import dev.restate.common.reflections.ReflectionUtils
import dev.restate.common.reflections.RestateUtils
import dev.restate.sdk.annotation.Raw
import dev.restate.serde.Serde
import dev.restate.serde.TypeTag
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.coroutines.Continuation
import kotlin.reflect.KFunction
import kotlin.reflect.KSuspendFunction1
import kotlin.reflect.KSuspendFunction2
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * Advanced API handle for invoking Restate services, virtual objects, or workflows from Kotlin.
 *
 * This handle provides method reference-based invocation capabilities using Kotlin suspend
 * functions.
 *
 * Example usage:
 * ```kotlin
 * // Create a request for later use
 * val request = serviceHandle<Greeter>().request(Greeter::greet, "Alice")
 *
 * // Call and await
 * val response = request.call(context()).await()
 *
 * // Or send without waiting
 * val handle = request.send(context())
 * ```
 *
 * @param SVC the service interface type
 */
interface KotlinServiceHandle<SVC : Any> {
  /**
   * Create a request for a method with input.
   *
   * @param method method reference (e.g., `Greeter::greet`)
   * @param input the input parameter to pass to the method
   * @return a [Request] that can be used with [Context.call] or [Context.send]
   */
  fun <I, O> request(method: KSuspendFunction2<SVC, I, O>, input: I): Request<I, O>

  /**
   * Create a request for a method without input.
   *
   * @param method method reference (e.g., `Counter::get`)
   * @return a [Request] that can be used with [Context.call] or [Context.send]
   */
  fun <O> request(method: KSuspendFunction1<SVC, O>): Request<Unit, O>

  /**
   * Create a request for a method with input and invocation options.
   *
   * @param method method reference
   * @param input the input parameter
   * @param options invocation options (e.g., idempotency key)
   * @return a [Request] that can be used with [Context.call] or [Context.send]
   */
  fun <I, O> request(
      method: KSuspendFunction2<SVC, I, O>,
      input: I,
      options: InvocationOptions,
  ): Request<I, O>

  /**
   * Create a request for a method without input and with invocation options.
   *
   * @param method method reference
   * @param options invocation options
   * @return a [Request] that can be used with [Context.call] or [Context.send]
   */
  fun <O> request(method: KSuspendFunction1<SVC, O>, options: InvocationOptions): Request<Unit, O>
}

// =============================================================================
// Simple Proxy API
// =============================================================================

/**
 * Create a proxy client for a Restate service.
 *
 * This creates a proxy that allows calling service methods directly. The proxy intercepts method
 * calls, converts them to Restate requests, and awaits the result.
 *
 * Example usage:
 * ```kotlin
 * @Handler
 * suspend fun myHandler(): String {
 *     val greeter = service<Greeter>()
 *     val response = greeter.greet("Alice")
 *     return "Got: $response"
 * }
 * ```
 *
 * @param SVC the service class annotated with @Service
 * @return a proxy client to invoke the service
 */
suspend inline fun <reified SVC : Any> service(): SVC {
  return service(SVC::class.java, context())
}

/**
 * Create a proxy client for a Restate virtual object.
 *
 * Example usage:
 * ```kotlin
 * @Handler
 * suspend fun myHandler(): Long {
 *     val counter = virtualObject<Counter>("my-counter")
 *     return counter.increment()
 * }
 * ```
 *
 * @param SVC the virtual object class annotated with @VirtualObject
 * @param key the key identifying the specific virtual object instance
 * @return a proxy client to invoke the virtual object
 */
suspend inline fun <reified SVC : Any> virtualObject(key: String): SVC {
  return virtualObject(SVC::class.java, key, context())
}

/**
 * Create a proxy client for a Restate workflow.
 *
 * @param SVC the workflow class annotated with @Workflow
 * @param key the key identifying the specific workflow instance
 * @return a proxy client to invoke the workflow
 */
suspend inline fun <reified SVC : Any> workflow(key: String): SVC {
  return workflow(SVC::class.java, key, context())
}

// =============================================================================
// Handle API
// =============================================================================

/**
 * Create a service handle for building requests.
 *
 * Example usage:
 * ```kotlin
 * @Handler
 * suspend fun myHandler(): String {
 *     val request = serviceHandle<Greeter>().request(Greeter::greet, "Alice")
 *     val response = request.call(context()).await()
 *     return response
 * }
 * ```
 *
 * @param SVC the service class annotated with @Service
 * @return a handle to create requests for the service
 */
inline fun <reified SVC : Any> serviceHandle(): KotlinServiceHandle<SVC> {
  return serviceHandle(SVC::class.java)
}

/**
 * Create a virtual object handle for building requests.
 *
 * @param SVC the virtual object class annotated with @VirtualObject
 * @param key the key identifying the specific virtual object instance
 * @return a handle to create requests for the virtual object
 */
inline fun <reified SVC : Any> virtualObjectHandle(key: String): KotlinServiceHandle<SVC> {
  return virtualObjectHandle(SVC::class.java, key)
}

/**
 * Create a workflow handle for building requests.
 *
 * @param SVC the workflow class annotated with @Workflow
 * @param key the key identifying the specific workflow instance
 * @return a handle to create requests for the workflow
 */
inline fun <reified SVC : Any> workflowHandle(key: String): KotlinServiceHandle<SVC> {
  return workflowHandle(SVC::class.java, key)
}

// =============================================================================
// Implementation
// =============================================================================

/** Internal proxy holder that captures the context for proxy invocations. */
private class ContextCapturingProxy<SVC : Any>(
    val serviceName: String,
    val key: String?,
    val ctx: Context,
) {
  var lastCallFuture: CallDurableFuture<Any?>? = null
}

@PublishedApi
internal fun <SVC : Any> service(clazz: Class<SVC>, ctx: Context): SVC {
  ReflectionUtils.mustHaveServiceAnnotation(clazz)
  val serviceName = ReflectionUtils.extractServiceName(clazz)
  val holder = ContextCapturingProxy<SVC>(serviceName, null, ctx)

  return ProxySupport.createProxy(clazz) { invocation ->
    val methodInfo = KotlinMethodInfo.fromMethod(invocation.method)
    val target = Target.service(serviceName, methodInfo.handlerName)

    @Suppress("UNCHECKED_CAST")
    val request =
        Request.of(
            target,
            methodInfo.inputType as TypeTag<Any?>,
            methodInfo.outputType as TypeTag<Any?>,
            if (invocation.arguments.isEmpty()) null else invocation.arguments[0],
        )

    // For suspend functions, we need special handling
    // The proxy intercepts the call and we need to use continuation
    val isSuspend = methodInfo.isSuspendFunction

    if (isSuspend && invocation.arguments.isNotEmpty()) {
      // Last argument is the continuation for suspend functions
      val lastArg = invocation.arguments.last()
      if (lastArg is Continuation<*>) {
        @Suppress("UNCHECKED_CAST") val continuation = lastArg as Continuation<Any?>

        // Run the call in a blocking way for now
        // This is a workaround since we can't easily bridge suspend in the interceptor
        val future = kotlinx.coroutines.runBlocking { holder.ctx.call(request) }

        // Return COROUTINE_SUSPENDED and resume later
        kotlinx.coroutines.runBlocking {
          try {
            val result = future.await()
            continuation.resumeWith(Result.success(result))
          } catch (e: Throwable) {
            continuation.resumeWith(Result.failure(e))
          }
        }
        return@createProxy kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
      }
    }

    // For non-suspend functions, just run blocking
    kotlinx.coroutines.runBlocking { holder.ctx.call(request).await() }
  }
}

@PublishedApi
internal fun <SVC : Any> virtualObject(clazz: Class<SVC>, key: String, ctx: Context): SVC {
  ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz)
  val serviceName = ReflectionUtils.extractServiceName(clazz)

  return ProxySupport.createProxy(clazz) { invocation ->
    val methodInfo = KotlinMethodInfo.fromMethod(invocation.method)
    val target = Target.virtualObject(serviceName, key, methodInfo.handlerName)

    @Suppress("UNCHECKED_CAST")
    val request =
        Request.of(
            target,
            methodInfo.inputType as TypeTag<Any?>,
            methodInfo.outputType as TypeTag<Any?>,
            if (invocation.arguments.isEmpty()) null else invocation.arguments[0],
        )

    val isSuspend = methodInfo.isSuspendFunction

    if (isSuspend && invocation.arguments.isNotEmpty()) {
      val lastArg = invocation.arguments.last()
      if (lastArg is Continuation<*>) {
        @Suppress("UNCHECKED_CAST") val continuation = lastArg as Continuation<Any?>

        kotlinx.coroutines.runBlocking {
          try {
            val result = ctx.call(request).await()
            continuation.resumeWith(Result.success(result))
          } catch (e: Throwable) {
            continuation.resumeWith(Result.failure(e))
          }
        }
        return@createProxy kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
      }
    }

    kotlinx.coroutines.runBlocking { ctx.call(request).await() }
  }
}

@PublishedApi
internal fun <SVC : Any> workflow(clazz: Class<SVC>, key: String, ctx: Context): SVC {
  ReflectionUtils.mustHaveWorkflowAnnotation(clazz)
  val serviceName = ReflectionUtils.extractServiceName(clazz)

  return ProxySupport.createProxy(clazz) { invocation ->
    val methodInfo = KotlinMethodInfo.fromMethod(invocation.method)
    val target = Target.virtualObject(serviceName, key, methodInfo.handlerName)

    @Suppress("UNCHECKED_CAST")
    val request =
        Request.of(
            target,
            methodInfo.inputType as TypeTag<Any?>,
            methodInfo.outputType as TypeTag<Any?>,
            if (invocation.arguments.isEmpty()) null else invocation.arguments[0],
        )

    val isSuspend = methodInfo.isSuspendFunction

    if (isSuspend && invocation.arguments.isNotEmpty()) {
      val lastArg = invocation.arguments.last()
      if (lastArg is Continuation<*>) {
        @Suppress("UNCHECKED_CAST") val continuation = lastArg as Continuation<Any?>

        kotlinx.coroutines.runBlocking {
          try {
            val result = ctx.call(request).await()
            continuation.resumeWith(Result.success(result))
          } catch (e: Throwable) {
            continuation.resumeWith(Result.failure(e))
          }
        }
        return@createProxy kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
      }
    }

    kotlinx.coroutines.runBlocking { ctx.call(request).await() }
  }
}

@PublishedApi
internal fun <SVC : Any> serviceHandle(clazz: Class<SVC>): KotlinServiceHandle<SVC> {
  ReflectionUtils.mustHaveServiceAnnotation(clazz)
  val serviceName = ReflectionUtils.extractServiceName(clazz)
  return KotlinServiceHandleImpl(serviceName, null)
}

@PublishedApi
internal fun <SVC : Any> virtualObjectHandle(
    clazz: Class<SVC>,
    key: String,
): KotlinServiceHandle<SVC> {
  ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz)
  val serviceName = ReflectionUtils.extractServiceName(clazz)
  return KotlinServiceHandleImpl(serviceName, key)
}

@PublishedApi
internal fun <SVC : Any> workflowHandle(clazz: Class<SVC>, key: String): KotlinServiceHandle<SVC> {
  ReflectionUtils.mustHaveWorkflowAnnotation(clazz)
  val serviceName = ReflectionUtils.extractServiceName(clazz)
  return KotlinServiceHandleImpl(serviceName, key)
}

private class KotlinServiceHandleImpl<SVC : Any>(
    private val serviceName: String,
    private val key: String?,
) : KotlinServiceHandle<SVC> {

  override fun <I, O> request(method: KSuspendFunction2<SVC, I, O>, input: I): Request<I, O> {
    val methodInfo = extractMethodInfo(method)
    val target =
        if (key != null) {
          Target.virtualObject(serviceName, key, methodInfo.handlerName)
        } else {
          Target.service(serviceName, methodInfo.handlerName)
        }

    @Suppress("UNCHECKED_CAST")
    return Request.of(
        target,
        methodInfo.inputType as TypeTag<I>,
        methodInfo.outputType as TypeTag<O>,
        input,
    )
  }

  override fun <O> request(method: KSuspendFunction1<SVC, O>): Request<Unit, O> {
    val methodInfo = extractMethodInfo(method)
    val target =
        if (key != null) {
          Target.virtualObject(serviceName, key, methodInfo.handlerName)
        } else {
          Target.service(serviceName, methodInfo.handlerName)
        }

    @Suppress("UNCHECKED_CAST")
    return Request.of(
        target,
        Serde.VOID as TypeTag<Unit>,
        methodInfo.outputType as TypeTag<O>,
        Unit,
    )
  }

  override fun <I, O> request(
      method: KSuspendFunction2<SVC, I, O>,
      input: I,
      options: InvocationOptions,
  ): Request<I, O> {
    val methodInfo = extractMethodInfo(method)
    val target =
        if (key != null) {
          Target.virtualObject(serviceName, key, methodInfo.handlerName)
        } else {
          Target.service(serviceName, methodInfo.handlerName)
        }

    @Suppress("UNCHECKED_CAST")
    var builder =
        Request.of(
            target,
            methodInfo.inputType as TypeTag<I>,
            methodInfo.outputType as TypeTag<O>,
            input,
        )

    options.idempotencyKey?.let { builder = builder.idempotencyKey(it) }
    options.headers?.let { headers ->
      for ((k, v) in headers) {
        builder = builder.header(k, v)
      }
    }

    return builder
  }

  override fun <O> request(
      method: KSuspendFunction1<SVC, O>,
      options: InvocationOptions,
  ): Request<Unit, O> {
    val methodInfo = extractMethodInfo(method)
    val target =
        if (key != null) {
          Target.virtualObject(serviceName, key, methodInfo.handlerName)
        } else {
          Target.service(serviceName, methodInfo.handlerName)
        }

    @Suppress("UNCHECKED_CAST")
    var builder =
        Request.of(target, Serde.VOID as TypeTag<Unit>, methodInfo.outputType as TypeTag<O>, Unit)

    options.idempotencyKey?.let { builder = builder.idempotencyKey(it) }
    options.headers?.let { headers ->
      for ((k, v) in headers) {
        builder = builder.header(k, v)
      }
    }

    return builder
  }

  private fun extractMethodInfo(method: KFunction<*>): KotlinMethodInfo {
    val javaMethod = method.javaMethod ?: error("Cannot extract Java method from KFunction")
    return KotlinMethodInfo.fromMethod(javaMethod)
  }
}

/** Kotlin-aware method info extraction that handles suspend functions properly. */
internal class KotlinMethodInfo(
    val handlerName: String,
    val inputType: TypeTag<*>,
    val outputType: TypeTag<*>,
    val isSuspendFunction: Boolean,
) {
  companion object {
    fun fromMethod(method: Method): KotlinMethodInfo {
      val handlerInfo = ReflectionUtils.mustHaveHandlerAnnotation(method)
      val handlerName = handlerInfo.name

      // Check if this is a Kotlin suspend function
      val kFunction = method.kotlinFunction
      val isSuspend = kFunction?.isSuspend == true

      val genericParameters = method.genericParameterTypes
      val paramCount = method.parameterCount

      // For suspend functions, last param is Continuation<T>
      val effectiveParamCount = if (isSuspend) paramCount - 1 else paramCount

      val inputTypeTag: TypeTag<*> =
          when {
            effectiveParamCount == 0 -> Serde.VOID
            else ->
                resolveTypeTag(
                    genericParameters[0],
                    method.parameters[0].getAnnotation(Raw::class.java),
                )
          }

      val outputTypeTag: TypeTag<*> =
          if (isSuspend) {
            // Extract return type from Continuation<T>
            val continuationType = genericParameters[paramCount - 1]
            val returnType = extractSuspendReturnType(continuationType)
            if (returnType == null || returnType.typeName == "kotlin.Unit") {
              Serde.VOID
            } else {
              resolveTypeTag(returnType, method.getAnnotation(Raw::class.java))
            }
          } else {
            resolveTypeTag(method.genericReturnType, method.getAnnotation(Raw::class.java))
          }

      return KotlinMethodInfo(handlerName, inputTypeTag, outputTypeTag, isSuspend)
    }

    private fun extractSuspendReturnType(continuationType: Type): Type? {
      if (continuationType is ParameterizedType) {
        val rawType = continuationType.rawType
        if (rawType == Continuation::class.java) {
          val typeArg = continuationType.actualTypeArguments[0]
          // Handle wildcard types like "? super T"
          if (typeArg is java.lang.reflect.WildcardType) {
            val lowerBounds = typeArg.lowerBounds
            if (lowerBounds.isNotEmpty()) {
              return lowerBounds[0]
            }
          }
          return typeArg
        }
      }
      return null
    }

    private fun resolveTypeTag(type: Type?, rawAnnotation: Raw?): TypeTag<*> {
      if (type == null || type == Void.TYPE) {
        return Serde.VOID
      }

      if (rawAnnotation != null && rawAnnotation.contentType != "application/octet-stream") {
        return Serde.withContentType(rawAnnotation.contentType, Serde.RAW)
      } else if (rawAnnotation != null) {
        return Serde.RAW
      }

      return RestateUtils.typeTag(type)
    }
  }
}
