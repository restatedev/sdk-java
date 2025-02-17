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
import dev.restate.common.CallRequest
import dev.restate.common.Output
import dev.restate.common.SendRequest
import dev.restate.serde.Serde
import kotlinx.coroutines.future.await

// Extension methods for the Client

fun clientRequestOptions(init: ClientRequestOptions.Builder.() -> Unit): ClientRequestOptions {
  val builder = ClientRequestOptions.builder()
  builder.init()
  return builder.build()
}

suspend fun <Req, Res> Client.callSuspend(callRequest: CallRequest<Req, Res>): ClientResponse<Res> {
  return this.callAsync(callRequest).await()
}

suspend fun <Req, Res> Client.callSuspend(
    callRequestBuilder: CallRequest.Builder<Req, Res>
): ClientResponse<Res> {
  return this.callAsync(callRequestBuilder).await()
}

suspend fun <Req> Client.sendSuspend(sendRequest: SendRequest<Req>): ClientResponse<SendResponse> {
  return this.sendAsync(sendRequest).await()
}

suspend fun <Req> Client.sendSuspend(
    sendRequestBuilder: SendRequest.Builder<Req>
): ClientResponse<SendResponse> {
  return this.sendAsync(sendRequestBuilder).await()
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
