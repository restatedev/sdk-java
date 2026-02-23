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
import dev.restate.sdk.common.DurablePromiseKey
import dev.restate.sdk.common.StateKey
import dev.restate.serde.Serde
import dev.restate.serde.Serde.Schema
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

@Deprecated("Use stateKey() instead")
object KtStateKey {

  /** Creates a json [StateKey]. */
  @Deprecated("Use stateKey() instead", replaceWith = ReplaceWith(expression = "stateKey()"))
  inline fun <reified T> json(name: String): StateKey<T> {
    return StateKey.of(name, KtSerdes.json())
  }
}

@Deprecated("Use durablePromiseKey() instead")
object KtDurablePromiseKey {

  /** Creates a json [StateKey]. */
  @Deprecated(
      "Use durablePromiseKey() instead",
      replaceWith = ReplaceWith(expression = "durablePromiseKey()"),
  )
  inline fun <reified T : Any?> json(name: String): DurablePromiseKey<T> {
    return DurablePromiseKey.of(name, KtSerdes.json())
  }
}

@Deprecated("Moved to dev.restate.serde.kotlinx")
object KtSerdes {

  @Deprecated("Moved to dev.restate.serde.kotlinx")
  inline fun <reified T : Any?> json(): Serde<T> {
    @Suppress("UNCHECKED_CAST")
    return when (typeOf<T>()) {
      typeOf<Unit>() -> UNIT as Serde<T>
      else -> json(serializer())
    }
  }

  @Deprecated("Moved to dev.restate.serde.kotlinx")
  val UNIT: Serde<Unit> =
      object : Serde<Unit> {
        override fun serialize(value: Unit?): Slice {
          return Slice.EMPTY
        }

        override fun deserialize(value: Slice) {
          return
        }

        override fun contentType(): String? {
          return null
        }
      }

  @Deprecated("Moved to dev.restate.serde.kotlinx")
  inline fun <reified T : Any?> json(serializer: KSerializer<T>): Serde<T> {
    return object : Serde<T> {
      override fun serialize(value: T?): Slice {
        if (value == null) {
          return Slice.wrap(
              Json.encodeToString(JsonNull.serializer(), JsonNull).encodeToByteArray()
          )
        }

        return Slice.wrap(Json.encodeToString(serializer, value).encodeToByteArray())
      }

      override fun deserialize(value: Slice): T {
        return Json.decodeFromString(
            serializer,
            String(value.toByteArray(), StandardCharsets.UTF_8),
        )
      }

      override fun contentType(): String {
        return "application/json"
      }

      override fun jsonSchema(): Schema {
        val schema: JsonSchema = serializer.descriptor.jsonSchema()
        return Serde.StringifiedJsonSchema(Json.encodeToString(schema))
      }
    }
  }

  @Deprecated("Moved to dev.restate.serde.kotlinx")
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

  @Deprecated("Moved to dev.restate.serde.kotlinx")
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
