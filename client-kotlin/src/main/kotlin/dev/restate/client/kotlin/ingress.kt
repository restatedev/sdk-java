// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client.kotlin

import dev.restate.client.Client
import dev.restate.client.RequestOptions
import dev.restate.client.Response
import dev.restate.client.ResponseHead
import dev.restate.client.SendResponse
import dev.restate.common.Output
import dev.restate.common.Request
import dev.restate.common.Target
import dev.restate.common.WorkflowRequest
import dev.restate.serde.TypeTag
import dev.restate.serde.kotlinx.typeTag
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.future.await

// Extension methods for the Client

/** Request options builder function */
fun requestOptions(init: RequestOptions.Builder.() -> Unit): RequestOptions {
  val builder = RequestOptions.builder()
  builder.init()
  return builder.build()
}

/**
 * Shorthand for [callSuspend]
 *
 * @param client the client to use for the call
 * @return the response
 */
suspend fun <Req, Res> Request<Req, Res>.call(client: Client): Response<Res> {
  return client.callSuspend(this)
}

/** Call a service and wait for the response. */
suspend fun <Req, Res> Client.callSuspend(request: Request<Req, Res>): Response<Res> {
  return this.callAsync(request).await()
}

/**
 * Shorthand for [sendSuspend]
 *
 * @param client the client to use for sending
 * @param delay optional execution delay
 * @return the send response
 */
suspend fun <Req, Res> Request<Req, Res>.send(
    client: Client,
    delay: Duration? = null,
): SendResponse<Res> {
  return client.sendSuspend(this, delay)
}

/**
 * Send a request to a service without waiting for the response, optionally providing an execution
 * delay to wait for.
 */
suspend fun <Req, Res> Client.sendSuspend(
    request: Request<Req, Res>,
    delay: Duration? = null,
): SendResponse<Res> {
  return this.sendAsync(request, delay?.toJavaDuration()).await()
}

/**
 * Shorthand for [submitSuspend]
 *
 * @param client the client to use for submission
 * @param delay optional execution delay
 * @return the send response
 */
suspend fun <Req, Res> WorkflowRequest<Req, Res>.submit(
    client: Client,
    delay: Duration? = null,
): SendResponse<Res> {
  return client.submitSuspend(this, delay)
}

/** Submit a workflow, optionally providing an execution delay to wait for. */
suspend fun <Req, Res> Client.submitSuspend(
    request: WorkflowRequest<Req, Res>,
    delay: Duration? = null,
): SendResponse<Res> {
  return this.submitAsync(request, delay?.toJavaDuration()).await()
}

/**
 * Complete with success the Awakeable.
 *
 * @param typeTag the type tag for serialization
 * @param payload the payload
 * @param options request options
 */
suspend fun <T : Any> Client.AwakeableHandle.resolveSuspend(
    typeTag: TypeTag<T>,
    payload: T,
    options: RequestOptions = RequestOptions.DEFAULT,
): Response<Void> {
  return this.resolveAsync(typeTag, payload, options).await()
}

/**
 * Complete with success the Awakeable.
 *
 * @param payload the payload
 * @param options request options
 */
suspend inline fun <reified T : Any> Client.AwakeableHandle.resolveSuspend(
    payload: T,
    options: RequestOptions = RequestOptions.DEFAULT,
): Response<Void> {
  return this.resolveSuspend(typeTag<T>(), payload, options)
}

/**
 * Complete with failure the Awakeable.
 *
 * @param reason the rejection reason
 * @param options request options
 */
suspend fun Client.AwakeableHandle.rejectSuspend(
    reason: String,
    options: RequestOptions = RequestOptions.DEFAULT,
): Response<Void> {
  return this.rejectAsync(reason, options).await()
}

/**
 * Create a new [Client.InvocationHandle] for the provided invocation identifier.
 *
 * @param invocationId the invocation identifier
 * @return the invocation handle
 */
inline fun <reified Res> Client.invocationHandle(
    invocationId: String
): Client.InvocationHandle<Res> {
  return this.invocationHandle(invocationId, typeTag<Res>())
}

