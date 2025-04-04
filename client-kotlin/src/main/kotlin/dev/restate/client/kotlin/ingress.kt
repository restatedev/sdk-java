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
import dev.restate.client.ClientRequestOptions
import dev.restate.client.ClientResponse
import dev.restate.client.SendResponse
import dev.restate.common.Output
import dev.restate.common.Request
import dev.restate.common.WorkflowRequest
import dev.restate.serde.TypeTag
import dev.restate.serde.kotlinx.typeTag
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.future.await

// Extension methods for the Client

fun clientRequestOptions(init: ClientRequestOptions.Builder.() -> Unit): ClientRequestOptions {
  val builder = ClientRequestOptions.builder()
  builder.init()
  return builder.build()
}

/** Shorthand for [callSuspend] */
suspend fun <Req, Res> Request<Req, Res>.call(client: Client): ClientResponse<Res> {
  return client.callSuspend(this)
}

/** Suspend version of [Client.callAsync] */
suspend fun <Req, Res> Client.callSuspend(request: Request<Req, Res>): ClientResponse<Res> {
  return this.callAsync(request).await()
}

/** Shorthand for [sendSuspend] */
suspend fun <Req, Res> Request<Req, Res>.send(
    client: Client,
    delay: Duration? = null
): ClientResponse<SendResponse<Res>> {
  return client.sendSuspend(this, delay)
}

/** Suspend version of [Client.sendAsync] */
suspend fun <Req, Res> Client.sendSuspend(
    request: Request<Req, Res>,
    delay: Duration? = null
): ClientResponse<SendResponse<Res>> {
  return this.sendAsync(request, delay?.toJavaDuration()).await()
}

/** Shorthand for [submitSuspend] */
suspend fun <Req, Res> WorkflowRequest<Req, Res>.submit(
    client: Client,
    delay: Duration? = null
): ClientResponse<SendResponse<Res>> {
  return client.submitSuspend(this, delay)
}

/** Suspend version of [Client.submitAsync] */
suspend fun <Req, Res> Client.submitSuspend(
    request: WorkflowRequest<Req, Res>,
    delay: Duration? = null
): ClientResponse<SendResponse<Res>> {
  return this.submitAsync(request, delay?.toJavaDuration()).await()
}

suspend fun <T : Any> Client.AwakeableHandle.resolveSuspend(
    typeTag: TypeTag<T>,
    payload: T,
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<Void> {
  return this.resolveAsync(typeTag, payload, options).await()
}

suspend inline fun <reified T : Any> Client.AwakeableHandle.resolveSuspend(
    payload: T,
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<Void> {
  return this.resolveSuspend(typeTag<T>(), payload, options)
}

suspend fun Client.AwakeableHandle.rejectSuspend(
    reason: String,
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<Void> {
  return this.rejectAsync(reason, options).await()
}

suspend fun <T> Client.InvocationHandle<T>.attachSuspend(
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<T> {
  return this.attachAsync(options).await()
}

suspend fun <T : Any?> Client.InvocationHandle<T>.getOutputSuspend(
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<Output<T>> {
  return this.getOutputAsync(options).await()
}

suspend fun <T> Client.IdempotentInvocationHandle<T>.attachSuspend(
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<T> {
  return this.attachAsync(options).await()
}

suspend fun <T> Client.IdempotentInvocationHandle<T>.getOutputSuspend(
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<Output<T>> {
  return this.getOutputAsync(options).await()
}

suspend fun <T> Client.WorkflowHandle<T>.attachSuspend(
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<T> {
  return this.attachAsync(options).await()
}

suspend fun <T> Client.WorkflowHandle<T>.getOutputSuspend(
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<Output<T>> {
  return this.getOutputAsync(options).await()
}
