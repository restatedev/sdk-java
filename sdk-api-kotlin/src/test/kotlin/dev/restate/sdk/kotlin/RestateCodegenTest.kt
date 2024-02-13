// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.RestateCodegenTestSuite
import dev.restate.sdk.core.testservices.*
import dev.restate.sdk.core.testservices.CodegenRestateKt.CodegenRestateKtImplBase
import dev.restate.sdk.core.testservices.GreeterRestateKt.GreeterRestateKtImplBase
import io.grpc.BindableService
import kotlin.time.Duration.Companion.seconds

class RestateCodegenTest : RestateCodegenTestSuite() {
  private class GreeterWithRestateClientAndServerCodegen : GreeterRestateKtImplBase() {
    override suspend fun greet(context: KeyedContext, request: GreetingRequest): GreetingResponse {
      val client = GreeterRestateKt.newClient(context)
      client.delayed(1.seconds).greet(request)
      client.oneWay().greet(request)
      return client.greet(request).await()
    }
  }

  override fun greeterWithRestateClientAndServerCodegen(): BindableService {
    return GreeterWithRestateClientAndServerCodegen()
  }

  private class Codegen : CodegenRestateKtImplBase() {
    override suspend fun emptyInput(context: Context): MyMessage {
      val client = CodegenRestateKt.newClient(context)
      return client.emptyInput().await()
    }

    override suspend fun emptyOutput(context: Context, request: MyMessage) {
      val client = CodegenRestateKt.newClient(context)
      client.emptyOutput(request).await()
    }

    override suspend fun emptyInputOutput(context: Context) {
      val client = CodegenRestateKt.newClient(context)
      client.emptyInputOutput().await()
    }

    override suspend fun oneWay(context: Context, request: MyMessage): MyMessage {
      val client = CodegenRestateKt.newClient(context)
      return client._oneWay(request).await()
    }

    override suspend fun delayed(context: Context, request: MyMessage): MyMessage {
      val client = CodegenRestateKt.newClient(context)
      return client._delayed(request).await()
    }
  }

  override fun codegen(): BindableService {
    return Codegen()
  }
}
