// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import dev.restate.sdk.common.ComponentAdapter
import dev.restate.sdk.common.ComponentType
import dev.restate.sdk.gen.model.Component
import dev.restate.sdk.gen.model.Handler
import dev.restate.sdk.gen.model.HandlerType
import dev.restate.sdk.gen.model.PayloadType
import dev.restate.sdk.gen.template.HandlebarsTemplateEngine
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.ObjectContext
import java.io.BufferedWriter
import java.io.IOException
import java.io.Writer
import java.nio.charset.Charset
import java.util.regex.Pattern
import kotlin.reflect.KClass

class ComponentProcessorProvider : SymbolProcessorProvider {

  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return ComponentProcessor(
        logger = environment.logger, codeGenerator = environment.codeGenerator)
  }
}

class ComponentProcessor(private val logger: KSPLogger, private val codeGenerator: CodeGenerator) :
    SymbolProcessor {

  private val serviceAdapterCodegen: HandlebarsTemplateEngine =
      HandlebarsTemplateEngine(
          "ComponentAdapter",
          ClassPathTemplateLoader(),
          mapOf(
              ComponentType.SERVICE to "templates/ComponentAdapter",
              ComponentType.VIRTUAL_OBJECT to "templates/ComponentAdapter"))
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
              (it to componentBuilder.build()!!)
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
        this.serviceAdapterCodegen.generate(fileCreator, component.second)
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
    val resourceFile = "META-INF/services/${ComponentAdapter::class.java.canonicalName}"
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
          it.write("${component.second.generatedClassFqcnPrefix}ComponentAdapter")
          it.newLine()
        }
      }
    } catch (e: IOException) {
      logger.error("Unable to create $resourceFile: $e")
    }
  }
}

