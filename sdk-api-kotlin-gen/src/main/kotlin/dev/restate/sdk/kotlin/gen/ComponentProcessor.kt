// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.gen

import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.Origin
import dev.restate.sdk.common.BindableComponentFactory
import dev.restate.sdk.common.ComponentType
import dev.restate.sdk.gen.model.Component
import dev.restate.sdk.gen.template.HandlebarsTemplateEngine
import java.io.BufferedWriter
import java.io.IOException
import java.io.Writer
import java.nio.charset.Charset

class ComponentProcessor(private val logger: KSPLogger, private val codeGenerator: CodeGenerator) :
    SymbolProcessor {

  private val bindableComponentFactoryCodegen: HandlebarsTemplateEngine =
      HandlebarsTemplateEngine(
          "BindableComponentFactory",
          ClassPathTemplateLoader(),
          mapOf(
              ComponentType.SERVICE to "templates/BindableComponentFactory",
              ComponentType.VIRTUAL_OBJECT to "templates/BindableComponentFactory"))
  private val bindableComponentCodegen: HandlebarsTemplateEngine =
      HandlebarsTemplateEngine(
          "BindableComponent",
          ClassPathTemplateLoader(),
          mapOf(
              ComponentType.SERVICE to "templates/BindableComponent",
              ComponentType.VIRTUAL_OBJECT to "templates/BindableComponent"))
  private val clientCodegen: HandlebarsTemplateEngine =
      HandlebarsTemplateEngine(
          "Client",
          ClassPathTemplateLoader(),
          mapOf(
              ComponentType.SERVICE to "templates/Client",
              ComponentType.VIRTUAL_OBJECT to "templates/Client"))

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val converter = KElementConverter(logger, resolver.builtIns)

    val resolved =
        resolver
            .getSymbolsWithAnnotation(dev.restate.sdk.annotation.Service::class.qualifiedName!!)
            .toSet() +
            resolver
                .getSymbolsWithAnnotation(
                    dev.restate.sdk.annotation.VirtualObject::class.qualifiedName!!)
                .toSet() +
            resolver
                .getSymbolsWithAnnotation(
                    dev.restate.sdk.annotation.Workflow::class.qualifiedName!!)
                .toSet()

    val components =
        resolved
            .filter { it.containingFile!!.origin == Origin.KOTLIN }
            .map {
              val componentBuilder = Component.builder()
              converter.visitAnnotated(it, componentBuilder)

              var componentModel: Component? = null
              try {
                componentModel = componentBuilder.validateAndBuild()
              } catch (e: Exception) {
                logger.error("Unable to build component: $e", it)
              }
              (it to componentModel!!)
            }
            .toList()

    // Run code generation
    for (component in components) {
      try {
        val fileCreator: (String) -> Writer = { name: String ->
          codeGenerator
              .createNewFile(
                  Dependencies(false, component.first.containingFile!!),
                  component.second.targetPkg.toString(),
                  name)
              .writer(Charset.defaultCharset())
        }
        this.bindableComponentFactoryCodegen.generate(fileCreator, component.second)
        this.bindableComponentCodegen.generate(fileCreator, component.second)
        this.clientCodegen.generate(fileCreator, component.second)
      } catch (ex: Throwable) {
        throw RuntimeException(ex)
      }
    }

    // META-INF
    if (components.isNotEmpty()) {
      generateMetaINF(components)
    }

    return emptyList()
  }

  private fun generateMetaINF(components: List<Pair<KSAnnotated, Component>>) {
    val resourceFile = "META-INF/services/${BindableComponentFactory::class.java.canonicalName}"
    val dependencies =
        Dependencies(true, *(components.map { it.first.containingFile!! }.toTypedArray()))

    val writer: BufferedWriter =
        try {
          codeGenerator.createNewFileByPath(dependencies, resourceFile, "").bufferedWriter()
        } catch (e: FileSystemException) {
          val existingFile = e.file
          val currentValues = existingFile.readText()
          val newWriter = e.file.bufferedWriter()
          newWriter.write(currentValues)
          newWriter
        }

    try {
      writer.use {
        for (component in components) {
          it.write("${component.second.generatedClassFqcnPrefix}BindableComponentFactory")
          it.newLine()
        }
      }
    } catch (e: IOException) {
      logger.error("Unable to create $resourceFile: $e")
    }
  }
}
