// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.core.AwakeableIdTestSuite
import dev.restate.sdk.core.testservices.GreeterGrpcKt
import dev.restate.sdk.core.testservices.GreetingRequest
import dev.restate.sdk.core.testservices.GreetingResponse
import dev.restate.sdk.core.testservices.greetingResponse
import io.grpc.BindableService
import kotlinx.coroutines.Dispatchers

class AwakeableIdTest : AwakeableIdTestSuite() {
  private class ReturnAwakeableId :
      GreeterGrpcKt.GreeterCoroutineImplBase(Dispatchers.Unconfined), RestateCoroutineService {

    override suspend fun greet(request: GreetingRequest): GreetingResponse {
      val id: String = restateContext().awakeable(CoreSerdes.STRING_UTF8).id
      return greetingResponse { message = id }
    }
  }

  override fun returnAwakeableId(): BindableService {
    return ReturnAwakeableId()
  }
}
