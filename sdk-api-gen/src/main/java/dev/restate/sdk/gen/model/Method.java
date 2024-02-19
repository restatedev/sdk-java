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
import dev.restate.sdk.KeyedContext;
import dev.restate.sdk.annotation.*;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.WorkflowSharedContext;
import javax.annotation.Nullable;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

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
      ServiceType serviceType,
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
    boolean isAnnotatedWithWorkflow = element.getAnnotation(Workflow.class) != null;
    boolean isAnnotatedWithExclusive = element.getAnnotation(Exclusive.class) != null;
    boolean isAnnotatedWithStateless = element.getAnnotation(Stateless.class) != null;

    // Should be guaranteed by the caller
    assert isAnnotatedWithWorkflow
        || isAnnotatedWithShared
        || isAnnotatedWithExclusive
        || isAnnotatedWithStateless;

    // Check there's no more than one annotation
    if (!Boolean.logicalXor(
        isAnnotatedWithShared,
        Boolean.logicalXor(
            isAnnotatedWithWorkflow,
            Boolean.logicalXor(isAnnotatedWithExclusive, isAnnotatedWithStateless)))) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "You can annotate only one method with a Restate method annotation",
          element);
    }

    MethodType methodType =
        isAnnotatedWithWorkflow
            ? MethodType.WORKFLOW
            : isAnnotatedWithShared
                ? MethodType.SHARED
                : isAnnotatedWithExclusive ? MethodType.EXCLUSIVE : MethodType.STATELESS;

    validateMethodSignature(serviceType, methodType, element, messager, elements, types);

    return new Method(
        element.getSimpleName(),
        methodType,
        element.getParameters().size() > 1 ? element.getParameters().get(1).asType() : null,
        !element.getReturnType().getKind().equals(TypeKind.VOID) ? element.getReturnType() : null);
  }

  private static void validateMethodSignature(
      ServiceType serviceType,
      MethodType methodType,
      ExecutableElement element,
      Messager messager,
      Elements elements,
      Types types) {
    switch (methodType) {
      case SHARED:
        if (serviceType == ServiceType.WORKFLOW) {
          validateFirstParameterType(
              WorkflowSharedContext.class, element, messager, elements, types);
        } else {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "The annotation @Shared is not supported by the service type " + serviceType,
              element);
        }
        break;
      case EXCLUSIVE:
        if (serviceType == ServiceType.OBJECT) {
          validateFirstParameterType(KeyedContext.class, element, messager, elements, types);
        } else {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "The annotation @Exclusive is not supported by the service type " + serviceType,
              element);
        }
        break;
      case STATELESS:
        validateFirstParameterType(Context.class, element, messager, elements, types);
        break;
      case WORKFLOW:
        if (serviceType == ServiceType.WORKFLOW) {
          validateFirstParameterType(WorkflowContext.class, element, messager, elements, types);
        } else {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "The annotation @Shared is not supported by the service type " + serviceType,
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
