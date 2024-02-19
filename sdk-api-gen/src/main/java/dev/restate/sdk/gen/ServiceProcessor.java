// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen;

import dev.restate.sdk.annotation.ServiceType;
import dev.restate.sdk.gen.model.Service;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

@SupportedAnnotationTypes("dev.restate.sdk.annotation.Service")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class ServiceProcessor extends AbstractProcessor {

  private HandlebarsCodegen serviceAdapterCodegen;
  private HandlebarsCodegen externalClientCodegen;
  private HandlebarsCodegen restateClientCodegen;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    this.serviceAdapterCodegen =
        new HandlebarsCodegen(
            processingEnv.getFiler(),
            "ServiceAdapter",
            Map.of(
                ServiceType.WORKFLOW,
                "templates.workflow",
                ServiceType.STATELESS,
                "templates",
                ServiceType.OBJECT,
                "templates"));
    this.externalClientCodegen =
        new HandlebarsCodegen(
            processingEnv.getFiler(),
            "ExternalClient",
            Map.of(
                ServiceType.WORKFLOW,
                "templates.workflow",
                ServiceType.STATELESS,
                "templates",
                ServiceType.OBJECT,
                "templates"));
    this.restateClientCodegen =
        new HandlebarsCodegen(
            processingEnv.getFiler(),
            "RestateClient",
            Map.of(
                ServiceType.WORKFLOW,
                "templates.workflow",
                ServiceType.STATELESS,
                "templates",
                ServiceType.OBJECT,
                "templates"));
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // Parsing phase
    List<Service> parsedServices =
        annotations.stream()
            .flatMap(annotation -> roundEnv.getElementsAnnotatedWith(annotation).stream())
            .map(
                e ->
                    Service.fromTypeElement(
                        (TypeElement) e,
                        processingEnv.getMessager(),
                        processingEnv.getElementUtils(),
                        processingEnv.getTypeUtils()))
            .collect(Collectors.toList());

    // Run code generation
    for (Service e : parsedServices) {
      try {
        this.serviceAdapterCodegen.generate(e);
        this.externalClientCodegen.generate(e);
        this.restateClientCodegen.generate(e);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    return true;
  }
}
