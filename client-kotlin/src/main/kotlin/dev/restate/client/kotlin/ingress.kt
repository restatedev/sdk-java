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