/**
 * Suspend version of [Client.InvocationHandle.attach].
 *
 * @param options request options
 * @return the response
 */
suspend fun <T> Client.InvocationHandle<T>.attachSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): Response<T> {
  return this.attachAsync(options).await()
}

/**
 * Suspend version of [Client.InvocationHandle.getOutput].
 *
 * @param options request options
 * @return the output response
 */
suspend fun <T : Any?> Client.InvocationHandle<T>.getOutputSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): Response<Output<T>> {
  return this.getOutputAsync(options).await()
}

/**
 * Create a new [Client.IdempotentInvocationHandle] for the provided target and idempotency key.
 *
 * @param target the target service/method
 * @param idempotencyKey the idempotency key
 * @return the idempotent invocation handle
 */
inline fun <reified Res> Client.idempotentInvocationHandle(
    target: Target,
    idempotencyKey: String,
): Client.IdempotentInvocationHandle<Res> {
  return this.idempotentInvocationHandle(target, idempotencyKey, typeTag<Res>())
}

/**
 * Suspend version of [Client.IdempotentInvocationHandle.attach].
 *
 * @param options request options
 * @return the response
 */
suspend fun <T> Client.IdempotentInvocationHandle<T>.attachSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): Response<T> {
  return this.attachAsync(options).await()
}

/**
 * Suspend version of [Client.IdempotentInvocationHandle.getOutput].
 *
 * @param options request options
 * @return the output response
 */
suspend fun <T> Client.IdempotentInvocationHandle<T>.getOutputSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): Response<Output<T>> {
  return this.getOutputAsync(options).await()
}

/**
 * Create a new [Client.WorkflowHandle] for the provided workflow name and identifier.
 *
 * @param workflowName the workflow name
 * @param workflowId the workflow identifier
 * @return the workflow handle
 */
inline fun <reified Res> Client.workflowHandle(
    workflowName: String,
    workflowId: String,
): Client.WorkflowHandle<Res> {
  return this.workflowHandle(workflowName, workflowId, typeTag<Res>())
}

/**
 * Suspend version of [Client.WorkflowHandle.attach].
 *
 * @param options request options
 * @return the response
 */
suspend fun <T> Client.WorkflowHandle<T>.attachSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): Response<T> {
  return this.attachAsync(options).await()
}

/**
 * Suspend version of [Client.WorkflowHandle.getOutput].
 *
 * @param options request options
 * @return the output response
 */
suspend fun <T> Client.WorkflowHandle<T>.getOutputSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): Response<Output<T>> {
  return this.getOutputAsync(options).await()
}

/** @see ResponseHead.statusCode */
val ResponseHead.status: Int
  get() = this.statusCode()

/** @see ResponseHead.headers */
val ResponseHead.headers: ResponseHead.Headers
  get() = this.headers()

/** @see Response.response */
val <Res> Response<Res>.response: Res
  get() = this.response()

/** @see SendResponse.sendStatus */
val <Res> SendResponse<Res>.sendStatus: SendResponse.SendStatus
  get() = this.sendStatus()

// =============================================================================
// Kotlin-specific Client Service Handle
// =============================================================================

/**
 * Kotlin-specific client service handle for invoking Restate services using method references.
 *
 * Example usage:
 * ```kotlin
 * val client = Client.connect("http://localhost:8080")
 *
 * // Call a service
 * val response = client.serviceHandle<Greeter>()
 *     .call(Greeter::greet, "Alice")
 *
 * // Send without waiting
 * val sendResponse = client.serviceHandle<Greeter>()
 *     .send(Greeter::greet, "Alice")
 * ```
 *
 * @param SVC the service interface type
 */
interface KotlinClientServiceHandle<SVC : Any> {
  /**
   * Call a service method with input and wait for the response.
   *
   * @param method method reference (e.g., `Greeter::greet`)
   * @param input the input parameter to pass to the method
   * @param options request options
   * @return the response
   */
  suspend fun <I, O> call(
      method: kotlin.reflect.KSuspendFunction2<SVC, I, O>,
      input: I,
      options: RequestOptions = RequestOptions.DEFAULT,
  ): Response<O>

