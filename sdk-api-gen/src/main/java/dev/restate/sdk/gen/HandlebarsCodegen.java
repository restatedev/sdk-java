// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.FieldValueResolver;
import com.github.jknack.handlebars.io.AbstractTemplateLoader;
import com.github.jknack.handlebars.io.TemplateSource;
import dev.restate.sdk.gen.model.Method;
import dev.restate.sdk.gen.model.MethodType;
import dev.restate.sdk.gen.model.Service;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.processing.Filer;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

public class HandlebarsCodegen {

  private final Filer filer;
  private final String templateName;
  private final Template template;

  public HandlebarsCodegen(Filer filer, String templateName) throws IOException {
    this.filer = filer;
    this.templateName = templateName;

    Handlebars handlebars = new Handlebars(new FilerTemplateLoader(filer));
    this.template = handlebars.compile(templateName);
  }

  public void generate(Service service) throws IOException {
    JavaFileObject entityAdapterFile =
        filer.createSourceFile(service.getFqcn() + this.templateName);
    try (Writer out = entityAdapterFile.openWriter()) {
      this.template.apply(
          Context.newBuilder(new EntityTemplateModel(service))
              .resolver(FieldValueResolver.INSTANCE)
              .build(),
          out);
    }
  }

  // --- classes to interact with the handlebars template

  static class EntityTemplateModel {
    public final String packageName;
    public final String className;
    public final String fqcn;
    public final List<MethodTemplateModel> methods;

    private EntityTemplateModel(Service inner) {
      this.packageName = inner.getPkg() != null ? inner.getPkg().toString() : null;
      this.className = inner.getSimpleClassName().toString();
      this.fqcn = inner.getFqcn().toString();
      this.methods =
          inner.getMethods().stream().map(MethodTemplateModel::new).collect(Collectors.toList());
    }
  }

  static class MethodTemplateModel {
    public final String builderMethod;
    public final String name;
    public final String descFieldName;
    public final boolean isWorkflow;
    public final boolean isShared;

    public final boolean inputEmpty;
    public final String inputFqcn;
    public final String inputSerdeDecl;
    public final String inputSerdeFieldName;

    public final boolean outputEmpty;
    public final String outputFqcn;
    public final String outputSerdeDecl;
    public final String outputSerdeFieldName;

    private MethodTemplateModel(Method inner) {
      this.builderMethod =
          inner.getMethodType().equals(MethodType.SHARED) ? "withShared" : "withExclusive";
      this.name = inner.getName().toString();
      this.descFieldName = "DESC_" + this.name.toUpperCase();
      this.isWorkflow = inner.getMethodType() == MethodType.WORKFLOW;
      this.isShared = inner.getMethodType() == MethodType.SHARED;

      this.inputEmpty = inner.getInputType() == null;
      this.inputFqcn = this.inputEmpty ? "" : inner.getInputType().toString();
      this.inputSerdeDecl = serdeDecl(inner.getInputType());
      this.inputSerdeFieldName = "SERDE_" + this.name.toUpperCase() + "_INPUT";

      this.outputEmpty = inner.getOutputType() == null;
      this.outputFqcn = this.outputEmpty ? "" : inner.getOutputType().toString();
      this.outputSerdeDecl = serdeDecl(inner.getOutputType());
      this.outputSerdeFieldName = "SERDE_" + this.name.toUpperCase() + "_OUTPUT";
    }

    private static String serdeDecl(@Nullable TypeMirror ty) {
      if (ty == null) {
        return "dev.restate.sdk.common.CoreSerdes.VOID";
      }
      return "dev.restate.sdk.serde.jackson.JacksonSerdes.of(new com.fasterxml.jackson.core.type.TypeReference<"
          + ty
          + ">() {})";
    }
  }

  // We need this because the built-in ClassLoaderTemplateLoader is not reliable in the annotation
  // processor context
  private static class FilerTemplateLoader extends AbstractTemplateLoader {
    private final Filer filer;

    public FilerTemplateLoader(Filer filer) {
      this.filer = filer;
    }

    @Override
    public TemplateSource sourceAt(String location) {
      return new TemplateSource() {
        @Override
        public String content(Charset charset) throws IOException {
          return filer
              .getResource(
                  StandardLocation.ANNOTATION_PROCESSOR_PATH, "templates", location + ".hbs")
              .getCharContent(true)
              .toString();
        }

        @Override
        public String filename() {
          return "/templates/" + location + ".hbs";
        }

        @Override
        public long lastModified() {
          return 0;
        }
      };
    }
  }
}
