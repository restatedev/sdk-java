// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.model;

import dev.restate.sdk.annotation.*;
import dev.restate.sdk.common.ComponentType;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

public class Service {

  private final CharSequence targetPkg;
  private final CharSequence targetFqcn;
  private final String componentName;
  private final ComponentType componentType;
  private final List<Method> methods;

  Service(
      CharSequence targetPkg,
      CharSequence targetFqcn,
      String componentName,
      ComponentType componentType,
      List<Method> methods) {
    this.targetPkg = targetPkg;
    this.targetFqcn = targetFqcn;
    this.componentName = componentName;

    this.componentType = componentType;
    this.methods = methods;
  }

  public CharSequence getTargetPkg() {
    return this.targetPkg;
  }

  public CharSequence getTargetFqcn() {
    return this.targetFqcn;
  }

  public String getFullyQualifiedComponentName() {
    return this.componentName;
  }

  public String getSimpleComponentName() {
    return this.componentName.substring(this.componentName.lastIndexOf('.') + 1);
  }

  public CharSequence getGeneratedClassFqcnPrefix() {
    if (this.targetPkg == null || this.targetPkg.length() == 0) {
      return getSimpleComponentName();
    }
    return this.targetPkg + "." + getSimpleComponentName();
  }

  public ComponentType getComponentType() {
    return componentType;
  }

  public List<Method> getMethods() {
    return methods;
  }

  public static Service fromTypeElement(
      TypeElement element, Messager messager, Elements elements, Types types) {
    validateType(element, messager);

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

    ComponentType type =
        isAnnotatedWithWorkflow
            ? ComponentType.WORKFLOW
            : isAnnotatedWithService ? ComponentType.SERVICE : ComponentType.VIRTUAL_OBJECT;

    // Infer names

    CharSequence targetPkg = elements.getPackageOf(element).getQualifiedName();
    CharSequence targetFqcn = element.getQualifiedName();

    String componentName =
        isAnnotatedWithService
            ? serviceAnnotation.name()
            : isAnnotatedWithVirtualObject
                ? virtualObjectAnnotation.name()
                : workflowAnnotation.name();
    if (componentName.isEmpty()) {
      // Use FQCN
      // With this logic we make sure we flatten subclasses names
      String simpleComponentName =
          targetFqcn.toString().substring(targetPkg.length()).replaceAll(Pattern.quote("."), "");
      componentName =
          targetPkg.length() > 0 ? targetPkg + "." + simpleComponentName : simpleComponentName;
    }

    // Compute methods
    List<Method> methods =
        elements.getAllMembers(element).stream()
            .filter(e -> e instanceof ExecutableElement)
            .filter(
                e ->
                    e.getAnnotation(Handler.class) != null
                        || e.getAnnotation(Workflow.class) != null
                        || e.getAnnotation(Exclusive.class) != null
                        || e.getAnnotation(Shared.class) != null)
            .map(
                e ->
                    Method.fromExecutableElement(
                        type, ((ExecutableElement) e), messager, elements, types))
            .collect(Collectors.toList());
    validateMethods(type, methods, element, messager);

    return new Service(targetPkg, targetFqcn, componentName, type, methods);
  }

  private static void validateType(TypeElement element, Messager messager) {
    if (!element.getTypeParameters().isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The EntityProcessor doesn't support services with generics",
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

  private static void validateMethods(
      ComponentType componentType, List<Method> methods, TypeElement element, Messager messager) {
    // Additional validation for Workflow types
    if (componentType.equals(ComponentType.WORKFLOW)) {
      if (methods.stream().filter(m -> m.getMethodType().equals(MethodType.WORKFLOW)).count()
          != 1) {
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "Workflow services must have exactly one method annotated as @Workflow",
            element);
      }
    }
  }
}
