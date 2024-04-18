// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen;

import dev.restate.sdk.Context;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.SharedObjectContext;
import dev.restate.sdk.annotation.Exclusive;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.Workflow;
import dev.restate.sdk.common.ServiceType;
import dev.restate.sdk.gen.model.*;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.WorkflowSharedContext;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

public class ElementConverter {

  private static final PayloadType EMPTY_PAYLOAD =
      new PayloadType(true, "", "Void", "dev.restate.sdk.common.CoreSerdes.VOID");

  private final Messager messager;
  private final Elements elements;
  private final Types types;

  public ElementConverter(Messager messager, Elements elements, Types types) {
    this.messager = messager;
    this.elements = elements;
    this.types = types;
  }

  public Service fromTypeElement(TypeElement element) {
    validateType(element);

    dev.restate.sdk.annotation.Service serviceAnnotation =
        element.getAnnotation(dev.restate.sdk.annotation.Service.class);
    dev.restate.sdk.annotation.VirtualObject virtualObjectAnnotation =
        element.getAnnotation(dev.restate.sdk.annotation.VirtualObject.class);
    dev.restate.sdk.annotation.Workflow workflowAnnotation =
        element.getAnnotation(dev.restate.sdk.annotation.Workflow.class);
    boolean isAnnotatedWithService = serviceAnnotation != null;
    boolean isAnnotatedWithVirtualObject = virtualObjectAnnotation != null;
    boolean isAnnotatedWithWorkflow = workflowAnnotation != null;

    // Should be guaranteed by the caller
    assert isAnnotatedWithWorkflow || isAnnotatedWithVirtualObject || isAnnotatedWithService;

    // Check there's no more than one annotation
    if (!Boolean.logicalXor(
        isAnnotatedWithService,
        Boolean.logicalXor(isAnnotatedWithWorkflow, isAnnotatedWithVirtualObject))) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The type can be annotated only with one annotation between @VirtualObject, @Workflow and @Service",
          element);
    }

    ServiceType type =
        isAnnotatedWithWorkflow
            ? ServiceType.WORKFLOW
            : isAnnotatedWithService ? ServiceType.SERVICE : ServiceType.VIRTUAL_OBJECT;

    // Infer names

    CharSequence targetPkg = elements.getPackageOf(element).getQualifiedName();
    CharSequence targetFqcn = element.getQualifiedName();

    String serviceName =
        isAnnotatedWithService
            ? serviceAnnotation.name()
            : isAnnotatedWithVirtualObject
                ? virtualObjectAnnotation.name()
                : workflowAnnotation.name();
    if (serviceName.isEmpty()) {
      // Use simple class name, flattening subclasses names
      serviceName =
          targetFqcn.toString().substring(targetPkg.length()).replaceAll(Pattern.quote("."), "");
    }

    // Compute handlers
    List<Handler> handlers =
        elements.getAllMembers(element).stream()
            .filter(e -> e instanceof ExecutableElement)
            .filter(
                e ->
                    e.getAnnotation(dev.restate.sdk.annotation.Handler.class) != null
                        || e.getAnnotation(Workflow.class) != null
                        || e.getAnnotation(Exclusive.class) != null
                        || e.getAnnotation(Shared.class) != null)
            .map(e -> fromExecutableElement(type, ((ExecutableElement) e)))
            .collect(Collectors.toList());

    if (handlers.isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.WARNING, "The service " + serviceName + " has no handlers", element);
    }

    try {
      return new Service.Builder()
          .withTargetPkg(targetPkg)
          .withTargetFqcn(targetFqcn)
          .withServiceName(serviceName)
          .withServiceType(type)
          .withHandlers(handlers)
          .validateAndBuild();
    } catch (Exception e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "Can't build the service " + serviceName + ": " + e.getMessage(),
          element);
      return null;
    }
  }

  private void validateType(TypeElement element) {
    if (!element.getTypeParameters().isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The ServiceProcessor doesn't support services with generics",
          element);
    }
    if (element.getKind().equals(ElementKind.ENUM)) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "The EntityProcessor doesn't support enums", element);
    }

    if (element.getModifiers().contains(Modifier.PRIVATE)) {
      messager.printMessage(Diagnostic.Kind.ERROR, "The annotated class is private", element);
    }
  }

  private Handler fromExecutableElement(ServiceType serviceType, ExecutableElement element) {
    if (!element.getTypeParameters().isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The EntityProcessor doesn't support methods with generics",
          element);
    }
    if (element.getKind().equals(ElementKind.CONSTRUCTOR)) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "You cannot annotate a constructor as Restate method");
    }
    if (element.getKind().equals(ElementKind.STATIC_INIT)) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "You cannot annotate a static init as Restate method");
    }

    boolean isAnnotatedWithShared = element.getAnnotation(Shared.class) != null;
    boolean isAnnotatedWithExclusive = element.getAnnotation(Exclusive.class) != null;
    boolean isAnnotatedWithWorkflow = element.getAnnotation(Workflow.class) != null;

    // Check there's no more than one annotation
    boolean hasAnyAnnotation =
        isAnnotatedWithExclusive || isAnnotatedWithShared || isAnnotatedWithWorkflow;
    boolean hasExactlyOneAnnotation =
        Boolean.logicalXor(
            isAnnotatedWithShared,
            Boolean.logicalXor(isAnnotatedWithWorkflow, isAnnotatedWithExclusive));
    if (!(!hasAnyAnnotation || hasExactlyOneAnnotation)) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "You can have only one annotation between @Shared, @Exclusive and @Workflow to a method",
          element);
    }

    HandlerType handlerType =
        isAnnotatedWithWorkflow
            ? HandlerType.WORKFLOW
            : isAnnotatedWithShared
                ? HandlerType.SHARED
                : isAnnotatedWithExclusive
                    ? HandlerType.EXCLUSIVE
                    : defaultHandlerType(serviceType, element);

    validateMethodSignature(serviceType, handlerType, element);

    try {
      return new Handler.Builder()
          .withName(element.getSimpleName())
          .withHandlerType(handlerType)
          .withInputType(
              element.getParameters().size() > 1
                  ? payloadFromType(element.getParameters().get(1).asType())
                  : EMPTY_PAYLOAD)
          .withOutputType(
              !element.getReturnType().getKind().equals(TypeKind.VOID)
                  ? payloadFromType(element.getReturnType())
                  : EMPTY_PAYLOAD)
          .validateAndBuild();
    } catch (Exception e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "Error when building handler: " + e.getMessage(), element);
      return null;
    }
  }

  private HandlerType defaultHandlerType(ServiceType serviceType, ExecutableElement element) {
    switch (serviceType) {
      case SERVICE:
        return HandlerType.STATELESS;
      case VIRTUAL_OBJECT:
        return HandlerType.EXCLUSIVE;
      case WORKFLOW:
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "Workflow methods MUST be annotated with either @Shared or @Workflow",
            element);
    }
    throw new IllegalStateException("Unexpected");
  }

  private void validateMethodSignature(
      ServiceType serviceType, HandlerType handlerType, ExecutableElement element) {
    switch (handlerType) {
      case SHARED:
        if (serviceType == ServiceType.WORKFLOW) {
          validateFirstParameterType(WorkflowSharedContext.class, element);
        } else if (serviceType == ServiceType.VIRTUAL_OBJECT) {
          validateFirstParameterType(SharedObjectContext.class, element);
        } else {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "The annotation @Shared is not supported by the service type " + serviceType,
              element);
        }
        break;
      case EXCLUSIVE:
        if (serviceType == ServiceType.VIRTUAL_OBJECT) {
          validateFirstParameterType(ObjectContext.class, element);
        } else {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "The annotation @Exclusive is not supported by the service type " + serviceType,
              element);
        }
        break;
      case STATELESS:
        validateFirstParameterType(Context.class, element);
        break;
      case WORKFLOW:
        if (serviceType == ServiceType.WORKFLOW) {
          validateFirstParameterType(WorkflowContext.class, element);
        } else {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "The annotation @Shared is not supported by the service type " + serviceType,
              element);
        }
        break;
    }
  }

  private void validateFirstParameterType(Class<?> clazz, ExecutableElement element) {
    if (!types.isSameType(
        element.getParameters().get(0).asType(),
        elements.getTypeElement(clazz.getCanonicalName()).asType())) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The method signature must have " + clazz.getCanonicalName() + " as first parameter",
          element);
    }
  }

  private PayloadType payloadFromType(TypeMirror typeMirror) {
    Objects.requireNonNull(typeMirror);
    return new PayloadType(
        false, typeMirror.toString(), boxedType(typeMirror), serdeDecl(typeMirror));
  }

  private static String serdeDecl(TypeMirror ty) {
    switch (ty.getKind()) {
      case BOOLEAN:
        return "dev.restate.sdk.common.CoreSerdes.JSON_BOOLEAN";
      case BYTE:
        return "dev.restate.sdk.common.CoreSerdes.JSON_BYTE";
      case SHORT:
        return "dev.restate.sdk.common.CoreSerdes.JSON_SHORT";
      case INT:
        return "dev.restate.sdk.common.CoreSerdes.JSON_INT";
      case LONG:
        return "dev.restate.sdk.common.CoreSerdes.JSON_LONG";
      case CHAR:
        return "dev.restate.sdk.common.CoreSerdes.JSON_CHAR";
      case FLOAT:
        return "dev.restate.sdk.common.CoreSerdes.JSON_FLOAT";
      case DOUBLE:
        return "dev.restate.sdk.common.CoreSerdes.JSON_DOUBLE";
      case VOID:
        return "dev.restate.sdk.common.CoreSerdes.VOID";
      default:
        // Default to Jackson type reference serde
        return "dev.restate.sdk.serde.jackson.JacksonSerdes.of(new com.fasterxml.jackson.core.type.TypeReference<"
            + ty
            + ">() {})";
    }
  }

  private static String boxedType(TypeMirror ty) {
    switch (ty.getKind()) {
      case BOOLEAN:
        return "Boolean";
      case BYTE:
        return "Byte";
      case SHORT:
        return "Short";
      case INT:
        return "Integer";
      case LONG:
        return "Long";
      case CHAR:
        return "Char";
      case FLOAT:
        return "Float";
      case DOUBLE:
        return "Double";
      case VOID:
        return "Void";
      default:
        return ty.toString();
    }
  }
}
