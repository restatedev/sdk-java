// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.model;

import dev.restate.sdk.Context;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.*;
import dev.restate.sdk.common.ComponentType;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.WorkflowSharedContext;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.jspecify.annotations.Nullable;

public class Method {

  private final CharSequence name;
  private final MethodType methodType;
  private final @Nullable TypeMirror inputType;
  private final @Nullable TypeMirror outputType;

  public Method(
      CharSequence name,
      MethodType methodType,
      @Nullable TypeMirror inputType,
      @Nullable TypeMirror outputType) {
    this.name = name;
    this.methodType = methodType;
    this.inputType = inputType;
    this.outputType = outputType;
  }

  public CharSequence getName() {
    return name;
  }

  public MethodType getMethodType() {
    return methodType;
  }

  @Nullable
  public TypeMirror getInputType() {
    return inputType;
  }

  @Nullable
  public TypeMirror getOutputType() {
    return outputType;
  }

  public static Method fromExecutableElement(
      ComponentType componentType,
      ExecutableElement element,
      Messager messager,
      Elements elements,
      Types types) {
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

    MethodType methodType =
        isAnnotatedWithWorkflow
            ? MethodType.WORKFLOW
            : isAnnotatedWithShared
                ? MethodType.SHARED
                : isAnnotatedWithExclusive
                    ? MethodType.EXCLUSIVE
                    : defaultMethodType(componentType, element, messager);

    validateMethodSignature(componentType, methodType, element, messager, elements, types);

    return new Method(
        element.getSimpleName(),
        methodType,
        element.getParameters().size() > 1 ? element.getParameters().get(1).asType() : null,
        !element.getReturnType().getKind().equals(TypeKind.VOID) ? element.getReturnType() : null);
  }

  private static MethodType defaultMethodType(
      ComponentType componentType, ExecutableElement element, Messager messager) {
    switch (componentType) {
      case SERVICE:
        return MethodType.STATELESS;
      case VIRTUAL_OBJECT:
        return MethodType.EXCLUSIVE;
      case WORKFLOW:
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "Workflow methods MUST be annotated with either @Shared or @Workflow",
            element);
    }
    throw new IllegalStateException(
        "Workflow methods MUST be annotated with either @Shared or @Workflow");
  }

  private static void validateMethodSignature(
      ComponentType componentType,
      MethodType methodType,
      ExecutableElement element,
      Messager messager,
      Elements elements,
      Types types) {
    switch (methodType) {
      case SHARED:
        if (componentType == ComponentType.WORKFLOW) {
          validateFirstParameterType(
              WorkflowSharedContext.class, element, messager, elements, types);
        } else {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "The annotation @Shared is not supported by the service type " + componentType,
              element);
        }
        break;
      case EXCLUSIVE:
        if (componentType == ComponentType.VIRTUAL_OBJECT) {
          validateFirstParameterType(ObjectContext.class, element, messager, elements, types);
        } else {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "The annotation @Exclusive is not supported by the service type " + componentType,
              element);
        }
        break;
      case STATELESS:
        validateFirstParameterType(Context.class, element, messager, elements, types);
        break;
      case WORKFLOW:
        if (componentType == ComponentType.WORKFLOW) {
          validateFirstParameterType(WorkflowContext.class, element, messager, elements, types);
        } else {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "The annotation @Shared is not supported by the service type " + componentType,
              element);
        }
        break;
    }
  }

  private static void validateFirstParameterType(
      Class<?> clazz,
      ExecutableElement element,
      Messager messager,
      Elements elements,
      Types types) {
    if (!types.isSameType(
        element.getParameters().get(0).asType(),
        elements.getTypeElement(clazz.getCanonicalName()).asType())) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The method signature must have " + clazz.getCanonicalName() + " as first parameter",
          element);
    }
  }
}
