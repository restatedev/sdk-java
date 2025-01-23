// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.gen

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import dev.restate.sdk.annotation.Accept
import dev.restate.sdk.annotation.Json
import dev.restate.sdk.annotation.Raw
import dev.restate.sdk.definition.ServiceType
import dev.restate.sdk.gen.model.Handler
import dev.restate.sdk.gen.model.HandlerType
import dev.restate.sdk.gen.model.PayloadType
import dev.restate.sdk.gen.model.Service
import dev.restate.sdk.gen.utils.AnnotationUtils.getAnnotationDefaultValue
import dev.restate.sdk.kotlin.*
import java.util.regex.Pattern
import kotlin.reflect.KClass

class KElementConverter(
    private val logger: KSPLogger,
    private val builtIns: KSBuiltIns,
    private val byteArrayType: KSType
) : KSDefaultVisitor<Service.Builder, Unit>() {
  companion object {
    private val SUPPORTED_CLASS_KIND: Set<ClassKind> = setOf(ClassKind.CLASS, ClassKind.INTERFACE)
    private val EMPTY_PAYLOAD: PayloadType =
        PayloadType(true, "", "Unit", "dev.restate.sdk.kotlin.KtSerdes.UNIT")
    private const val RAW_SERDE: String = "dev.restate.sdk.common.Serde.RAW"
  }

  override fun defaultHandler(node: KSNode, data: Service.Builder) {}

  override fun visitAnnotated(annotated: KSAnnotated, data: Service.Builder) {
    if (annotated !is KSClassDeclaration) {
      logger.error(
          "Only classes or interfaces can be annotated with @Service or @VirtualObject or @Workflow")
    }
    visitClassDeclaration(annotated as KSClassDeclaration, data)
  }

  @OptIn(KspExperimental::class)
  override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Service.Builder) {
    // Validate class declaration
    if (classDeclaration.typeParameters.isNotEmpty()) {
      logger.error("The ServiceProcessor doesn't support services with generics", classDeclaration)
    }
    if (!SUPPORTED_CLASS_KIND.contains(classDeclaration.classKind)) {
      logger.error(
          "The ServiceProcessor supports only class declarations of kind $SUPPORTED_CLASS_KIND",
          classDeclaration)
    }
    if (classDeclaration.getVisibility() == Visibility.PRIVATE) {
      logger.error("The annotated class is private", classDeclaration)
    }

    // Infer names
    val targetPkg = classDeclaration.packageName.asString()
    val targetFqcn = classDeclaration.qualifiedName!!.asString()
    data.withTargetPkg(targetPkg).withTargetFqcn(targetFqcn)

    if (data.serviceName.isNullOrEmpty()) {
      // Use Simple class name
      // With this logic we make sure we flatten subclasses names
      data.withServiceName(
          targetFqcn.substring(targetPkg.length).replace(Pattern.quote(".").toRegex(), ""))
    }

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
  override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Service.Builder) {
    // Validate function declaration
    if (function.typeParameters.isNotEmpty()) {
      logger.error("The ServiceProcessor doesn't support methods with generics", function)
    }
    if (function.functionKind != FunctionKind.MEMBER) {
      logger.error("Only member function declarations are supported as Restate handlers")
    }
    if (function.getVisibility() == Visibility.PRIVATE) {
      logger.error("The annotated function is private", function)
    }

    val isAnnotatedWithShared =
        function.isAnnotationPresent(dev.restate.sdk.annotation.Shared::class)
    val isAnnotatedWithExclusive =
        function.isAnnotationPresent(dev.restate.sdk.annotation.Exclusive::class)
    val isAnnotatedWithWorkflow =
        function.isAnnotationPresent(dev.restate.sdk.annotation.Workflow::class)

    // Check there's no more than one annotation
    val hasAnyAnnotation =
        isAnnotatedWithExclusive || isAnnotatedWithShared || isAnnotatedWithWorkflow
    val hasExactlyOneAnnotation =
        isAnnotatedWithShared xor isAnnotatedWithExclusive xor isAnnotatedWithWorkflow
    if (!(!hasAnyAnnotation || hasExactlyOneAnnotation)) {
      logger.error(
          "You can have only one annotation between @Shared and @Exclusive and @Workflow to a method",
          function)
    }

    val handlerBuilder = Handler.builder()

    // Set handler type
    val handlerType =
        if (isAnnotatedWithShared) HandlerType.SHARED
        else if (isAnnotatedWithExclusive) HandlerType.EXCLUSIVE
        else if (isAnnotatedWithWorkflow) HandlerType.WORKFLOW
        else defaultHandlerType(data.serviceType)
    handlerBuilder.withHandlerType(handlerType)

    validateMethodSignature(data.serviceType, handlerType, function)

    try {
      data.withHandler(
          handlerBuilder
              .withName(function.simpleName.asString())
              .withHandlerType(handlerType)
              .withInputAccept(inputAcceptFromParameterList(function.parameters))
              .withInputType(inputPayloadFromParameterList(function.parameters))
              .withOutputType(outputPayloadFromExecutableElement(function))
              .validateAndBuild())
    } catch (e: Exception) {
      logger.error("Error when building handler: $e", function)
    }
  }

  @OptIn(KspExperimental::class)
  private fun inputAcceptFromParameterList(paramList: List<KSValueParameter>): String? {
    if (paramList.size <= 1) {
      return null
    }

    return paramList[1].getAnnotationsByType(Accept::class).firstOrNull()?.value
  }

  @OptIn(KspExperimental::class)
  private fun inputPayloadFromParameterList(paramList: List<KSValueParameter>): PayloadType {
    if (paramList.size <= 1) {
      return EMPTY_PAYLOAD
    }

    val parameterElement: KSValueParameter = paramList[1]
    return payloadFromTypeMirrorAndAnnotations(
        parameterElement.type.resolve(),
        parameterElement.getAnnotationsByType(Json::class).firstOrNull(),
        parameterElement.getAnnotationsByType(Raw::class).firstOrNull(),
        parameterElement)
  }

  @OptIn(KspExperimental::class)
  private fun outputPayloadFromExecutableElement(fn: KSFunctionDeclaration): PayloadType {
    return payloadFromTypeMirrorAndAnnotations(
        fn.returnType?.resolve() ?: builtIns.unitType,
        fn.getAnnotationsByType(Json::class).firstOrNull(),
        fn.getAnnotationsByType(Raw::class).firstOrNull(),
        fn)
  }

  private fun payloadFromTypeMirrorAndAnnotations(
      ty: KSType,
      jsonAnnotation: Json?,
      rawAnnotation: Raw?,
      relatedNode: KSNode
  ): PayloadType {
    if (ty == builtIns.unitType) {
      if (rawAnnotation != null || jsonAnnotation != null) {
        logger.error("Unexpected annotation for void type.", relatedNode)
      }
      return EMPTY_PAYLOAD
    }
    // Some validation
    if (rawAnnotation != null && jsonAnnotation != null) {
      logger.error("A parameter cannot be annotated both with @Raw and @Json.", relatedNode)
    }
    if (rawAnnotation != null && ty != byteArrayType) {
      logger.error("A parameter annotated with @Raw MUST be of type byte[], was $ty", relatedNode)
    }
    if (ty.isFunctionType || ty.isSuspendFunctionType) {
      logger.error("Cannot use fun as parameter or return type", relatedNode)
    }

    val qualifiedTypeName = qualifiedTypeName(ty)
    var serdeDecl: String =
        if (rawAnnotation != null) RAW_SERDE else jsonSerdeDecl(ty, qualifiedTypeName)
    if (rawAnnotation != null &&
        rawAnnotation.contentType != getAnnotationDefaultValue(Raw::class.java, "contentType")) {
      serdeDecl = contentTypeDecoratedSerdeDecl(serdeDecl, rawAnnotation.contentType)
    }
    if (jsonAnnotation != null &&
        jsonAnnotation.contentType != getAnnotationDefaultValue(Json::class.java, "contentType")) {
      serdeDecl = contentTypeDecoratedSerdeDecl(serdeDecl, jsonAnnotation.contentType)
    }

    return PayloadType(false, qualifiedTypeName, boxedType(ty, qualifiedTypeName), serdeDecl)
  }

  private fun contentTypeDecoratedSerdeDecl(serdeDecl: String, contentType: String): String {
    return ("dev.restate.sdk.common.Serde.withContentType(\"" +
        contentType +
        "\", " +
        serdeDecl +
        ")")
  }

  private fun defaultHandlerType(serviceType: ServiceType): HandlerType {
    when (serviceType) {
      ServiceType.SERVICE -> return HandlerType.STATELESS
      ServiceType.VIRTUAL_OBJECT -> return HandlerType.EXCLUSIVE
      ServiceType.WORKFLOW -> return HandlerType.SHARED
    }
  }

  private fun validateMethodSignature(
    serviceType: ServiceType,
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
          if (serviceType == ServiceType.VIRTUAL_OBJECT) {
            validateFirstParameterType(SharedObjectContext::class, function)
          } else if (serviceType == ServiceType.WORKFLOW) {
            validateFirstParameterType(SharedWorkflowContext::class, function)
          } else {
            logger.error(
                "The annotation @Shared is not supported by the service type $serviceType",
                function)
          }
      HandlerType.EXCLUSIVE ->
          if (serviceType == ServiceType.VIRTUAL_OBJECT) {
            validateFirstParameterType(ObjectContext::class, function)
          } else {
            logger.error(
                "The annotation @Exclusive is not supported by the service type $serviceType",
                function)
          }
      HandlerType.STATELESS -> validateFirstParameterType(Context::class, function)
      HandlerType.WORKFLOW ->
          if (serviceType == ServiceType.WORKFLOW) {
            validateFirstParameterType(WorkflowContext::class, function)
          } else {
            logger.error(
                "The annotation @Workflow is not supported by the service type $serviceType",
                function)
          }
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

  private fun jsonSerdeDecl(ty: KSType, qualifiedTypeName: String): String {
    return when (ty) {
      builtIns.unitType -> "dev.restate.sdk.kotlin.KtSerdes.UNIT"
      else -> "dev.restate.sdk.kotlin.KtSerdes.json<${boxedType(ty, qualifiedTypeName)}>()"
    }
  }

  private fun boxedType(ty: KSType, qualifiedTypeName: String): String {
    return when (ty) {
      builtIns.unitType -> "Unit"
      else -> qualifiedTypeName
    }
  }

  private fun qualifiedTypeName(ksType: KSType): String {
    var typeName = ksType.declaration.qualifiedName?.asString() ?: ksType.toString()

    if (ksType.arguments.isNotEmpty()) {
      typeName =
          "$typeName<${
                ksType.arguments.joinToString(separator = ", ") {
                    if (it.variance == Variance.STAR) {
                        it.variance.label
                    } else {
                        "${it.variance.label} ${qualifiedTypeName(it.type!!.resolve())}"
                    }
                }
            }>"
    }

    if (ksType.isMarkedNullable) {
      typeName = "$typeName?"
    }

    return typeName
  }
}