class KElementConverter(private val logger: KSPLogger, private val builtIns: KSBuiltIns) :
    KSDefaultVisitor<Component.Builder, Unit>() {
  companion object {
    private val SUPPORTED_CLASS_KIND: Set<ClassKind> = setOf(ClassKind.CLASS, ClassKind.INTERFACE)
    private val EMPTY_PAYLOAD: PayloadType =
        PayloadType(true, "", "Unit", "dev.restate.sdk.kotlin.KtSerdes.UNIT")
  }

  override fun defaultHandler(node: KSNode, data: Component.Builder) {}

  override fun visitAnnotated(annotated: KSAnnotated, data: Component.Builder) {
    if (annotated !is KSClassDeclaration) {
      logger.error(
          "Only classes or interfaces can be annotated with @Service or @VirtualObject or @Workflow")
    }
    visitClassDeclaration(annotated as KSClassDeclaration, data)
  }

  @OptIn(KspExperimental::class)
  override fun visitClassDeclaration(
      classDeclaration: KSClassDeclaration,
      data: Component.Builder
  ) {
    // Validate class declaration
    if (classDeclaration.typeParameters.isNotEmpty()) {
      logger.error(
          "The ComponentProcessor doesn't support components with generics", classDeclaration)
    }
    if (!SUPPORTED_CLASS_KIND.contains(classDeclaration.classKind)) {
      logger.error(
          "The ComponentProcessor supports only class declarations of kind $SUPPORTED_CLASS_KIND",
          classDeclaration)
    }
    if (classDeclaration.getVisibility() == Visibility.PRIVATE) {
      logger.error("The annotated class is private", classDeclaration)
    }
    if (classDeclaration.isAnnotationPresent(dev.restate.sdk.annotation.Workflow::class)) {
      logger.error("sdk-api-kotlin doesn't support workflows yet", classDeclaration)
    }

    // Figure out component type annotations
    val serviceAnnotation =
        classDeclaration
            .getAnnotationsByType(dev.restate.sdk.annotation.Service::class)
            .firstOrNull()
    val virtualObjectAnnotation =
        classDeclaration
            .getAnnotationsByType(dev.restate.sdk.annotation.VirtualObject::class)
            .firstOrNull()
    val isAnnotatedWithService = serviceAnnotation != null
    val isAnnotatedWithVirtualObject = virtualObjectAnnotation != null

    // Check there's exactly one annotation
    if (!(isAnnotatedWithService xor isAnnotatedWithVirtualObject)) {
      logger.error(
          "The type can be annotated only with one annotation between @VirtualObject and @Service",
          classDeclaration)
    }

    data.withComponentType(
        if (isAnnotatedWithService) ComponentType.SERVICE else ComponentType.VIRTUAL_OBJECT)

    // Infer names
    val targetPkg = classDeclaration.packageName.asString()
    val targetFqcn = classDeclaration.qualifiedName!!.asString()
    var componentName =
        if (isAnnotatedWithService) serviceAnnotation!!.name else virtualObjectAnnotation!!.name
    if (componentName.isEmpty()) {
      // Use Simple class name
      // With this logic we make sure we flatten subclasses names
      componentName =
          targetFqcn.substring(targetPkg.length).replace(Pattern.quote(".").toRegex(), "")
    }
    data.withTargetPkg(targetPkg).withTargetFqcn(targetFqcn).withComponentName(componentName)

    // Compute handlers
    classDeclaration
        .getAllFunctions()
        .filter {
          it.isAnnotationPresent(dev.restate.sdk.annotation.Handler::class) ||
              it.isAnnotationPresent(dev.restate.sdk.annotation.Workflow::class) ||
              it.isAnnotationPresent(dev.restate.sdk.annotation.Exclusive::class) ||
              it.isAnnotationPresent(dev.restate.sdk.annotation.Shared::class)
        }
        .forEach { visitFunctionDeclaration(it, data) }

    if (data.handlers.isEmpty()) {
      logger.warn(
          "The class declaration $targetFqcn has no methods annotated as handlers",
          classDeclaration)
    }
  }

  @OptIn(KspExperimental::class)
  override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Component.Builder) {
    // Validate function declaration
    if (function.typeParameters.isNotEmpty()) {
      logger.error("The ComponentProcessor doesn't support methods with generics", function)
    }
    if (function.functionKind != FunctionKind.MEMBER) {
      logger.error("Only member function declarations are supported as Restate handlers")
    }
    if (function.isAnnotationPresent(dev.restate.sdk.annotation.Workflow::class)) {
      logger.error("sdk-api-kotlin doesn't support workflows yet", function)
    }

    val isAnnotatedWithShared =
        function.isAnnotationPresent(dev.restate.sdk.annotation.Service::class)
    val isAnnotatedWithExclusive =
        function.isAnnotationPresent(dev.restate.sdk.annotation.Exclusive::class)

    // Check there's no more than one annotation
    val hasAnyAnnotation = isAnnotatedWithExclusive || isAnnotatedWithShared
    val hasExactlyOneAnnotation = isAnnotatedWithShared xor isAnnotatedWithExclusive
    if (!(!hasAnyAnnotation || hasExactlyOneAnnotation)) {
      logger.error(
          "You can have only one annotation between @Shared and @Exclusive to a method", function)
    }

    val handlerBuilder = Handler.builder()

    // Set handler type
    val handlerType =
        if (isAnnotatedWithShared) HandlerType.SHARED
        else if (isAnnotatedWithExclusive) HandlerType.EXCLUSIVE
        else defaultHandlerType(data.componentType, function)
    handlerBuilder.withHandlerType(handlerType)

    validateMethodSignature(data.componentType, handlerType, function)

    data.withHandler(
        handlerBuilder
            .withName(function.simpleName.asString())
            .withHandlerType(handlerType)
            .withInputType(
                if (function.parameters.size == 2) payloadFromType(function.parameters[1].type)
                else EMPTY_PAYLOAD)
            .withOutputType(
                if (function.returnType != null) payloadFromType(function.returnType!!)
                else EMPTY_PAYLOAD)
            .build())
  }

  private fun defaultHandlerType(componentType: ComponentType, node: KSNode): HandlerType {
    when (componentType) {
      ComponentType.SERVICE -> return HandlerType.STATELESS
      ComponentType.VIRTUAL_OBJECT -> return HandlerType.EXCLUSIVE
      ComponentType.WORKFLOW ->
          logger.error("Workflow handlers MUST be annotated with either @Shared or @Workflow", node)
    }
    throw IllegalStateException("Unexpected")
  }

  private fun validateMethodSignature(
      componentType: ComponentType,
      handlerType: HandlerType,
      function: KSFunctionDeclaration
  ) {
    if (function.parameters.isEmpty()) {
      logger.error(
          "The annotated method has no parameters. There must be at least the context parameter as first parameter",
          function)
    }
    when (handlerType) {
      HandlerType.SHARED ->
          logger.error(
              "The annotation @Shared is not supported by the component type $componentType",
              function)
      HandlerType.EXCLUSIVE ->
          if (componentType == ComponentType.VIRTUAL_OBJECT) {
            validateFirstParameterType(ObjectContext::class, function)
          } else {
            logger.error(
                "The annotation @Exclusive is not supported by the component type $componentType",
                function)
          }
      HandlerType.STATELESS -> validateFirstParameterType(Context::class, function)
      HandlerType.WORKFLOW ->
          logger.error(
              "The annotation @Workflow is currently not supported in sdk-api-kotlin", function)
    }
  }

  private fun validateFirstParameterType(clazz: KClass<*>, function: KSFunctionDeclaration) {
    if (function.parameters[0].type.resolve().declaration.qualifiedName!!.asString() !=
        clazz.qualifiedName) {
      logger.error(
          "The method signature must have ${clazz.qualifiedName} as first parameter, was ${function.parameters[0].type.resolve().declaration.qualifiedName!!.asString()}",
          function)
    }
  }

  private fun payloadFromType(typeRef: KSTypeReference): PayloadType {
    val ty = typeRef.resolve()
    return PayloadType(false, typeRef.toString(), boxedType(ty), serdeDecl(ty))
  }

  private fun serdeDecl(ty: KSType): String {
    return when (ty) {
      builtIns.unitType -> "dev.restate.sdk.kotlin.KtSerdes.UNIT"
      else -> "dev.restate.sdk.kotlin.KtSerdes.json<${boxedType(ty)}>()"
    }
  }

  private fun boxedType(ty: KSType): String {
    return when (ty) {
      builtIns.unitType -> "Unit"
      else -> ty.toString()
    }
  }
}
