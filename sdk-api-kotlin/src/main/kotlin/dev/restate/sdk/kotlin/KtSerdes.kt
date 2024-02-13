// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.Serde
import java.nio.charset.StandardCharsets
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

object KtSerdes {

  /** Creates a [Serde] implementation using the `kotlinx.serialization` json module. */
  inline fun <reified T> json(): Serde<T> {
    return json(serializer())
  }

  /** Creates a [Serde] implementation using the `kotlinx.serialization` json module. */
  fun <T> json(serializer: KSerializer<T>): Serde<T> {
    return object : Serde<T> {
      override fun serialize(value: T?): ByteArray {
        return Json.encodeToString(serializer, value!!).encodeToByteArray()
      }

      override fun deserialize(value: ByteArray?): T {
        return Json.decodeFromString(serializer, String(value!!, StandardCharsets.UTF_8))
      }
    }
  }
}
