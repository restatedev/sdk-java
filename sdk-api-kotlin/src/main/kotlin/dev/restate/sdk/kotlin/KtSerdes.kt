// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.DurablePromiseKey
import dev.restate.sdk.common.RichSerde
import dev.restate.sdk.common.Serde
import dev.restate.sdk.common.StateKey
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.reflect.typeOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.serializer

object KtStateKey {

  /** Creates a json [StateKey]. */
  inline fun <reified T> json(name: String): StateKey<T> {
    return StateKey.of(name, KtSerdes.json())
  }
}

object KtDurablePromiseKey {

  /** Creates a json [StateKey]. */
  inline fun <reified T : Any?> json(name: String): DurablePromiseKey<T> {
    return DurablePromiseKey.of(name, KtSerdes.json())
  }
}

object KtSerdes {

  /** Creates a [Serde] implementation using the `kotlinx.serialization` json module. */
  inline fun <reified T : Any?> json(): Serde<T> {
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

        override fun serializeToByteBuffer(value: Unit?): ByteBuffer {
          return ByteBuffer.allocate(0)
        }

        override fun deserialize(value: ByteArray) {
          return
        }

        override fun deserialize(byteBuffer: ByteBuffer) {
          return
        }

        override fun contentType(): String? {
          return null
        }
      }

  /** Creates a [Serde] implementation using the `kotlinx.serialization` json module. */
  inline fun <reified T : Any?> json(serializer: KSerializer<T>): Serde<T> {
    return object : RichSerde<T> {
      override fun serialize(value: T?): ByteArray {
        if (value == null) {
          return Json.encodeToString(JsonNull.serializer(), JsonNull).encodeToByteArray()
        }

        return Json.encodeToString(serializer, value).encodeToByteArray()
      }

      override fun deserialize(value: ByteArray): T {
        return Json.decodeFromString(serializer, String(value, StandardCharsets.UTF_8))
      }

      override fun contentType(): String {
        return "application/json"
      }

      override fun jsonSchema(): String {
        val schema: JsonSchema = serializer.descriptor.jsonSchema()
        return Json.encodeToString(schema)
      }
    }
  }

  @Serializable
  @PublishedApi
  internal data class JsonSchema(
      @Serializable(with = StringListSerializer::class) val type: List<String>? = null,
      val format: String? = null,
  ) {
    companion object {
      val INT = JsonSchema(type = listOf("number"), format = "int32")

      val LONG = JsonSchema(type = listOf("number"), format = "int64")

      val DOUBLE = JsonSchema(type = listOf("number"), format = "double")

      val FLOAT = JsonSchema(type = listOf("number"), format = "float")

      val STRING = JsonSchema(type = listOf("string"))

      val BOOLEAN = JsonSchema(type = listOf("boolean"))

      val OBJECT = JsonSchema(type = listOf("object"))

      val LIST = JsonSchema(type = listOf("array"))

      val ANY = JsonSchema()
    }
  }

  object StringListSerializer :
      JsonTransformingSerializer<List<String>>(ListSerializer(String.Companion.serializer())) {
    override fun transformSerialize(element: JsonElement): JsonElement {
      require(element is JsonArray)
      return element.singleOrNull() ?: element
    }
  }

  /**
   * Super simplistic json schema generation. We should replace this with an appropriate library.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @PublishedApi
  internal fun SerialDescriptor.jsonSchema(): JsonSchema {
    var schema =
        when (this.kind) {
          PrimitiveKind.BOOLEAN -> JsonSchema.BOOLEAN
          PrimitiveKind.BYTE -> JsonSchema.INT
          PrimitiveKind.CHAR -> JsonSchema.STRING
          PrimitiveKind.DOUBLE -> JsonSchema.DOUBLE
          PrimitiveKind.FLOAT -> JsonSchema.FLOAT
          PrimitiveKind.INT -> JsonSchema.INT
          PrimitiveKind.LONG -> JsonSchema.LONG
          PrimitiveKind.SHORT -> JsonSchema.INT
          PrimitiveKind.STRING -> JsonSchema.STRING
          StructureKind.LIST -> JsonSchema.LIST
          StructureKind.MAP -> JsonSchema.OBJECT
          else -> JsonSchema.ANY
        }

    // Add nullability constraint
    if (this.isNullable && schema.type != null) {
      schema = schema.copy(type = schema.type.plus("null"))
    }

    return schema
  }
}
