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
import dev.restate.sdk.common.ServiceAdapter;
import dev.restate.sdk.gen.model.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

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

    // META-INF
    Path resourceFilePath;
    try {
      resourceFilePath =
          readOrCreateResource(
              processingEnv.getFiler(),
              "META-INF/services/" + ServiceAdapter.class.getCanonicalName());
      Files.createDirectories(resourceFilePath.getParent());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try (BufferedWriter writer =
        Files.newBufferedWriter(
            resourceFilePath,
            StandardCharsets.UTF_8,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND)) {
      for (Service svc : parsedServices) {
        writer.write(svc.getGeneratedClassFqcnPrefix() + "ServiceAdapter");
        writer.write('\n');
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return false;
  }

  public static Path readOrCreateResource(Filer filer, String file) throws IOException {
    try {
      FileObject fileObject = filer.getResource(StandardLocation.CLASS_OUTPUT, "", file);
      return new File(fileObject.toUri()).toPath();
    } catch (IOException e) {
      FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", file);
      return new File(fileObject.toUri()).toPath();
    }
  }
}
