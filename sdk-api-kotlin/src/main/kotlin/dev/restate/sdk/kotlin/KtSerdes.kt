// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import com.google.protobuf.ByteString
import dev.restate.sdk.common.Serde
import dev.restate.sdk.common.StateKey
import java.nio.charset.StandardCharsets
import kotlin.reflect.typeOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

object KtStateKey {

  /** Creates a json [StateKey]. */
  inline fun <reified T> json(name: String): StateKey<T> {
    return StateKey.of(name, KtSerdes.json())
  }
}

object KtSerdes {

  /** Creates a [Serde] implementation using the `kotlinx.serialization` json module. */
  inline fun <reified T> json(): Serde<T> {
    @Suppress("UNCHECKED_CAST")
    return when (typeOf<T>()) {
      typeOf<Unit>() -> UNIT as Serde<T>
      else -> json(serializer())
    }
  }

  val UNIT: Serde<Unit> =
      object : Serde<Unit> {
        override fun serialize(value: Unit?): ByteArray {
          return ByteArray(0)
        }

        override fun serializeToByteString(value: Unit?): ByteString {
          return ByteString.EMPTY
        }

        override fun deserialize(value: ByteArray) {
          return
        }

        override fun deserialize(byteString: ByteString) {
          return
        }

        override fun contentType(): String? {
          return null
        }
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

      override fun contentType(): String {
        return "application/json"
      }
    }
  }
}
