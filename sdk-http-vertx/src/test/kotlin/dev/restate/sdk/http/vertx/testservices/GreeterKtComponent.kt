// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx.testservices
//
// import dev.restate.sdk.core.testservices.GreeterGrpcKt
// import dev.restate.sdk.core.testservices.GreetingRequest
// import dev.restate.sdk.core.testservices.GreetingResponse
// import dev.restate.sdk.core.testservices.greetingResponse
// import dev.restate.sdk.kotlin.ObjectContext
// import dev.restate.sdk.kotlin.RestateKtComponent
// import kotlin.coroutines.CoroutineContext
// import kotlin.time.Duration.Companion.seconds
// import org.apache.logging.log4j.LogManager
//
// class GreeterKtComponent(coroutineContext: CoroutineContext) :
//    GreeterGrpcKt.GreeterCoroutineImplBase(coroutineContext), RestateKtComponent {
//
//  private val LOG = LogManager.getLogger(GreeterKtComponent::class.java)
//
//  override suspend fun greet(request: GreetingRequest): GreetingResponse {
//    LOG.info("Greet invoked!")
//
//    val count = (ObjectContext.current().get(BlockingGreeterService.COUNTER) ?: 0) + 1
//    ObjectContext.current().set(BlockingGreeterService.COUNTER, count)
//
//    ObjectContext.current().sleep(1.seconds)
//
//    return greetingResponse { message = "Hello ${request.name}. Count: $count" }
//  }
// }
