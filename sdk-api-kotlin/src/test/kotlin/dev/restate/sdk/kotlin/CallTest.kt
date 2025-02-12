// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.common.Slice
import dev.restate.common.Target
import dev.restate.sdk.core.CallTestSuite
import dev.restate.sdk.kotlin.KotlinCoroutinesTests.Companion.testDefinitionForService
import dev.restate.serde.Serde

class CallTest : CallTestSuite() {

  override fun oneWayCall(
      target: Target,
      idempotencyKey: String,
      headers: Map<String, String>,
      body: Slice
  ) =
      testDefinitionForService("OneWayCall") { ctx, _: Unit ->
        ctx.send(
            target,
            Serde.SLICE,
            body,
            sendOptions {
              this.idempotencyKey = idempotencyKey
              this.headers = LinkedHashMap(headers)
            })
      }

  override fun implicitCancellation(target: Target, body: Slice) =
      testDefinitionForService("ImplicitCancellation") { ctx, _: Unit ->
        ctx.call(target, Serde.SLICE, Serde.RAW, body)
      }
}
