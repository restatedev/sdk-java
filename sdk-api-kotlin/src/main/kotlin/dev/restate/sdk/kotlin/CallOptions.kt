// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

data class CallOptions(
    val idempotencyKey: String? = null,
    val headers: LinkedHashMap<String, String>? = null
) {

  companion object {
    val DEFAULT: CallOptions = CallOptions()
  }

  data class Builder(
      var idempotencyKey: String? = null,
      var headers: LinkedHashMap<String, String>? = null,
  ) {
    fun build() =
        CallOptions(
            idempotencyKey,
            headers,
        )
  }
}

fun callOptions(init: CallOptions.Builder.() -> Unit): CallOptions {
  val builder = CallOptions.Builder()
  builder.init()
  return builder.build()
}