  /**
   * Call a service method without input and wait for the response.
   *
   * @param method method reference (e.g., `Counter::get`)
   * @param options request options
   * @return the response
   */
  suspend fun <O> call(
      method: kotlin.reflect.KSuspendFunction1<SVC, O>,
      options: RequestOptions = RequestOptions.DEFAULT,
  ): Response<O>

  /**
   * Send a one-way invocation to a service method with input.
   *
   * @param method method reference
   * @param input the input parameter
   * @param delay optional execution delay
   * @param options request options
   * @return the send response
   */
  suspend fun <I, O> send(
      method: kotlin.reflect.KSuspendFunction2<SVC, I, O>,
      input: I,
      delay: Duration? = null,
      options: RequestOptions = RequestOptions.DEFAULT,
  ): SendResponse<O>

  /**
   * Send a one-way invocation to a service method without input.
   *
   * @param method method reference
   * @param delay optional execution delay
   * @param options request options
   * @return the send response
   */
  suspend fun <O> send(
      method: kotlin.reflect.KSuspendFunction1<SVC, O>,
      delay: Duration? = null,
      options: RequestOptions = RequestOptions.DEFAULT,
  ): SendResponse<O>
}

/**
 * Create a Kotlin-specific service handle for invoking a Restate service.
 *
 * @param SVC the service class annotated with @Service
 * @return a handle to invoke the service with method references
 */
inline fun <reified SVC : Any> Client.serviceHandle(): KotlinClientServiceHandle<SVC> {
  return KotlinClientServiceHandleImpl(this, SVC::class.java, null)
}

/**
 * Create a Kotlin-specific virtual object handle.
 *
 * @param SVC the virtual object class annotated with @VirtualObject
 * @param key the key identifying the specific virtual object instance
 * @return a handle to invoke the virtual object with method references
 */
inline fun <reified SVC : Any> Client.virtualObjectHandle(
    key: String
): KotlinClientServiceHandle<SVC> {
  return KotlinClientServiceHandleImpl(this, SVC::class.java, key)
}

/**
 * Create a Kotlin-specific workflow handle.
 *
 * @param SVC the workflow class annotated with @Workflow
 * @param key the key identifying the specific workflow instance
 * @return a handle to invoke the workflow with method references
 */
inline fun <reified SVC : Any> Client.workflowHandle(key: String): KotlinClientServiceHandle<SVC> {
  return KotlinClientServiceHandleImpl(this, SVC::class.java, key)
}

