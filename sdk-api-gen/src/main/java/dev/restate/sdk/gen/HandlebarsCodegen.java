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
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.io.AbstractTemplateLoader;
import com.github.jknack.handlebars.io.TemplateSource;
import dev.restate.sdk.common.ComponentType;
import dev.restate.sdk.gen.model.Method;
import dev.restate.sdk.gen.model.MethodType;
import dev.restate.sdk.gen.model.Service;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.jspecify.annotations.Nullable;

public class HandlebarsCodegen {

  private final Filer filer;
  private final String baseTemplateName;
  private final Map<ComponentType, Template> templates;

  public HandlebarsCodegen(
      Filer filer, String baseTemplateName, Map<ComponentType, String> templates) {
    this.filer = filer;
    this.baseTemplateName = baseTemplateName;

    Handlebars handlebars = new Handlebars(new FilerTemplateLoader(filer, this.baseTemplateName));
    handlebars.registerHelpers(StringHelpers.class);

    this.templates =
        templates.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> {
                      try {
                        return handlebars.compile(e.getValue());
                      } catch (IOException ex) {
                        throw new RuntimeException(
                            "Can't compile template for service "
                                + e.getKey()
                                + " with base template name "
                                + baseTemplateName,
                            ex);
                      }
                    }));
  }

  public void generate(Service service) throws IOException {
    JavaFileObject entityAdapterFile =
        filer.createSourceFile(service.getGeneratedClassFqcnPrefix() + this.baseTemplateName);
    try (Writer out = entityAdapterFile.openWriter()) {
      this.templates
          .get(service.getComponentType())
          .apply(
              Context.newBuilder(new EntityTemplateModel(service, this.baseTemplateName))
                  .resolver(FieldValueResolver.INSTANCE)
                  .build(),
              out);
    }
  }

  // --- classes to interact with the handlebars template

  static class EntityTemplateModel {
    public final String originalClassPkg;
    public final String originalClassFqcn;
    public final String generatedClassSimpleNamePrefix;
    public final String generatedClassSimpleName;
    public final String componentName;
    public final String componentType;
    public final boolean isWorkflow;
    public final boolean isObject;
    public final boolean isService;
    public final List<MethodTemplateModel> methods;

    private EntityTemplateModel(Service inner, String baseTemplateName) {
      this.originalClassPkg = inner.getTargetPkg().toString();
      this.originalClassFqcn = inner.getTargetFqcn().toString();
      this.generatedClassSimpleNamePrefix = inner.getSimpleComponentName();
      this.generatedClassSimpleName = this.generatedClassSimpleNamePrefix + baseTemplateName;
      this.componentName = inner.getFullyQualifiedComponentName();

      this.componentType = inner.getComponentType().toString();
      this.isWorkflow = inner.getComponentType() == ComponentType.WORKFLOW;
      this.isObject = inner.getComponentType() == ComponentType.VIRTUAL_OBJECT;
      this.isService = inner.getComponentType() == ComponentType.SERVICE;

      this.methods =
          inner.getMethods().stream().map(MethodTemplateModel::new).collect(Collectors.toList());
    }
  }

  static class MethodTemplateModel {
    public final String name;
    public final String methodType;
    public final boolean isWorkflow;
    public final boolean isShared;
    public final boolean isStateless;
    public final boolean isExclusive;

    public final boolean inputEmpty;
    public final String inputFqcn;
    public final String inputSerdeDecl;
    public final String boxedInputFqcn;
    public final String inputSerdeFieldName;

    public final boolean outputEmpty;
    public final String outputFqcn;
    public final String outputSerdeDecl;
    public final String boxedOutputFqcn;
    public final String outputSerdeFieldName;

    private MethodTemplateModel(Method inner) {
      this.name = inner.getName().toString();
      this.methodType = inner.getMethodType().toString();
      this.isWorkflow = inner.getMethodType() == MethodType.WORKFLOW;
      this.isShared = inner.getMethodType() == MethodType.SHARED;
      this.isExclusive = inner.getMethodType() == MethodType.EXCLUSIVE;
      this.isStateless = inner.getMethodType() == MethodType.STATELESS;

      this.inputEmpty = inner.getInputType() == null;
      this.inputFqcn = this.inputEmpty ? "" : inner.getInputType().toString();
      this.inputSerdeDecl = serdeDecl(inner.getInputType());
      this.boxedInputFqcn = boxedType(inner.getInputType());
      this.inputSerdeFieldName = "SERDE_" + this.name.toUpperCase() + "_INPUT";

      this.outputEmpty = inner.getOutputType() == null;
      this.outputFqcn = this.outputEmpty ? "" : inner.getOutputType().toString();
      this.outputSerdeDecl = serdeDecl(inner.getOutputType());
      this.boxedOutputFqcn = boxedType(inner.getOutputType());
      this.outputSerdeFieldName = "SERDE_" + this.name.toUpperCase() + "_OUTPUT";
    }

    private static String serdeDecl(@Nullable TypeMirror ty) {
      if (ty == null) {
        return "dev.restate.sdk.common.CoreSerdes.VOID";
      }
      switch (ty.getKind()) {
        case BOOLEAN:
          return "dev.restate.sdk.common.CoreSerdes.JSON_BOOLEAN";
        case BYTE:
          return "dev.restate.sdk.common.CoreSerdes.JSON_BYTE";
        case SHORT:
          return "dev.restate.sdk.common.CoreSerdes.JSON_SHORT";
        case INT:
          return "dev.restate.sdk.common.CoreSerdes.JSON_INT";
        case LONG:
          return "dev.restate.sdk.common.CoreSerdes.JSON_LONG";
        case CHAR:
          return "dev.restate.sdk.common.CoreSerdes.JSON_CHAR";
        case FLOAT:
          return "dev.restate.sdk.common.CoreSerdes.JSON_FLOAT";
        case DOUBLE:
          return "dev.restate.sdk.common.CoreSerdes.JSON_DOUBLE";
        case VOID:
          return "dev.restate.sdk.common.CoreSerdes.VOID";
        default:
          // Default to Jackson type reference serde
          return "dev.restate.sdk.serde.jackson.JacksonSerdes.of(new com.fasterxml.jackson.core.type.TypeReference<"
              + ty
              + ">() {})";
      }
    }

    private static String boxedType(@Nullable TypeMirror ty) {
      if (ty == null) {
        return "Void";
      }
      switch (ty.getKind()) {
        case BOOLEAN:
          return "Boolean";
        case BYTE:
          return "Byte";
        case SHORT:
          return "Short";
        case INT:
          return "Integer";
        case LONG:
          return "Long";
        case CHAR:
          return "Char";
        case FLOAT:
          return "Float";
        case DOUBLE:
          return "Double";
        case VOID:
          return "Void";
        default:
          return ty.toString();
      }
    }
  }

  // We need this because the built-in ClassLoaderTemplateLoader is not reliable in the annotation
  // processor context
  private static class FilerTemplateLoader extends AbstractTemplateLoader {
    private final Filer filer;
    private final String templateName;

    public FilerTemplateLoader(Filer filer, String baseTemplateName) {
      this.filer = filer;
      this.templateName = baseTemplateName + ".hbs";
    }

    @Override
    public TemplateSource sourceAt(String location) {
      return new TemplateSource() {
        @Override
        public String content(Charset charset) throws IOException {
          return filer
              .getResource(StandardLocation.ANNOTATION_PROCESSOR_PATH, location, templateName)
              .getCharContent(true)
              .toString();
        }

        @Override
        public String filename() {
          return "/" + location.replace('.', '/') + "/" + templateName;
        }

        @Override
        public long lastModified() {
          return 0;
        }
      };
    }
  }
}
