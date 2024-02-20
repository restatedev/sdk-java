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

  private final CharSequence pkg;
  private final CharSequence fqcn;
  private final CharSequence fqsn;
  private final CharSequence generatedClassSimpleName;
  private final ServiceType serviceType;
  private final List<Method> methods;

  Service(CharSequence pkg, CharSequence fqcn, ServiceType serviceType, List<Method> methods) {
    this.pkg = pkg;
    this.fqcn = fqcn;

    // Service name flattens subclasses!
    this.generatedClassSimpleName =
        fqcn.toString().substring(pkg.length()).replaceAll(Pattern.quote("."), "");
    this.fqsn =
        this.pkg.length() > 0
            ? this.pkg + "." + this.generatedClassSimpleName
            : this.generatedClassSimpleName;

    this.serviceType = serviceType;
    this.methods = methods;
  }

  public CharSequence getPkg() {
    return pkg;
  }

  public CharSequence getOriginalClassFqcn() {
    return this.fqcn;
  }

  public CharSequence getFqsn() {
    return fqsn;
  }

  public CharSequence getGeneratedClassSimpleNamePrefix() {
    return this.generatedClassSimpleName;
  }

  public CharSequence getGeneratedClassFqcnPrefix() {
    // This might be different if the package name of the service can be modified
    return fqsn;
  }

  public ServiceType getServiceType() {
    return serviceType;
  }

  public List<Method> getMethods() {
    return methods;
  }

  public static Service fromTypeElement(
      TypeElement element, Messager messager, Elements elements, Types types) {
    validateType(element, messager);

    ServiceType type = element.getAnnotation(dev.restate.sdk.annotation.Service.class).value();

    List<Method> methods =
        elements.getAllMembers(element).stream()
            .filter(e -> e instanceof ExecutableElement)
            .filter(
                e ->
                    e.getAnnotation(Shared.class) != null
                        || e.getAnnotation(Workflow.class) != null
                        || e.getAnnotation(Exclusive.class) != null
                        || e.getAnnotation(Stateless.class) != null)
            .map(
                e ->
                    Method.fromExecutableElement(
                        type, ((ExecutableElement) e), messager, elements, types))
            .collect(Collectors.toList());
    validateMethods(type, methods, element, messager);

    return new Service(
        elements.getPackageOf(element).getQualifiedName(),
        element.getQualifiedName(),
        type,
        methods);
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
      ServiceType serviceType, List<Method> methods, TypeElement element, Messager messager) {
    // Additional validation for Workflow types
    if (serviceType.equals(ServiceType.WORKFLOW)) {
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
