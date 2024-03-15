// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.template;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.FieldValueResolver;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.io.TemplateLoader;
import dev.restate.sdk.common.ComponentType;
import dev.restate.sdk.common.function.ThrowingFunction;
import dev.restate.sdk.gen.model.Component;
import dev.restate.sdk.gen.model.Handler;
import dev.restate.sdk.gen.model.HandlerType;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HandlebarsTemplateEngine {

  private final String baseTemplateName;
  private final Map<ComponentType, Template> templates;

  public HandlebarsTemplateEngine(
      String baseTemplateName,
      TemplateLoader templateLoader,
      Map<ComponentType, String> templates) {
    this.baseTemplateName = baseTemplateName;

    Handlebars handlebars = new Handlebars(templateLoader);
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
                            "Can't compile template for "
                                + e.getKey()
                                + " with base template name "
                                + baseTemplateName,
                            ex);
                      }
                    }));
  }

  public void generate(ThrowingFunction<String, Writer> createFile, Component component)
      throws Throwable {
    String fileName = component.getGeneratedClassFqcnPrefix() + this.baseTemplateName;
    try (Writer out = createFile.apply(fileName)) {
      this.templates
          .get(component.getComponentType())
          .apply(
              Context.newBuilder(new ComponentTemplateModel(component, this.baseTemplateName))
                  .resolver(FieldValueResolver.INSTANCE)
                  .build(),
              out);
    }
  }

  // --- classes to interact with the handlebars template

  static class ComponentTemplateModel {
    public final String originalClassPkg;
    public final String originalClassFqcn;
    public final String generatedClassSimpleNamePrefix;
    public final String generatedClassSimpleName;
    public final String componentName;
    public final String componentType;
    public final boolean isWorkflow;
    public final boolean isObject;
    public final boolean isService;
    public final List<HandlerTemplateModel> handlers;

    private ComponentTemplateModel(Component inner, String baseTemplateName) {
      this.originalClassPkg = inner.getTargetPkg().toString();
      this.originalClassFqcn = inner.getTargetFqcn().toString();
      this.generatedClassSimpleNamePrefix = inner.getSimpleComponentName();
      this.generatedClassSimpleName = this.generatedClassSimpleNamePrefix + baseTemplateName;
      this.componentName = inner.getFullyQualifiedComponentName();

      this.componentType = inner.getComponentType().toString();
      this.isWorkflow = inner.getComponentType() == ComponentType.WORKFLOW;
      this.isObject = inner.getComponentType() == ComponentType.VIRTUAL_OBJECT;
      this.isService = inner.getComponentType() == ComponentType.SERVICE;

      this.handlers =
          inner.getMethods().stream().map(HandlerTemplateModel::new).collect(Collectors.toList());
    }
  }

  static class HandlerTemplateModel {
    public final String name;
    public final String handlerType;
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

    private HandlerTemplateModel(Handler inner) {
      this.name = inner.getName().toString();
      this.handlerType = inner.getHandlerType().toString();
      this.isWorkflow = inner.getHandlerType() == HandlerType.WORKFLOW;
      this.isShared = inner.getHandlerType() == HandlerType.SHARED;
      this.isExclusive = inner.getHandlerType() == HandlerType.EXCLUSIVE;
      this.isStateless = inner.getHandlerType() == HandlerType.STATELESS;

      this.inputEmpty = inner.getInputType().isEmpty();
      this.inputFqcn = inner.getInputType().getName();
      this.inputSerdeDecl = inner.getInputType().getSerdeDecl();
      this.boxedInputFqcn = inner.getInputType().getBoxed();
      this.inputSerdeFieldName = "SERDE_" + this.name.toUpperCase() + "_INPUT";

      this.outputEmpty = inner.getOutputType().isEmpty();
      this.outputFqcn = inner.getOutputType().getName();
      this.outputSerdeDecl = inner.getOutputType().getSerdeDecl();
      this.boxedOutputFqcn = inner.getOutputType().getBoxed();
      this.outputSerdeFieldName = "SERDE_" + this.name.toUpperCase() + "_OUTPUT";
    }
  }
}
