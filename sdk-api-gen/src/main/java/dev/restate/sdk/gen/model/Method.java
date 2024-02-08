// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.model;

import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.Workflow;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.WorkflowSharedContext;
import javax.annotation.Nullable;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
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
      ExecutableElement element, Messager messager, Elements elements, Types types) {
    if (!element.getTypeParameters().isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The EntityProcessor doesn't support methods with generics",
          element);
    }
    if (element.getKind().equals(ElementKind.CONSTRUCTOR)) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "You cannot annotate a constructor as @Workflow and @Shared");
    }
    if (element.getKind().equals(ElementKind.STATIC_INIT)) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "You cannot annotate a static init as @Workflow and @Shared");
    }

    boolean isAnnotatedWithShared = element.getAnnotation(Shared.class) != null;
    boolean isAnnotatedWithWorkflow = element.getAnnotation(Workflow.class) != null;

    if (isAnnotatedWithShared && isAnnotatedWithWorkflow) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "You cannot annotate a method both as @Workflow and @Shared");
    }
    if (!isAnnotatedWithWorkflow && !isAnnotatedWithShared) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "The method should be annotated either with @Workflow or @Shared");
    }
    if (element.getParameters().isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "The method signature must have at least one parameter");
    }

    boolean firstParameterIsExclusiveContext =
        types.isSameType(
            element.getParameters().get(0).asType(),
            elements.getTypeElement(WorkflowContext.class.getCanonicalName()).asType());
    boolean firstParameterIsSharedContext =
        types.isSameType(
            element.getParameters().get(0).asType(),
            elements.getTypeElement(WorkflowSharedContext.class.getCanonicalName()).asType());

    if (isAnnotatedWithShared && !firstParameterIsSharedContext) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The method signature must have WorkflowSharedContext as first parameter");
    }
    if (isAnnotatedWithWorkflow && !firstParameterIsExclusiveContext) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The method signature must have WorkflowContext as first parameter");
    }

    if (element.getModifiers().contains(Modifier.PRIVATE)) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The annotated method is private. The method must be at least package-private to be accessible from the code-generated classes",
          element);
    }

    return new Method(
        element.getSimpleName(),
        isAnnotatedWithShared ? MethodType.SHARED : MethodType.WORKFLOW,
        element.getParameters().size() > 1 ? element.getParameters().get(1).asType() : null,
        !element.getReturnType().getKind().equals(TypeKind.VOID) ? element.getReturnType() : null);
  }
}
