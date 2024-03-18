// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen;

import dev.restate.sdk.common.BindableComponentFactory;
import dev.restate.sdk.common.ComponentType;
import dev.restate.sdk.common.function.ThrowingFunction;
import dev.restate.sdk.gen.model.Component;
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
public class ComponentProcessor extends AbstractProcessor {

  private HandlebarsTemplateEngine bindableComponentFactoryCodegen;
  private HandlebarsTemplateEngine bindableComponentCodegen;
  private HandlebarsTemplateEngine clientCodegen;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    FilerTemplateLoader filerTemplateLoader = new FilerTemplateLoader(processingEnv.getFiler());

    this.bindableComponentFactoryCodegen =
        new HandlebarsTemplateEngine(
            "BindableComponentFactory",
            filerTemplateLoader,
            Map.of(
                ComponentType.WORKFLOW,
                "templates/BindableComponentFactory.hbs",
                ComponentType.SERVICE,
                "templates/BindableComponentFactory.hbs",
                ComponentType.VIRTUAL_OBJECT,
                "templates/BindableComponentFactory.hbs"));
    this.bindableComponentCodegen =
        new HandlebarsTemplateEngine(
            "BindableComponent",
            filerTemplateLoader,
            Map.of(
                ComponentType.WORKFLOW,
                "templates/workflow/BindableComponent.hbs",
                ComponentType.SERVICE,
                "templates/BindableComponent.hbs",
                ComponentType.VIRTUAL_OBJECT,
                "templates/BindableComponent.hbs"));
    this.clientCodegen =
        new HandlebarsTemplateEngine(
            "Client",
            filerTemplateLoader,
            Map.of(
                ComponentType.WORKFLOW,
                "templates/workflow/Client.hbs",
                ComponentType.SERVICE,
                "templates/Client.hbs",
                ComponentType.VIRTUAL_OBJECT,
                "templates/Client.hbs"));
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    ElementConverter converter =
        new ElementConverter(
            processingEnv.getMessager(),
            processingEnv.getElementUtils(),
            processingEnv.getTypeUtils());

    // Parsing phase
    List<Map.Entry<Element, Component>> parsedServices =
        annotations.stream()
            .flatMap(annotation -> roundEnv.getElementsAnnotatedWith(annotation).stream())
            .filter(e -> e.getKind().isClass() || e.getKind().isInterface())
            .map(e -> Map.entry((Element) e, converter.fromTypeElement((TypeElement) e)))
            .collect(Collectors.toList());

    Filer filer = processingEnv.getFiler();

    // Run code generation
    for (Map.Entry<Element, Component> e : parsedServices) {
      try {
        ThrowingFunction<String, Writer> fileCreator =
            name -> filer.createSourceFile(name, e.getKey()).openWriter();
        this.bindableComponentFactoryCodegen.generate(fileCreator, e.getValue());
        this.bindableComponentCodegen.generate(fileCreator, e.getValue());
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
              "META-INF/services/" + BindableComponentFactory.class.getCanonicalName());
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
      for (Map.Entry<Element, Component> e : parsedServices) {
        writer.write(e.getValue().getGeneratedClassFqcnPrefix() + "BindableComponentFactory");
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
