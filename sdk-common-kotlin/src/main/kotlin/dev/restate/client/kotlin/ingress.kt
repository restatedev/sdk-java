// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE

package dev.restate.client.kotlin

import dev.restate.client.CallRequestOptions
import dev.restate.client.Client
import dev.restate.client.RequestOptions
import dev.restate.client.SendResponse
import dev.restate.sdk.types.Output
import dev.restate.sdk.types.Target
import dev.restate.sdk.serde.Serde
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.future.await

// Extension methods for the Client

data class RequestOptionsBuilder(
  var additionalHeaders: MutableMap<String, String> = mutableMapOf<String, String>(),
) {
  fun build() = RequestOptions(additionalHeaders)
}

fun requestOptions(init: RequestOptionsBuilder.() -> Unit): RequestOptions {
  val builder = RequestOptionsBuilder()
  builder.init()
  return builder.build()
}

data class CallRequestOptionsBuilder(
  var additionalHeaders: MutableMap<String, String> = mutableMapOf<String, String>(),
  var idempotencyKey: String? = null
) {
  fun build() = CallRequestOptions(additionalHeaders, idempotencyKey)
}

fun callRequestOptions(init: CallRequestOptionsBuilder.() -> Unit): CallRequestOptions {
  val builder = CallRequestOptionsBuilder()
  builder.init()
  return builder.build()
}

suspend fun <Req, Res> Client.callSuspend(
  target: Target,
  reqSerde: Serde<Req>,
  resSerde: Serde<Res>,
  req: Req,
  options: RequestOptions = RequestOptions.DEFAULT
): Res {
  return this.callAsync(target, reqSerde, resSerde, req, options).await()
}

suspend fun <Req> Client.sendSuspend(
    target: Target,
    reqSerde: Serde<Req>,
    req: Req,
    delay: Duration = Duration.ZERO,
    options: RequestOptions = RequestOptions.DEFAULT
): SendResponse {
  return this.sendAsync(target, reqSerde, req, delay.toJavaDuration(), options).await()
}

suspend fun <T : Any> Client.AwakeableHandle.resolveSuspend(
    serde: Serde<T>,
    payload: T,
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.resolveAsync(serde, payload, options).await()
}

suspend fun Client.AwakeableHandle.rejectSuspend(
    reason: String,
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.rejectAsync(reason, options).await()
}

suspend fun <T> Client.InvocationHandle<T>.attachSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): T {
  return this.attachAsync(options).await()
}

suspend fun <T : Any?> Client.InvocationHandle<T>.getOutputSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): Output<T> {
  return this.getOutputAsync(options).await()
}

suspend fun <T> Client.IdempotentInvocationHandle<T>.attachSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): T {
  return this.attachAsync(options).await()
}

suspend fun <T> Client.IdempotentInvocationHandle<T>.getOutputSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): Output<T> {
  return this.getOutputAsync(options).await()
}

suspend fun <T> Client.WorkflowHandle<T>.attachSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): T {
  return this.attachAsync(options).await()
}

suspend fun <T> Client.WorkflowHandle<T>.getOutputSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
): Output<T> {
  return this.getOutputAsync(options).await()
}
