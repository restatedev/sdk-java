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
import dev.restate.common.InvocationOptions
import dev.restate.common.Output
import dev.restate.common.Request
import dev.restate.common.Target
import dev.restate.common.WorkflowRequest
import dev.restate.common.reflection.kotlin.RequestCaptureProxy
import dev.restate.common.reflection.kotlin.captureInvocation
import dev.restate.common.reflections.ProxySupport
import dev.restate.common.reflections.ReflectionUtils
import dev.restate.serde.TypeTag
import dev.restate.serde.kotlinx.typeTag
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine
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

/**
 * Create a proxy client for a Restate service.
 *
 * Example usage:
 * ```kotlin
 * val greeter = client.service<Greeter>()
 * val response = greeter.greet("Alice")
 * ```
 *
 * @param SVC the service class annotated with @Service
 * @return a proxy client to invoke the service
 */
@org.jetbrains.annotations.ApiStatus.Experimental
inline fun <reified SVC : Any> Client.service(): SVC {
  return service(this, SVC::class.java)
}

/**
 * Create a proxy client for a Restate virtual object.
 *
 * Example usage:
 * ```kotlin
 * val counter = client.virtualObject<Counter>("my-key")
 * val value = counter.increment()
 * ```
 *
 * @param SVC the virtual object class annotated with @VirtualObject
 * @param key the key identifying the specific virtual object instance
 * @return a proxy client to invoke the virtual object
 */
@org.jetbrains.annotations.ApiStatus.Experimental
inline fun <reified SVC : Any> Client.virtualObject(key: String): SVC {
  return virtualObject(this, SVC::class.java, key)
}

/**
 * Create a proxy client for a Restate workflow.
 *
 * Example usage:
 * ```kotlin
 * val wf = client.workflow<MyWorkflow>("wf-123")
 * val result = wf.run("input")
 * ```
 *
 * @param SVC the workflow class annotated with @Workflow
 * @param key the key identifying the specific workflow instance
 * @return a proxy client to invoke the workflow
 */
@org.jetbrains.annotations.ApiStatus.Experimental
inline fun <reified SVC : Any> Client.workflow(key: String): SVC {
  return workflow(this, SVC::class.java, key)
}

/**
 * Create a proxy for a service that uses the ingress client to make calls.
 *
 * @param client the ingress client to use for calls
 * @param clazz the service class
 * @return a proxy that intercepts method calls and executes them via the client
 */
@PublishedApi
internal fun <SVC : Any> service(client: Client, clazz: Class<SVC>): SVC {
  ReflectionUtils.mustHaveServiceAnnotation(clazz)
  require(ReflectionUtils.isKotlinClass(clazz)) {
    "Using Java classes with Kotlin's API is not supported"
  }

  val serviceName = ReflectionUtils.extractServiceName(clazz)
  return ProxySupport.createProxy(clazz) { invocation ->
    val request = invocation.captureInvocation(serviceName, null).toRequest()
    @Suppress("UNCHECKED_CAST") val continuation = invocation.arguments.last() as Continuation<Any?>

    // Start a coroutine that calls the client and resumes the continuation
    val suspendBlock: suspend () -> Any? = { client.callAsync(request).await().response() }
    suspendBlock.startCoroutine(continuation)
    COROUTINE_SUSPENDED
  }
}

/**
 * Create a proxy for a virtual object that uses the ingress client to make calls.
 *
 * @param client the ingress client to use for calls
 * @param clazz the virtual object class
 * @param key the virtual object key
 * @return a proxy that intercepts method calls and executes them via the client
 */
@PublishedApi
internal fun <SVC : Any> virtualObject(client: Client, clazz: Class<SVC>, key: String): SVC {
  ReflectionUtils.mustHaveVirtualObjectAnnotation(clazz)
  require(ReflectionUtils.isKotlinClass(clazz)) {
    "Using Java classes with Kotlin's API is not supported"
  }

  val serviceName = ReflectionUtils.extractServiceName(clazz)
  return ProxySupport.createProxy(clazz) { invocation ->
    val request = invocation.captureInvocation(serviceName, key).toRequest()
    @Suppress("UNCHECKED_CAST") val continuation = invocation.arguments.last() as Continuation<Any?>

    // Start a coroutine that calls the client and resumes the continuation
    val suspendBlock: suspend () -> Any? = { client.callAsync(request).await().response() }
    suspendBlock.startCoroutine(continuation)
    COROUTINE_SUSPENDED
  }
}

/**
 * Create a proxy for a workflow that uses the ingress client to make calls.
 *
 * @param client the ingress client to use for calls
 * @param clazz the workflow class
 * @param key the workflow key
 * @return a proxy that intercepts method calls and executes them via the client
 */
@PublishedApi
internal fun <SVC : Any> workflow(client: Client, clazz: Class<SVC>, key: String): SVC {
  ReflectionUtils.mustHaveWorkflowAnnotation(clazz)
  require(ReflectionUtils.isKotlinClass(clazz)) {
    "Using Java classes with Kotlin's API is not supported"
  }

  val serviceName = ReflectionUtils.extractServiceName(clazz)
  return ProxySupport.createProxy(clazz) { invocation ->
    val request = invocation.captureInvocation(serviceName, key).toRequest()
    @Suppress("UNCHECKED_CAST") val continuation = invocation.arguments.last() as Continuation<Any?>

    // Start a coroutine that calls the client and resumes the continuation
    val suspendBlock: suspend () -> Any? = { client.callAsync(request).await().response() }
    suspendBlock.startCoroutine(continuation)
    COROUTINE_SUSPENDED
  }
}

