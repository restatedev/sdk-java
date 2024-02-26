// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda.testservices

import dev.restate.sdk.common.Serde
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.RestateKtComponent
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers

class KotlinCounterService :
    KotlinCounterGrpcKt.KotlinCounterCoroutineImplBase(coroutineContext = Dispatchers.Unconfined),
    RestateKtComponent {

  companion object {
    private val COUNTER: StateKey<Long> =
        StateKey.of(
            "counter",
            Serde.using(
                { l: Long -> l.toString().toByteArray(StandardCharsets.UTF_8) },
                { v: ByteArray? -> String(v!!, StandardCharsets.UTF_8).toLong() }))
  }

  override suspend fun get(request: CounterRequest): GetResponse {
    (ObjectContext.current().get(COUNTER) ?: 0) + 1

    throw IllegalStateException("We shouldn't reach this point")
  }
}
