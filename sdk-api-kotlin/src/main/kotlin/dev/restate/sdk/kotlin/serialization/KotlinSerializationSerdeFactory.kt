package dev.restate.sdk.kotlin.serialization

import dev.restate.common.Slice
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import dev.restate.serde.SerdeInfo
import dev.restate.serde.TypeRef
import java.nio.charset.StandardCharsets
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
import kotlin.reflect.KClass
import kotlin.reflect.KType

class KotlinSerializationSerdeFactory(private val json: Json = Json.Default): SerdeFactory {

  @PublishedApi
  internal class KtSerdeInfo<T>(
    internal val type: KClass<*>,
    /**
     * Reified type
     */
    internal val kotlinType: KType?
  ): SerdeInfo<T>

  override fun <T : Any?> create(serdeInfo: SerdeInfo<T>): Serde<T> {
    if (serdeInfo is KtSerdeInfo) {
      return create(serdeInfo)
    }
    return super.create(serdeInfo)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any?> create(typeRef: TypeRef<T>): Serde<T> {
    if (typeRef.type == Unit::class.java) {
      return UNIT as Serde<T>
    }
    val serializer: KSerializer<T> = json.serializersModule.serializer(typeRef.type) as KSerializer<T>
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
  private fun <T : Any?> create(ktSerdeInfo: KtSerdeInfo<T>): Serde<T> {
    if (ktSerdeInfo.type == Unit::class) {
      return UNIT as Serde<T>
    }
    val serializer: KSerializer<T> = json.serializersModule.serializerForKtTypeInfo(ktSerdeInfo) as KSerializer<T>
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
  /**
   * Copy-pasted from ktor!
   */
  private fun SerializersModule.serializerForKtTypeInfo(ktSerdeInfoInfo: KtSerdeInfo<*>): KSerializer<*> {
    val module = this
    return ktSerdeInfoInfo.kotlinType
      ?.let { type ->
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

  private fun <T : Any> KSerializer<T>.maybeNullable(typeInfo: KtSerdeInfo<*>): KSerializer<*> {
    return if (typeInfo.kotlinType?.isMarkedNullable == true) this.nullable else this
  }
}