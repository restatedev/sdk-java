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
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.getKotlinClassByName
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Origin
import dev.restate.sdk.common.ServiceType
import dev.restate.sdk.common.syscalls.ServiceDefinitionFactory
import dev.restate.sdk.gen.model.Service
import dev.restate.sdk.gen.template.HandlebarsTemplateEngine
import java.io.BufferedWriter
import java.io.IOException
import java.io.Writer
import java.nio.charset.Charset

class ServiceProcessor(private val logger: KSPLogger, private val codeGenerator: CodeGenerator) :
    SymbolProcessor {

  companion object {
    private val RESERVED_METHOD_NAMES: Set<String> = setOf("send", "submit", "workflowHandle")
  }

  private val bindableServiceFactoryCodegen: HandlebarsTemplateEngine =
      HandlebarsTemplateEngine(
          "ServiceDefinitionFactory",
          ClassPathTemplateLoader(),
          mapOf(
              ServiceType.SERVICE to "templates/ServiceDefinitionFactory",
              ServiceType.WORKFLOW to "templates/ServiceDefinitionFactory",
              ServiceType.VIRTUAL_OBJECT to "templates/ServiceDefinitionFactory"),
          RESERVED_METHOD_NAMES)
  private val clientCodegen: HandlebarsTemplateEngine =
      HandlebarsTemplateEngine(
          "Client",
          ClassPathTemplateLoader(),
          mapOf(
              ServiceType.SERVICE to "templates/Client",
              ServiceType.WORKFLOW to "templates/Client",
              ServiceType.VIRTUAL_OBJECT to "templates/Client"),
          RESERVED_METHOD_NAMES)
  private val definitionsCodegen: HandlebarsTemplateEngine =
      HandlebarsTemplateEngine(
          "Definitions",
          ClassPathTemplateLoader(),
          mapOf(
              ServiceType.SERVICE to "templates/Definitions",
              ServiceType.WORKFLOW to "templates/Definitions",
              ServiceType.VIRTUAL_OBJECT to "templates/Definitions"),
          RESERVED_METHOD_NAMES)

  @OptIn(KspExperimental::class)
  override fun process(resolver: Resolver): List<KSAnnotated> {
    val converter =
        KElementConverter(
            logger,
            resolver.builtIns,
            resolver.getKotlinClassByName(ByteArray::class.qualifiedName!!)!!.asType(listOf()))

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
                // Workflow annotation can be set on functions too
                .filter { ksAnnotated -> ksAnnotated is KSClassDeclaration }
                .toSet()

    val services =
        resolved
            .filter { it.containingFile!!.origin == Origin.KOTLIN }
            .map {
              val serviceBuilder = Service.builder()
              converter.visitAnnotated(it, serviceBuilder)

              var serviceModel: Service? = null
              try {
                serviceModel = serviceBuilder.validateAndBuild()
              } catch (e: Exception) {
                logger.error("Unable to build service: $e", it)
              }
              (it to serviceModel!!)
            }
            .toList()

    // Run code generation
    for (service in services) {
      try {
        val fileCreator: (String) -> Writer = { name: String ->
          codeGenerator
              .createNewFile(
                  Dependencies(false, service.first.containingFile!!),
                  service.second.targetPkg.toString(),
                  name)
              .writer(Charset.defaultCharset())
        }
        this.bindableServiceFactoryCodegen.generate(fileCreator, service.second)
        this.clientCodegen.generate(fileCreator, service.second)
        this.definitionsCodegen.generate(fileCreator, service.second)
      } catch (ex: Throwable) {
        throw RuntimeException(ex)
      }
    }

    // META-INF
    if (services.isNotEmpty()) {
      generateMetaINF(services)
    }

    return emptyList()
  }

  private fun generateMetaINF(services: List<Pair<KSAnnotated, Service>>) {
    val resourceFile = "META-INF/services/${ServiceDefinitionFactory::class.java.canonicalName}"
    val dependencies =
        Dependencies(true, *(services.map { it.first.containingFile!! }.toTypedArray()))

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
        for (service in services) {
          it.write("${service.second.generatedClassFqcnPrefix}ServiceDefinitionFactory")
          it.newLine()
        }
      }
    } catch (e: IOException) {
      logger.error("Unable to create $resourceFile: $e")
    }
  }
}
