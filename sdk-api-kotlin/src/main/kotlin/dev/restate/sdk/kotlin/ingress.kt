// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.client.IngressClient
import dev.restate.sdk.client.RequestOptions
import dev.restate.sdk.common.Serde
import dev.restate.sdk.common.Target
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.future.await

// Extension methods for the IngressClient

suspend fun <Req, Res> IngressClient.callSuspend(
    target: Target,
    reqSerde: Serde<Req>,
    resSerde: Serde<Res>,
    req: Req,
    options: RequestOptions = RequestOptions.DEFAULT
): Res {
  return this.callAsync(target, reqSerde, resSerde, req, options).await()
}

suspend fun <Req> IngressClient.sendSuspend(
    target: Target,
    reqSerde: Serde<Req>,
    req: Req,
    delay: Duration = Duration.ZERO,
    options: RequestOptions = RequestOptions.DEFAULT
): String {
  return this.sendAsync(target, reqSerde, req, delay.toJavaDuration(), options).await()
}

suspend fun <T> IngressClient.AwakeableHandle.resolveSuspend(
    serde: Serde<T>,
    payload: T,
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.resolveAsync(serde, payload, options).await()
}

suspend fun IngressClient.AwakeableHandle.rejectSuspend(
    reason: String,
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.rejectAsync(reason, options).await()
}

suspend fun <T> IngressClient.InvocationHandle<T>.attachSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.attachAsync(options).await()
}

suspend fun <T> IngressClient.InvocationHandle<T>.getOutputSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.getOutputAsync(options).await()
}

suspend fun <T> IngressClient.WorkflowHandle<T>.attachSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.attachAsync(options).await()
}

suspend fun <T> IngressClient.WorkflowHandle<T>.getOutputSuspend(
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.getOutputAsync(options).await()
}
