// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import kotlin.time.Duration

data class SendOptions(
    val idempotencyKey: String? = null,
    val headers: LinkedHashMap<String, String>? = null,
    val delay: Duration? = null
) {

  companion object {
    val DEFAULT: SendOptions = SendOptions()
  }

  data class Builder(
      var idempotencyKey: String? = null,
      var headers: LinkedHashMap<String, String>? = null,
      /** Time to wait before executing the call. */
      var delay: Duration? = null
  ) {
    fun build() = SendOptions(idempotencyKey, headers, delay)
  }
}

fun sendOptions(init: SendOptions.Builder.() -> Unit): SendOptions {
  val builder = SendOptions.Builder()
  builder.init()
  return builder.build()
}
