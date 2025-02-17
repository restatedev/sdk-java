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
import dev.restate.sdk.SharedWorkflowContext;
import dev.restate.sdk.WorkflowContext;
import dev.restate.sdk.annotation.*;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.sdk.gen.model.*;
import dev.restate.sdk.gen.model.Handler;
import dev.restate.sdk.gen.model.Service;
import dev.restate.sdk.gen.utils.AnnotationUtils;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.jspecify.annotations.Nullable;

class ElementConverter {

  private static final PayloadType EMPTY_PAYLOAD =
      new PayloadType(true, "", "Void", "dev.restate.serde.Serde.VOID");
  private static final String RAW_SERDE = "dev.restate.serde.Serde.RAW";

  private final Messager messager;
  private final Elements elements;
  private final Types types;

  public ElementConverter(Messager messager, Elements elements, Types types) {
    this.messager = messager;
    this.elements = elements;
    this.types = types;
  }

  Service fromTypeElement(MetaRestateAnnotation metaAnnotation, TypeElement element) {
    validateType(element);

    // Find annotation mirror
    AnnotationMirror metaAnnotationMirror =
        element.getAnnotationMirrors().stream()
            .filter(
                a ->
                    a.getAnnotationType()
                        .asElement()
                        .equals(metaAnnotation.getAnnotationTypeElement()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot find the annotation mirror for meta annotation "
                            + metaAnnotation.getAnnotationTypeElement().getQualifiedName()));

    // Infer names
    CharSequence targetPkg = elements.getPackageOf(element).getQualifiedName();
    CharSequence targetFqcn = element.getQualifiedName();
    String serviceName = metaAnnotation.resolveName(metaAnnotationMirror);
    if (serviceName == null || serviceName.isEmpty()) {
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
            .map(
                e ->
                    fromExecutableElement(metaAnnotation.getServiceType(), ((ExecutableElement) e)))
            .collect(Collectors.toList());

    if (handlers.isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.WARNING, "The service " + serviceName + " has no handlers", element);
    }

    String serdeFactoryDecl = "new dev.restate.sdk.serde.jackson.JacksonSerdeFactory()";
    CustomSerdeFactory customSerdeFactory = element.getAnnotation(CustomSerdeFactory.class);
    if (customSerdeFactory != null) {
      serdeFactoryDecl = "new " + getCustomSerdeClassCanonicalName(customSerdeFactory) + "()";
    }

    try {
      return new Service.Builder()
          .withTargetPkg(targetPkg)
          .withTargetFqcn(targetFqcn)
          .withServiceName(serviceName)
          .withDocumentation(sanitizeJavadoc(elements.getDocComment(element)))
          .withServiceType(metaAnnotation.getServiceType())
          .withHandlers(handlers)
          .withSerdeFactoryDecl(serdeFactoryDecl)
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
    if (element.getModifiers().contains(Modifier.PRIVATE)) {
      messager.printMessage(Diagnostic.Kind.ERROR, "The annotated method is private");
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
                    : defaultHandlerType(serviceType);

    validateMethodSignature(serviceType, handlerType, element);

    try {
      return new Handler.Builder()
          .withName(element.getSimpleName())
          .withHandlerType(handlerType)
          .withDocumentation(sanitizeJavadoc(elements.getDocComment(element)))
          .withInputAccept(inputAcceptFromParameterList(element.getParameters()))
          .withInputType(inputPayloadFromParameterList(element.getParameters()))
          .withOutputType(outputPayloadFromExecutableElement(element))
          .validateAndBuild();
    } catch (Exception e) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Error when building handler: " + e, element);
      return null;
    }
  }

  private HandlerType defaultHandlerType(ServiceType serviceType) {
    return switch (serviceType) {
      case SERVICE -> HandlerType.STATELESS;
      case VIRTUAL_OBJECT -> HandlerType.EXCLUSIVE;
      case WORKFLOW -> HandlerType.SHARED;
    };
  }

  private void validateMethodSignature(
      ServiceType serviceType, HandlerType handlerType, ExecutableElement element) {
    switch (handlerType) {
      case SHARED:
        if (serviceType == ServiceType.WORKFLOW) {
          validateFirstParameterType(SharedWorkflowContext.class, element);
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
    if (element.getParameters().isEmpty()
        || !types.isSameType(
            element.getParameters().get(0).asType(),
            elements.getTypeElement(clazz.getCanonicalName()).asType())) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The method signature must have " + clazz.getCanonicalName() + " as first parameter",
          element);
    }
  }

  private String inputAcceptFromParameterList(List<? extends VariableElement> element) {
    if (element.size() <= 1) {
      return null;
    }

    Accept accept = element.get(1).getAnnotation(Accept.class);
    if (accept == null) {
      return null;
    }
    return accept.value();
  }

  private PayloadType inputPayloadFromParameterList(List<? extends VariableElement> element) {
    if (element.size() <= 1) {
      return EMPTY_PAYLOAD;
    }

    Element parameterElement = element.get(1);
    return payloadFromTypeMirrorAndAnnotations(
        parameterElement.asType(),
        parameterElement.getAnnotation(Json.class),
        parameterElement.getAnnotation(Raw.class),
        parameterElement);
  }

  private PayloadType outputPayloadFromExecutableElement(ExecutableElement element) {
    return payloadFromTypeMirrorAndAnnotations(
        element.getReturnType(),
        element.getAnnotation(Json.class),
        element.getAnnotation(Raw.class),
        element);
  }

  private PayloadType payloadFromTypeMirrorAndAnnotations(
      TypeMirror ty, @Nullable Json jsonAnnotation, @Nullable Raw rawAnnotation, Element element) {
    if (ty.getKind().equals(TypeKind.VOID)) {
      if (rawAnnotation != null || jsonAnnotation != null) {
        messager.printMessage(
            Diagnostic.Kind.ERROR, "Unexpected annotation for void type.", element);
      }
      return EMPTY_PAYLOAD;
    }
    // Some validation
    if (rawAnnotation != null && jsonAnnotation != null) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "A parameter cannot be annotated both with @Raw and @Json.",
          element);
    }
    if (rawAnnotation != null
        && !types.isSameType(ty, types.getArrayType(types.getPrimitiveType(TypeKind.BYTE)))) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "A parameter annotated with @Raw MUST be of type byte[], was " + ty,
          element);
    }

    String serdeDecl = rawAnnotation != null ? RAW_SERDE : serdeDecl(ty);
    if (rawAnnotation != null
        && !rawAnnotation
            .contentType()
            .equals(AnnotationUtils.getAnnotationDefaultValue(Raw.class, "contentType"))) {
      serdeDecl = contentTypeDecoratedSerdeDecl(serdeDecl, rawAnnotation.contentType());
    }
    if (jsonAnnotation != null
        && !jsonAnnotation
            .contentType()
            .equals(AnnotationUtils.getAnnotationDefaultValue(Json.class, "contentType"))) {
      serdeDecl = contentTypeDecoratedSerdeDecl(serdeDecl, jsonAnnotation.contentType());
    }

    return new PayloadType(false, ty.toString(), boxedType(ty), serdeDecl);
  }

  private static String contentTypeDecoratedSerdeDecl(String serdeDecl, String contentType) {
    return "dev.restate.serde.Serde.withContentType(\"" + contentType + "\", " + serdeDecl + ")";
  }

  private static String serdeDecl(TypeMirror ty) {
    return switch (ty.getKind()) {
      case VOID -> "dev.restate.serde.Serde.VOID";
      default ->
          // Default to Jackson type reference serde
          "SERDE_FACTORY.create(dev.restate.serde.SerdeInfo.of(new dev.restate.serde.TypeRef<"
              + boxedType(ty)
              + ">() {}))";
    };
  }

  private static String boxedType(TypeMirror ty) {
    return switch (ty.getKind()) {
      case BOOLEAN -> "Boolean";
      case BYTE -> "Byte";
      case SHORT -> "Short";
      case INT -> "Integer";
      case LONG -> "Long";
      case CHAR -> "Char";
      case FLOAT -> "Float";
      case DOUBLE -> "Double";
      case VOID -> "Void";
      default -> ty.toString();
    };
  }

  private String getCustomSerdeClassCanonicalName(CustomSerdeFactory customSerdeFactoryAnnotation) {
    try {
      Class<?> clazz = customSerdeFactoryAnnotation.value();
      return clazz.getCanonicalName();
    } catch (MirroredTypeException e) {
      return e.getTypeMirror().toString();
    }
  }

  private static String sanitizeJavadoc(String documentation) {
    // TODO this needs probably a bit more work, but eventually people will use markdown for
    // javadocs anyway!
    return documentation == null ? null : documentation.trim().replaceAll("[\t\n\r] *", "\n");
  }
}
