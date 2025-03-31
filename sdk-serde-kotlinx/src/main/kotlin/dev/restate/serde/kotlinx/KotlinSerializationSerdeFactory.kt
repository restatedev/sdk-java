// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde.kotlinx

import dev.restate.common.Slice
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import dev.restate.serde.TypeRef
import dev.restate.serde.TypeTag
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.modules.SerializersModule

/**
 * This class implements [SerdeFactory] using Kotlinx serialization stack.
 *
 * If you want to customize the [Json] object used in your service, it is recommended to subclass
 * this class, and then register it using the [dev.restate.sdk.annotation.CustomSerdeFactory]
 * annotation.
 */
open class KotlinSerializationSerdeFactory
@JvmOverloads
constructor(private val json: Json = Json.Default) : SerdeFactory {

  @PublishedApi
  internal class KtTypeTag<T>(
      val type: KClass<*>,
      /** Reified type */
      val kotlinType: KType?
  ) : TypeTag<T>

  override fun <T : Any?> create(typeTag: TypeTag<T>): Serde<T> {
    if (typeTag is KtTypeTag) {
      return create(typeTag)
    }
    return super.create(typeTag)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any?> create(typeRef: TypeRef<T>): Serde<T> {
    if (typeRef.type == Unit::class.java) {
      return UNIT as Serde<T>
    }
    val serializer: KSerializer<T> =
        json.serializersModule.serializer(typeRef.type) as KSerializer<T>
    return jsonSerde(json, serializer)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any?> create(clazz: Class<T>): Serde<T> {
    if (clazz == Unit::class.java) {
      return UNIT as Serde<T>
    }
    val serializer: KSerializer<T> = json.serializersModule.serializer(clazz) as KSerializer<T>
    return jsonSerde(json, serializer)
  }

  @Suppress("UNCHECKED_CAST")
  @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
  private fun <T : Any?> create(ktSerdeInfo: KtTypeTag<T>): Serde<T> {
    if (ktSerdeInfo.type == Unit::class) {
      return UNIT as Serde<T>
    }
    val serializer: KSerializer<T> =
        json.serializersModule.serializerForKtTypeInfo(ktSerdeInfo) as KSerializer<T>
    return jsonSerde(json, serializer)
  }

  companion object {
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

    /** Creates a [Serde] implementation using the `kotlinx.serialization` json module. */
    fun <T : Any?> jsonSerde(json: Json = Json.Default, serializer: KSerializer<T>): Serde<T> {
      return object : Serde<T> {
        @Suppress("WRONG_NULLABILITY_FOR_JAVA_OVERRIDE")
        override fun serialize(value: T?): Slice {
          if (value == null) {
            return Slice.wrap(json.encodeToString(JsonNull.serializer(), JsonNull))
          }

          return Slice.wrap(json.encodeToString(serializer, value))
        }

        override fun deserialize(value: Slice): T {
          return json.decodeFromString(
              serializer, String(value.toByteArray(), StandardCharsets.UTF_8))
        }

        override fun contentType(): String {
          return "application/json"
        }

        override fun jsonSchema(): Serde.Schema {
          val schema: JsonSchema = serializer.descriptor.jsonSchema()
          return Serde.StringifiedJsonSchema(Json.encodeToString(schema))
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

  @InternalSerializationApi
  @ExperimentalSerializationApi
  /** Copy-pasted from ktor! */
  private fun SerializersModule.serializerForKtTypeInfo(
      ktSerdeInfoInfo: KtTypeTag<*>
  ): KSerializer<*> {
    val module = this
    return ktSerdeInfoInfo.kotlinType?.let { type ->
      if (type.arguments.isEmpty()) {
        null // fallback to a simple case because of
        // https://github.com/Kotlin/kotlinx.serialization/issues/1870
      } else {
        module.serializerOrNull(type)
      }
    }
        ?: module.getContextual(ktSerdeInfoInfo.type)?.maybeNullable(ktSerdeInfoInfo)
        ?: ktSerdeInfoInfo.type.serializer().maybeNullable(ktSerdeInfoInfo)
  }

  private fun <T : Any> KSerializer<T>.maybeNullable(typeInfo: KtTypeTag<*>): KSerializer<*> {
    return if (typeInfo.kotlinType?.isMarkedNullable == true) this.nullable else this
  }
}
