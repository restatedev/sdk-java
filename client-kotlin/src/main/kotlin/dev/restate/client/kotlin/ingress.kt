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
import dev.restate.serde.Serde
import kotlinx.coroutines.future.await

// Extension methods for the Client

fun clientRequestOptions(init: ClientRequestOptions.Builder.() -> Unit): ClientRequestOptions {
  val builder = ClientRequestOptions.builder()
  builder.init()
  return builder.build()
}

suspend fun <Req, Res> Client.callSuspend(request: Request<Req, Res>): ClientResponse<Res> {
  return this.callAsync(request).await()
}

suspend fun <Req, Res> Client.callSuspend(
    requestBuilder: Request.Builder<Req, Res>
): ClientResponse<Res> {
  return this.callAsync(requestBuilder).await()
}

suspend fun <Req, Res> Client.sendSuspend(
    request: Request<Req, Res>
): ClientResponse<SendResponse<Res>> {
  return this.sendAsync(request).await()
}

suspend fun <Req, Res> Client.sendSuspend(
    request: Request.Builder<Req, Res>
): ClientResponse<SendResponse<Res>> {
  return this.sendSuspend(request.build())
}

suspend fun <T : Any> Client.AwakeableHandle.resolveSuspend(
    serde: Serde<T>,
    payload: T,
    options: ClientRequestOptions = ClientRequestOptions.DEFAULT
): ClientResponse<Void> {
  return this.resolveAsync(serde, payload, options).await()
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
