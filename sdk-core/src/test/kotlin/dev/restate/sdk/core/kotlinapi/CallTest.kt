// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.common.CallRequest
import dev.restate.common.SendRequest
import dev.restate.common.Slice
import dev.restate.common.Target
import dev.restate.sdk.core.CallTestSuite
import dev.restate.sdk.core.kotlinapi.KotlinAPITests.Companion.testDefinitionForService
import dev.restate.serde.Serde

class CallTest : CallTestSuite() {

  override fun oneWayCall(
      target: Target,
      idempotencyKey: String,
      headers: Map<String, String>,
      body: Slice
  ) =
      testDefinitionForService("OneWayCall") { ctx, _: Unit ->
        val ignored =
            ctx.send(
                SendRequest.of<Slice>(target, Serde.SLICE, body)
                    .idempotencyKey(idempotencyKey)
                    .headers(headers))
      }

  override fun implicitCancellation(target: Target, body: Slice) =
      testDefinitionForService("ImplicitCancellation") { ctx, _: Unit ->
        val ignored =
            ctx.call(CallRequest.of<Slice, ByteArray>(target, Serde.SLICE, Serde.RAW, body)).await()
      }
}