/**
 * Builder for creating type-safe requests.
 *
 * This builder allows the response type to be inferred from the lambda passed to [request].
 *
 * @param SVC the service/virtual object/workflow class
 */
@org.jetbrains.annotations.ApiStatus.Experimental
class KClientRequestBuilder<SVC : Any>
@PublishedApi
internal constructor(
    private val client: Client,
    private val clazz: Class<SVC>,
    private val key: String?,
) {
  /**
   * Create a request by invoking a method on the target.
   *
   * The response type is inferred from the return type of the invoked method.
   *
   * @param Res the response type (inferred from the lambda)
   * @param block a suspend lambda that invokes a method on the target
   * @return a [KClientRequest] with the correct response type
   */
  @Suppress("UNCHECKED_CAST")
  fun <Res> request(block: suspend SVC.() -> Res): KClientRequest<Any?, Res> {
    return KClientRequestImpl(
        client,
        RequestCaptureProxy(clazz, key).capture(block as suspend SVC.() -> Any?).toRequest(),
    )
        as KClientRequest<Any?, Res>
  }
}

/**
 * Kotlin-idiomatic request for invoking Restate services from an ingress client.
 *
 * Example usage:
 * ```kotlin
 * client.toService<CounterKt>()
 *     .request { add(1) }
 *     .options { idempotencyKey = "123" }
 *     .call()
 * ```
 *
 * @param Req the request type
 * @param Res the response type
 */
@org.jetbrains.annotations.ApiStatus.Experimental
interface KClientRequest<Req, Res> : Request<Req, Res> {

  /**
   * Configure invocation options using a DSL.
   *
   * @param block builder block for options
   * @return a new request with the configured options
   */
  fun options(block: InvocationOptions.Builder.() -> Unit): KClientRequest<Req, Res>

  /**
   * Call the target handler and wait for the response.
   *
   * @return the response
   */
  suspend fun call(): Response<Res>

  /**
   * Send the request without waiting for the response.
   *
   * @param delay optional delay before the invocation is executed
   * @return the send response with invocation handle
   */
  suspend fun send(delay: Duration? = null): SendResponse<Res>
}

/**
 * Create a builder for invoking a Restate service.
 *
 * Example usage:
 * ```kotlin
 * val response = client.toService<Greeter>()
 *     .request { greet("Alice") }
 *     .call()
 * ```
 *
 * @param SVC the service class annotated with @Service
 * @return a builder for creating typed requests
 */
@org.jetbrains.annotations.ApiStatus.Experimental
inline fun <reified SVC : Any> Client.toService(): KClientRequestBuilder<SVC> {
  ReflectionUtils.mustHaveServiceAnnotation(SVC::class.java)
  require(ReflectionUtils.isKotlinClass(SVC::class.java)) {
    "Using Java classes with Kotlin's API is not supported"
  }
  return KClientRequestBuilder(this, SVC::class.java, null)
}

/**
 * Create a builder for invoking a Restate virtual object.
 *
 * Example usage:
 * ```kotlin
 * val response = client.toVirtualObject<Counter>("my-counter")
 *     .request { add(1) }
 *     .call()
 * ```
 *
 * @param SVC the virtual object class annotated with @VirtualObject
 * @param key the key identifying the specific virtual object instance
 * @return a builder for creating typed requests
 */
@org.jetbrains.annotations.ApiStatus.Experimental
inline fun <reified SVC : Any> Client.toVirtualObject(key: String): KClientRequestBuilder<SVC> {
  ReflectionUtils.mustHaveVirtualObjectAnnotation(SVC::class.java)
  require(ReflectionUtils.isKotlinClass(SVC::class.java)) {
    "Using Java classes with Kotlin's API is not supported"
  }
  return KClientRequestBuilder(this, SVC::class.java, key)
}

/**
 * Create a builder for invoking a Restate workflow.
 *
 * Example usage:
 * ```kotlin
 * val response = client.toWorkflow<MyWorkflow>("workflow-123")
 *     .request { run("input") }
 *     .call()
 * ```
 *
 * @param SVC the workflow class annotated with @Workflow
 * @param key the key identifying the specific workflow instance
 * @return a builder for creating typed requests
 */
@org.jetbrains.annotations.ApiStatus.Experimental
inline fun <reified SVC : Any> Client.toWorkflow(key: String): KClientRequestBuilder<SVC> {
  ReflectionUtils.mustHaveWorkflowAnnotation(SVC::class.java)
  require(ReflectionUtils.isKotlinClass(SVC::class.java)) {
    "Using Java classes with Kotlin's API is not supported"
  }
  return KClientRequestBuilder(this, SVC::class.java, key)
}

/** Implementation of [KClientRequest] for ingress client. */
private class KClientRequestImpl<Req, Res>(
    private val client: Client,
    private val request: Request<Req, Res>,
) : KClientRequest<Req, Res>, Request<Req, Res> by request {

  override fun options(block: InvocationOptions.Builder.() -> Unit): KClientRequest<Req, Res> {
    val builder = InvocationOptions.builder()
    builder.block()
    return KClientRequestImpl(
        client,
        this.toBuilder().headers(builder.headers).idempotencyKey(builder.idempotencyKey).build(),
    )
  }

  override suspend fun call(): Response<Res> {
    return client.callSuspend(request)
  }

  override suspend fun send(delay: Duration?): SendResponse<Res> {
    return client.sendSuspend(request, delay)
  }
}
