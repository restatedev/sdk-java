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
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getKotlinClassByName
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Origin
import dev.restate.sdk.endpoint.definition.ServiceDefinitionFactory
import dev.restate.sdk.endpoint.definition.ServiceType
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
  private val handlersCodegen: HandlebarsTemplateEngine =
      HandlebarsTemplateEngine(
          "Handlers",
          ClassPathTemplateLoader(),
          mapOf(
              ServiceType.SERVICE to "templates/Handlers",
              ServiceType.WORKFLOW to "templates/Handlers",
              ServiceType.VIRTUAL_OBJECT to "templates/Handlers"),
          RESERVED_METHOD_NAMES)

  @OptIn(KspExperimental::class)
  override fun process(resolver: Resolver): List<KSAnnotated> {
    val converter =
        KElementConverter(
            logger,
            resolver.builtIns,
            resolver.getKotlinClassByName(ByteArray::class.qualifiedName!!)!!.asType(listOf()))

    val discovered = discoverRestateAnnotatedOrMetaAnnotatedServices(resolver)

    val services =
        discovered
            .map {
              val serviceBuilder = Service.builder()
              serviceBuilder.withServiceType(it.first.serviceType)

              converter.visitAnnotated(it.second, serviceBuilder)

              var serviceModel: Service? = null
              try {
                serviceModel = serviceBuilder.validateAndBuild()
              } catch (e: Exception) {
                logger.error("Unable to build service: $e", it.second)
              }
              (it.second to serviceModel!!)
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
        this.handlersCodegen.generate(fileCreator, service.second)
        this.clientCodegen.generate(fileCreator, service.second)
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

  private fun discoverRestateAnnotatedOrMetaAnnotatedServices(
      resolver: Resolver
  ): Set<Pair<MetaRestateAnnotation, KSAnnotated>> {
    val discoveredAnnotatedElements = mutableSetOf<Pair<MetaRestateAnnotation, KSAnnotated>>()

    val metaAnnotationsToProcess =
        mutableListOf(
            MetaRestateAnnotation(
                resolver
                    .getClassDeclarationByName<dev.restate.sdk.annotation.Service>()!!
                    .qualifiedName!!,
                ServiceType.SERVICE),
            MetaRestateAnnotation(
                resolver
                    .getClassDeclarationByName<dev.restate.sdk.annotation.VirtualObject>()!!
                    .qualifiedName!!,
                ServiceType.VIRTUAL_OBJECT),
            MetaRestateAnnotation(
                resolver
                    .getClassDeclarationByName<dev.restate.sdk.annotation.Workflow>()!!
                    .qualifiedName!!,
                ServiceType.WORKFLOW))

    // Add spring annotations, if available
    resolver.getClassDeclarationByName("dev.restate.sdk.springboot.RestateService")?.let {
      metaAnnotationsToProcess.add(MetaRestateAnnotation(it.qualifiedName!!, ServiceType.SERVICE))
    }
    resolver.getClassDeclarationByName("dev.restate.sdk.springboot.RestateVirtualObject")?.let {
      metaAnnotationsToProcess.add(
          MetaRestateAnnotation(it.qualifiedName!!, ServiceType.VIRTUAL_OBJECT))
    }
    resolver.getClassDeclarationByName("dev.restate.sdk.springboot.RestateWorkflow")?.let {
      metaAnnotationsToProcess.add(MetaRestateAnnotation(it.qualifiedName!!, ServiceType.WORKFLOW))
    }

    val discoveredAnnotations = mutableSetOf<String>()

    var metaAnnotation = metaAnnotationsToProcess.removeFirstOrNull()
    while (metaAnnotation != null) {
      if (!discoveredAnnotations.add(metaAnnotation.annotationName.asString())) {
        // We already discovered it, skip
        continue
      }
      for (annotatedElement in
          resolver.getSymbolsWithAnnotation(metaAnnotation.annotationName.asString())) {
        if (annotatedElement !is KSClassDeclaration) {
          continue
        }
        when (annotatedElement.classKind) {
          ClassKind.INTERFACE,
          ClassKind.CLASS -> {
            if (annotatedElement.containingFile!!.origin != Origin.KOTLIN) {
              // Skip if it's not kotlin
              continue
            }
            discoveredAnnotatedElements.add(metaAnnotation to annotatedElement)
          }
          ClassKind.ANNOTATION_CLASS -> {
            metaAnnotationsToProcess.add(
                MetaRestateAnnotation(annotatedElement.qualifiedName!!, metaAnnotation.serviceType))
          }
          else ->
              logger.error(
                  "The ServiceProcessor supports only interfaces or classes declarations",
                  annotatedElement)
        }
      }
      metaAnnotation = metaAnnotationsToProcess.removeFirstOrNull()
    }

    val knownAnnotations = discoveredAnnotations.toSet()

    // Check annotated elements are annotated with only one of the given annotations.
    discoveredAnnotatedElements.forEach { it ->
      val forbiddenAnnotations = knownAnnotations - setOf(it.first.annotationName.asString())
      val elementAnnotations =
          it.second.annotations
              .mapNotNull { it.annotationType.resolve().declaration.qualifiedName?.asString() }
              .toSet()
      if (forbiddenAnnotations.intersect(elementAnnotations).isNotEmpty()) {
        logger.error("The type is annotated with more than one Restate annotation", it.second)
      }
    }

    return discoveredAnnotatedElements.toSet()
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
          it.write("${service.second.fqcnGeneratedNamePrefix}ServiceDefinitionFactory")
          it.newLine()
        }
      }
    } catch (e: IOException) {
      logger.error("Unable to create $resourceFile: $e")
    }
  }
}
