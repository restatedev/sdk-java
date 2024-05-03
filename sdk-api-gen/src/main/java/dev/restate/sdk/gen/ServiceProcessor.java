// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen;

import dev.restate.sdk.common.BindableServiceFactory;
import dev.restate.sdk.common.ServiceType;
import dev.restate.sdk.common.function.ThrowingFunction;
import dev.restate.sdk.gen.model.Service;
import dev.restate.sdk.gen.template.HandlebarsTemplateEngine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes({
  "dev.restate.sdk.annotation.Service",
  "dev.restate.sdk.annotation.Workflow",
  "dev.restate.sdk.annotation.VirtualObject"
})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class ServiceProcessor extends AbstractProcessor {

  private HandlebarsTemplateEngine definitionsCodegen;
  private HandlebarsTemplateEngine bindableServiceFactoryCodegen;
  private HandlebarsTemplateEngine bindableServiceCodegen;
  private HandlebarsTemplateEngine clientCodegen;

  private static final Set<String> RESERVED_METHOD_NAMES = Set.of("send");

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    FilerTemplateLoader filerTemplateLoader = new FilerTemplateLoader(processingEnv.getFiler());

    this.definitionsCodegen =
        new HandlebarsTemplateEngine(
            "Definitions",
            filerTemplateLoader,
            Map.of(
                ServiceType.WORKFLOW,
                "templates/Definitions.hbs",
                ServiceType.SERVICE,
                "templates/Definitions.hbs",
                ServiceType.VIRTUAL_OBJECT,
                "templates/Definitions.hbs"),
            RESERVED_METHOD_NAMES);
    this.bindableServiceFactoryCodegen =
        new HandlebarsTemplateEngine(
            "BindableServiceFactory",
            filerTemplateLoader,
            Map.of(
                ServiceType.WORKFLOW,
                "templates/BindableServiceFactory.hbs",
                ServiceType.SERVICE,
                "templates/BindableServiceFactory.hbs",
                ServiceType.VIRTUAL_OBJECT,
                "templates/BindableServiceFactory.hbs"),
            RESERVED_METHOD_NAMES);
    this.bindableServiceCodegen =
        new HandlebarsTemplateEngine(
            "BindableService",
            filerTemplateLoader,
            Map.of(
                ServiceType.WORKFLOW,
                "templates/workflow/BindableService.hbs",
                ServiceType.SERVICE,
                "templates/BindableService.hbs",
                ServiceType.VIRTUAL_OBJECT,
                "templates/BindableService.hbs"),
            RESERVED_METHOD_NAMES);
    this.clientCodegen =
        new HandlebarsTemplateEngine(
            "Client",
            filerTemplateLoader,
            Map.of(
                ServiceType.WORKFLOW,
                "templates/workflow/Client.hbs",
                ServiceType.SERVICE,
                "templates/Client.hbs",
                ServiceType.VIRTUAL_OBJECT,
                "templates/Client.hbs"),
            RESERVED_METHOD_NAMES);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    ElementConverter converter =
        new ElementConverter(
            processingEnv.getMessager(),
            processingEnv.getElementUtils(),
            processingEnv.getTypeUtils());

    // Parsing phase
    List<Map.Entry<Element, Service>> parsedServices =
        annotations.stream()
            .flatMap(annotation -> roundEnv.getElementsAnnotatedWith(annotation).stream())
            .filter(e -> e.getKind().isClass() || e.getKind().isInterface())
            .map(e -> Map.entry((Element) e, converter.fromTypeElement((TypeElement) e)))
            .collect(Collectors.toList());

    Filer filer = processingEnv.getFiler();

    // Run code generation
    for (Map.Entry<Element, Service> e : parsedServices) {
      try {
        ThrowingFunction<String, Writer> fileCreator =
            name -> filer.createSourceFile(name, e.getKey()).openWriter();
        this.definitionsCodegen.generate(fileCreator, e.getValue());
        this.bindableServiceFactoryCodegen.generate(fileCreator, e.getValue());
        this.bindableServiceCodegen.generate(fileCreator, e.getValue());
        this.clientCodegen.generate(fileCreator, e.getValue());
      } catch (Throwable ex) {
        throw new RuntimeException(ex);
      }
    }

    // META-INF
    Path resourceFilePath;
    try {
      resourceFilePath =
          readOrCreateResource(
              processingEnv.getFiler(),
              "META-INF/services/" + BindableServiceFactory.class.getCanonicalName());
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
      for (Map.Entry<Element, Service> e : parsedServices) {
        writer.write(e.getValue().getGeneratedClassFqcnPrefix() + "BindableServiceFactory");
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
