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
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.compileReferencing
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.generateJsonSchema
import io.github.smiley4.schemakenerator.jsonschema.TitleBuilder
import io.github.smiley4.schemakenerator.jsonschema.data.IntermediateJsonSchemaData
import io.github.smiley4.schemakenerator.jsonschema.data.RefType
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonArray
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNode
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonObject
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonTextValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.array
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.analyzeTypeUsingKotlinxSerialization
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.initial
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.renameMembers
import kotlin.collections.set
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

object DefaultJsonSchemaFactory : KotlinSerializationSerdeFactory.JsonSchemaFactory {
  @OptIn(ExperimentalSerializationApi::class)
  override fun generateSchema(json: Json, serializer: KSerializer<*>) =
      Serde.StringifiedJsonSchema(
          runCatching {
                var initialStep =
                    initial(serializer.descriptor).analyzeTypeUsingKotlinxSerialization {
                      serializersModule = json.serializersModule
                    }

                if (json.configuration.namingStrategy != null) {
                  initialStep = initialStep.renameMembers(json.configuration.namingStrategy!!)
                }

                val intermediateStep =
                    initialStep.generateJsonSchema {
                      optionalHandling = JsonSchemaSteps.OptionalHandling.NON_REQUIRED
                    }
                intermediateStep.writeTitles()
                val compiledSchema = intermediateStep.compileReferencing(RefType.SIMPLE)

                // In case of nested schemas, compileReferencing also contains self schema...
                val rootSchemaName =
                    TitleBuilder.BUILDER_SIMPLE(
                        compiledSchema.typeData, intermediateStep.typeDataById)

                // If schema is not json object, then it's boolean, so we're good no need for
                // additional manipulation
                if (compiledSchema.json !is JsonObject) {
                  return@runCatching compiledSchema.json
                }

                // Assemble the final schema now
                val rootNode = compiledSchema.json as JsonObject
                // Add $schema
                rootNode.properties.put(
                    "\$schema", JsonTextValue("https://json-schema.org/draft/2020-12/schema"))
                // Add $defs
                val definitions =
                    compiledSchema.definitions.filter { it.key != rootSchemaName }.toMutableMap()
                if (definitions.isNotEmpty()) {
                  rootNode.properties.put("\$defs", JsonObject(definitions))
                }
                // Replace all $refs
                rootNode.fixRefsPrefix("#/definitions/$rootSchemaName")
                // If the root type is nullable, it should be in the schema too
                if (serializer.descriptor.isNullable) {
                  val oldTypeProperty = rootNode.properties["type"]
                  if (oldTypeProperty is JsonTextValue) {
                    rootNode.properties["type"] = array {
                      item(oldTypeProperty.value)
                      item(JsonTextValue("null"))
                    }
                  } else if (oldTypeProperty is JsonArray) {
                    oldTypeProperty.items.add(JsonTextValue("null"))
                  }
                }

                return@runCatching rootNode
              }
              .getOrDefault(JsonObject(mutableMapOf()))
              .prettyPrint())

  private fun IntermediateJsonSchemaData.writeTitles() {
    this.entries.forEach { schema ->
      if (schema.json is JsonObject) {
        if ((schema.typeData.isMap ||
            schema.typeData.isCollection ||
            schema.typeData.isEnum ||
            schema.typeData.isInlineValue ||
            schema.typeData.typeParameters.isNotEmpty() ||
            schema.typeData.members.isNotEmpty()) &&
            (schema.json as JsonObject).properties["title"] == null) {
          (schema.json as JsonObject).properties["title"] =
              JsonTextValue(TitleBuilder.BUILDER_SIMPLE(schema.typeData, this.typeDataById))
        }
      }
    }
  }

  private fun JsonNode.fixRefsPrefix(rootDefinition: String) {
    when (this) {
      is JsonArray -> this.items.forEach { it.fixRefsPrefix(rootDefinition) }
      is JsonObject -> this.fixRefsPrefix(rootDefinition)
      else -> {}
    }
  }

  private fun JsonObject.fixRefsPrefix(rootDefinition: String) {
    this.properties.computeIfPresent("\$ref") { key, node ->
      if (node is JsonTextValue) {
        if (node.value.startsWith(rootDefinition)) {
          JsonTextValue("#/" + node.value.removePrefix(rootDefinition))
        } else {
          JsonTextValue("#/\$defs/" + node.value.removePrefix("#/definitions/"))
        }
      } else {
        node
      }
    }
    this.properties.values.forEach { it.fixRefsPrefix(rootDefinition) }
  }
}
