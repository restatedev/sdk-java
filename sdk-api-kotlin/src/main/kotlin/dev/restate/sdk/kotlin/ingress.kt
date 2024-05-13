// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.client.CallRequestOptions
import dev.restate.sdk.client.IngressClient
import dev.restate.sdk.client.RequestOptions
import dev.restate.sdk.common.Serde
import dev.restate.sdk.common.Target
import kotlinx.coroutines.future.await
import kotlin.time.Duration
import kotlin.time.toJavaDuration

// Extension methods for the IngressClient

suspend fun <Req, Res> IngressClient.callSuspend(
    target: Target,
    reqSerde: Serde<Req>,
    resSerde: Serde<Res>,
    req: Req,
    options: CallRequestOptions = CallRequestOptions.DEFAULT
): Res {
  return this.callAsync(target, reqSerde, resSerde, req, options).await()
}

suspend fun <Req> IngressClient.sendSuspend(
    target: Target,
    reqSerde: Serde<Req>,
    req: Req,
    delay: Duration = Duration.ZERO,
    options: CallRequestOptions = CallRequestOptions.DEFAULT
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

suspend fun <T> IngressClient.InvocationHandle.attachSuspend(
    resSerde: Serde<T>,
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.attachAsync(resSerde, options).await()
}

suspend fun <T> IngressClient.InvocationHandle.getOutputSuspend(
    resSerde: Serde<T>,
    options: RequestOptions = RequestOptions.DEFAULT
) {
  this.getOutputAsync(resSerde, options).await()
}