@PublishedApi
internal class KotlinClientServiceHandleImpl<SVC : Any>(
    private val client: Client,
    private val clazz: Class<SVC>,
    private val key: String?,
) : KotlinClientServiceHandle<SVC> {

  private val serviceName: String =
      dev.restate.common.reflections.ReflectionUtils.extractServiceName(clazz)

  override suspend fun <I, O> call(
      method: kotlin.reflect.KSuspendFunction2<SVC, I, O>,
      input: I,
      options: RequestOptions,
  ): Response<O> {
    val methodInfo = extractMethodInfo(method)
    val target =
        if (key != null) {
          Target.virtualObject(serviceName, key, methodInfo.handlerName)
        } else {
          Target.service(serviceName, methodInfo.handlerName)
        }

    @Suppress("UNCHECKED_CAST")
    val request =
        Request.of(
            target,
            methodInfo.inputType as TypeTag<I>,
            methodInfo.outputType as TypeTag<O>,
            input,
        )

    return client.callSuspend(request)
  }

  override suspend fun <O> call(
      method: kotlin.reflect.KSuspendFunction1<SVC, O>,
      options: RequestOptions,
  ): Response<O> {
    val methodInfo = extractMethodInfo(method)
    val target =
        if (key != null) {
          Target.virtualObject(serviceName, key, methodInfo.handlerName)
        } else {
          Target.service(serviceName, methodInfo.handlerName)
        }

    @Suppress("UNCHECKED_CAST")
    val request =
        Request.of(
            target,
            dev.restate.serde.Serde.VOID as TypeTag<Unit>,
            methodInfo.outputType as TypeTag<O>,
            Unit,
        )

    @Suppress("UNCHECKED_CAST")
    return client.callSuspend(request as Request<Unit, O>)
  }

  override suspend fun <I, O> send(
      method: kotlin.reflect.KSuspendFunction2<SVC, I, O>,
      input: I,
      delay: Duration?,
      options: RequestOptions,
  ): SendResponse<O> {
    val methodInfo = extractMethodInfo(method)
    val target =
        if (key != null) {
          Target.virtualObject(serviceName, key, methodInfo.handlerName)
        } else {
          Target.service(serviceName, methodInfo.handlerName)
        }

    @Suppress("UNCHECKED_CAST")
    val request =
        Request.of(
            target,
            methodInfo.inputType as TypeTag<I>,
            methodInfo.outputType as TypeTag<O>,
            input,
        )

    return client.sendSuspend(request, delay)
  }

  override suspend fun <O> send(
      method: kotlin.reflect.KSuspendFunction1<SVC, O>,
      delay: Duration?,
      options: RequestOptions,
  ): SendResponse<O> {
    val methodInfo = extractMethodInfo(method)
    val target =
        if (key != null) {
          Target.virtualObject(serviceName, key, methodInfo.handlerName)
        } else {
          Target.service(serviceName, methodInfo.handlerName)
        }

    @Suppress("UNCHECKED_CAST")
    val request =
        Request.of(
            target,
            dev.restate.serde.Serde.VOID as TypeTag<Unit>,
            methodInfo.outputType as TypeTag<O>,
            Unit,
        )

    @Suppress("UNCHECKED_CAST")
    return client.sendSuspend(request as Request<Unit, O>, delay)
  }

  private fun extractMethodInfo(method: kotlin.reflect.KFunction<*>): KotlinClientMethodInfo {
    val javaMethod = method.javaMethod ?: error("Cannot extract Java method from KFunction")
    return KotlinClientMethodInfo.fromMethod(javaMethod)
  }
}

/** Kotlin-aware method info extraction for client-side that handles suspend functions. */
internal class KotlinClientMethodInfo(
    val handlerName: String,
    val inputType: TypeTag<*>,
    val outputType: TypeTag<*>,
) {
  companion object {
    fun fromMethod(method: java.lang.reflect.Method): KotlinClientMethodInfo {
      val handlerInfo =
          dev.restate.common.reflections.ReflectionUtils.mustHaveHandlerAnnotation(method)
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
            effectiveParamCount == 0 -> dev.restate.serde.Serde.VOID
            else -> resolveTypeTag(genericParameters[0])
          }

      val outputTypeTag: TypeTag<*> =
          if (isSuspend) {
            // Extract return type from Continuation<T>
            val continuationType = genericParameters[paramCount - 1]
            val returnType = extractSuspendReturnType(continuationType)
            if (returnType == null || returnType.typeName == "kotlin.Unit") {
              dev.restate.serde.Serde.VOID
            } else {
              resolveTypeTag(returnType)
            }
          } else {
            resolveTypeTag(method.genericReturnType)
          }

      return KotlinClientMethodInfo(handlerName, inputTypeTag, outputTypeTag)
    }

    private fun extractSuspendReturnType(
        continuationType: java.lang.reflect.Type
    ): java.lang.reflect.Type? {
      if (continuationType is java.lang.reflect.ParameterizedType) {
        val rawType = continuationType.rawType
        if (rawType == kotlin.coroutines.Continuation::class.java) {
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

    private fun resolveTypeTag(type: java.lang.reflect.Type?): TypeTag<*> {
      if (type == null || type == Void.TYPE) {
        return dev.restate.serde.Serde.VOID
      }
      return dev.restate.common.reflections.RestateUtils.typeTag(type)
    }
  }
}

// Extension properties for InvocationOptions to access fields with Kotlin property syntax
private val dev.restate.common.InvocationOptions.idempotencyKey: String?
  get() = this.getIdempotencyKey()

private val dev.restate.common.InvocationOptions.headers: Map<String, String>?
  get() = this.getHeaders()
