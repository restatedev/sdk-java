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
import dev.restate.sdk.common.ServiceType
import dev.restate.sdk.gen.model.Handler
import dev.restate.sdk.gen.model.HandlerType
import dev.restate.sdk.gen.model.PayloadType
import dev.restate.sdk.gen.model.Service
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.ObjectContext
import java.util.regex.Pattern
import kotlin.reflect.KClass

class KElementConverter(private val logger: KSPLogger, private val builtIns: KSBuiltIns) :
    KSDefaultVisitor<Service.Builder, Unit>() {
  companion object {
    private val SUPPORTED_CLASS_KIND: Set<ClassKind> = setOf(ClassKind.CLASS, ClassKind.INTERFACE)
    private val EMPTY_PAYLOAD: PayloadType =
        PayloadType(true, "", "Unit", "dev.restate.sdk.kotlin.KtSerdes.UNIT")
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
    if (classDeclaration.isAnnotationPresent(dev.restate.sdk.annotation.Workflow::class)) {
      logger.error("sdk-api-kotlin doesn't support workflows yet", classDeclaration)
    }

    // Figure out service type annotations
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

    data.withServiceType(
        if (isAnnotatedWithService) ServiceType.SERVICE else ServiceType.VIRTUAL_OBJECT)

    // Infer names
    val targetPkg = classDeclaration.packageName.asString()
    val targetFqcn = classDeclaration.qualifiedName!!.asString()
    var serviceName =
        if (isAnnotatedWithService) serviceAnnotation!!.name else virtualObjectAnnotation!!.name
    if (serviceName.isEmpty()) {
      // Use Simple class name
      // With this logic we make sure we flatten subclasses names
      serviceName = targetFqcn.substring(targetPkg.length).replace(Pattern.quote(".").toRegex(), "")
    }
    data.withTargetPkg(targetPkg).withTargetFqcn(targetFqcn).withServiceName(serviceName)

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
        else defaultHandlerType(data.serviceType, function)
    handlerBuilder.withHandlerType(handlerType)

    validateMethodSignature(data.serviceType, handlerType, function)

    try {
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
              .validateAndBuild())
    } catch (e: Exception) {
      logger.error("Error when building handler: $e", function)
    }
  }

  private fun defaultHandlerType(serviceType: ServiceType, node: KSNode): HandlerType {
    when (serviceType) {
      ServiceType.SERVICE -> return HandlerType.STATELESS
      ServiceType.VIRTUAL_OBJECT -> return HandlerType.EXCLUSIVE
      ServiceType.WORKFLOW ->
          logger.error("Workflow handlers MUST be annotated with either @Shared or @Workflow", node)
    }
    throw IllegalStateException("Unexpected")
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
          logger.error(
              "The annotation @Shared is not supported by the service type $serviceType", function)
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
