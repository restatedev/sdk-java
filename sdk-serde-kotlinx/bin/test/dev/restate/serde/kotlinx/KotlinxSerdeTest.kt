// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde.kotlinx

import dev.restate.serde.Serde
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinxSerdeTest {

  @Serializable data class Recursive(val rec: Recursive? = null, val value: String)

  @Serializable
  data class RecursiveCircular(val rec: RecursiveOtherCircular? = null, val value: String)

  @Serializable
  data class RecursiveOtherCircular(val rec: RecursiveCircular? = null, val value: String)

  @Serializable
  data class RecursiveTemplateCircular<V>(
      val rec: RecursiveTemplateOtherCircular<V>? = null,
      val value: V,
  )

  @Serializable
  data class RecursiveTemplateOtherCircular<V>(
      val rec: RecursiveTemplateCircular<V>? = null,
      val value: V,
  )

  @Test
  fun schemaGenWithPrimitive() {
    testSchemaGen<String>(
        """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "string"
        }
        """
            .trimIndent()
    )
  }

  @Test
  fun schemaGenWithNullablePrimitive() {
    testSchemaGen<String?>(
        """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": ["string", "null"]
        }
        """
            .trimIndent()
    )
  }

  @Test
  fun schemaGenWithRecursive() {
    testSchemaGen<Recursive>(
        """
        {
           "type": "object",
           "required": [
              "value"
           ],
           "properties": {
              "rec": {
                 "${'$'}ref": "#/"
              },
              "value": {
                 "type": "string"
              }
           },
           "title": "Recursive",
           "${'$'}schema": "https://json-schema.org/draft/2020-12/schema"
        }
        """
            .trimIndent()
    )
  }

  @Test
  fun schemaGenWithRecursiveCircular() {
    testSchemaGen<RecursiveCircular>(
        """
        {
           "type": "object",
           "required": [
              "value"
           ],
           "properties": {
              "rec": {
                 "${'$'}ref": "#/${'$'}defs/RecursiveOtherCircular"
              },
              "value": {
                 "type": "string"
              }
           },
           "title": "RecursiveCircular",
           "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
           "${'$'}defs": {
              "RecursiveOtherCircular": {
                 "type": "object",
                 "required": [
                    "value"
                 ],
                 "properties": {
                    "rec": {
                       "${'$'}ref": "#/"
                    },
                    "value": {
                       "type": "string"
                    }
                 },
                 "title": "RecursiveOtherCircular"
              }
           }
        }
        """
            .trimIndent()
    )
  }

  @Test
  fun schemaGenWorksWithNestedRecursionTemplated() {
    testSchemaGen<RecursiveTemplateCircular<Integer>>(
        """
        {
           "type": "object",
           "required": [
              "value"
           ],
           "properties": {
              "rec": {
                 "${'$'}ref": "#/${'$'}defs/RecursiveTemplateOtherCircular"
              },
              "value": {
                 "type": "integer",
                 "minimum": -2147483648,
                 "maximum": 2147483647
              }
           },
           "title": "RecursiveTemplateCircular",
           "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
           "${'$'}defs": {
              "RecursiveTemplateOtherCircular": {
                 "type": "object",
                 "required": [
                    "value"
                 ],
                 "properties": {
                    "rec": {
                       "${'$'}ref": "#/"
                    },
                    "value": {
                       "type": "integer",
                       "minimum": -2147483648,
                       "maximum": 2147483647
                    }
                 },
                 "title": "RecursiveTemplateOtherCircular"
              }
           }
        }
        """
            .trimIndent()
    )
  }

  inline fun <reified T : Any?> testSchemaGen(expectedSchema: String) {
    val expectedJsonElement = Json.decodeFromString<JsonElement>(expectedSchema)
    val actualSchema =
        (jsonSerde<T>(jsonSchemaFactory = DefaultJsonSchemaFactory).jsonSchema()
                as Serde.StringifiedJsonSchema)
            .schema
    val actualJsonElement = Json.decodeFromString<JsonElement>(actualSchema)

    assertThat(actualJsonElement).isEqualTo(expectedJsonElement)
  }
}
