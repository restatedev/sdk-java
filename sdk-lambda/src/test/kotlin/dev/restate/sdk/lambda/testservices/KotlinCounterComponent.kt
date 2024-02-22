// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda.testservices

import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.RestateKtComponent
import kotlinx.coroutines.Dispatchers

class KotlinCounterComponent :
    KotlinCounterGrpcKt.KotlinCounterCoroutineImplBase(coroutineContext = Dispatchers.Unconfined),
    RestateKtComponent {

  override suspend fun get(request: CounterRequest): GetResponse {
    (ObjectContext.current().get(JavaCounterService.COUNTER) ?: 0) + 1

    throw IllegalStateException("We shouldn't reach this point")
  }
}
