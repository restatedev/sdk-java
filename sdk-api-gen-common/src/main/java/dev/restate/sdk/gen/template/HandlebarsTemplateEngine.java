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
import com.github.jknack.handlebars.internal.text.StringEscapeUtils;
import com.github.jknack.handlebars.io.TemplateLoader;
import dev.restate.common.function.ThrowingFunction;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.sdk.gen.model.Handler;
import dev.restate.sdk.gen.model.HandlerType;
import dev.restate.sdk.gen.model.Service;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HandlebarsTemplateEngine {

  private final String baseTemplateName;
  private final Map<ServiceType, Template> templates;
  private final Set<String> handlerNamesToPrefix;

  public HandlebarsTemplateEngine(
      String baseTemplateName,
      TemplateLoader templateLoader,
      Map<ServiceType, String> templates,
      Set<String> handlerNamesToPrefix) {
    this.baseTemplateName = baseTemplateName;
    this.handlerNamesToPrefix = handlerNamesToPrefix;

    Handlebars handlebars = new Handlebars(templateLoader);
    handlebars.registerHelpers(StringHelpers.class);
    handlebars.<HandlerTemplateModel>registerHelper(
        "targetExpr",
        (h, options) -> {
          return switch (h.serviceType) {
            case SERVICE ->
                String.format(
                    "dev.restate.common.Target.service(%s.SERVICE_NAME, \"%s\")",
                    h.metadataClass, h.restateName);
            case VIRTUAL_OBJECT ->
                String.format(
                    "dev.restate.common.Target.virtualObject(%s.SERVICE_NAME, %s, \"%s\")",
                    h.metadataClass, options.param(0), h.restateName);
            case WORKFLOW ->
                String.format(
                    "dev.restate.common.Target.workflow(%s.SERVICE_NAME, %s, \"%s\")",
                    h.metadataClass, options.param(0), h.restateName);
          };
        });
    handlebars.registerHelpers(StringEscapeUtils.class);

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

  public void generate(ThrowingFunction<String, Writer> createFile, Service service)
      throws Throwable {
    String fileName = service.getFqcnGeneratedNamePrefix() + this.baseTemplateName;
    try (Writer out = createFile.apply(fileName)) {
      this.templates
          .get(service.getServiceType())
          .apply(
              Context.newBuilder(
                      new ServiceTemplateModel(
                          service, this.baseTemplateName, this.handlerNamesToPrefix))
                  .resolver(FieldValueResolver.INSTANCE)
                  .build(),
              out);
    }
  }

  // --- classes to interact with the handlebars template

  static class ServiceTemplateModel {
    public final String originalClassPkg;
    public final String originalClassFqcn;
    public final String generatedClassSimpleNamePrefix;
    public final String generatedClassSimpleName;
    public final String restateServiceName;
    public final String documentation;

    public final String metadataClass;
    public final String handlersClass;

    public final String serdeFactoryDecl;
    public final String serdeFactoryRef;

    public final String serviceType;
    public final boolean isWorkflow;
    public final boolean isObject;
    public final boolean isService;
    public final boolean isKeyed;
    public final List<HandlerTemplateModel> handlers;

    private ServiceTemplateModel(
        Service inner, String baseTemplateName, Set<String> handlerNamesToPrefix) {
      this.originalClassPkg = inner.getTargetPkg().toString();
      this.originalClassFqcn = inner.getTargetFqcn().toString();
      this.generatedClassSimpleNamePrefix = inner.getSimpleClassGeneratedNamePrefix();
      this.generatedClassSimpleName = this.generatedClassSimpleNamePrefix + baseTemplateName;
      this.restateServiceName = inner.getRestateServiceName();

      this.handlersClass = this.generatedClassSimpleNamePrefix + "Handlers";
      this.metadataClass = this.handlersClass + ".Metadata";

      this.serdeFactoryDecl = inner.getSerdeFactoryDecl();
      this.serdeFactoryRef = metadataClass + ".SERDE_FACTORY";

      this.documentation = inner.getDocumentation();

      this.serviceType = inner.getServiceType().toString();
      this.isWorkflow = inner.getServiceType() == ServiceType.WORKFLOW;
      this.isObject = inner.getServiceType() == ServiceType.VIRTUAL_OBJECT;
      this.isService = inner.getServiceType() == ServiceType.SERVICE;
      this.isKeyed = this.isObject || this.isWorkflow;

      this.handlers =
          inner.getMethods().stream()
              .map(
                  h ->
                      new HandlerTemplateModel(
                          h, inner.getServiceType(), metadataClass, handlerNamesToPrefix))
              .collect(Collectors.toList());
    }
  }

  static class HandlerTemplateModel {
    public final String name;
    public final String handlersClassMethodName;
    public final String restateName;
    public final String handlerType;
    public final boolean isWorkflow;
    public final boolean isShared;
    public final boolean isStateless;
    public final boolean isExclusive;

    private final ServiceType serviceType;
    private final String metadataClass;
    public final String documentation;

    public final boolean inputEmpty;
    public final String inputFqcn;
    public final String inputSerdeDecl;
    public final String boxedInputFqcn;
    public final String inputSerdeFieldName;
    public final String inputAcceptContentType;
    public final String inputSerdeRef;

    public final boolean outputEmpty;
    public final String outputFqcn;
    public final String outputSerdeDecl;
    public final String boxedOutputFqcn;
    public final String outputSerdeFieldName;
    public final String outputSerdeRef;

    private HandlerTemplateModel(
        Handler inner,
        ServiceType serviceType,
        String metadataClass,
        Set<String> handlerNamesToPrefix) {
      this.name = inner.name().toString();
      this.handlersClassMethodName =
          (handlerNamesToPrefix.contains(this.name) ? "_" : "") + this.name;
      this.restateName = inner.restateName();
      this.handlerType = inner.handlerType().toString();
      this.isWorkflow = inner.handlerType() == HandlerType.WORKFLOW;
      this.isShared = inner.handlerType() == HandlerType.SHARED;
      this.isExclusive = inner.handlerType() == HandlerType.EXCLUSIVE;
      this.isStateless = inner.handlerType() == HandlerType.STATELESS;

      this.serviceType = serviceType;
      this.metadataClass = metadataClass;
      this.documentation = inner.documentation();

      this.inputEmpty = inner.inputType().isEmpty();
      this.inputFqcn = inner.inputType().name();
      this.inputSerdeDecl = inner.inputType().serdeDecl();
      this.boxedInputFqcn = inner.inputType().boxed();
      this.inputSerdeFieldName = this.name.toUpperCase() + "_INPUT";
      this.inputAcceptContentType = inner.inputAccept();
      this.inputSerdeRef = metadataClass + ".Serde." + this.inputSerdeFieldName;

      this.outputEmpty = inner.outputType().isEmpty();
      this.outputFqcn = inner.outputType().name();
      this.outputSerdeDecl = inner.outputType().serdeDecl();
      this.boxedOutputFqcn = inner.outputType().boxed();
      this.outputSerdeFieldName = this.name.toUpperCase() + "_OUTPUT";
      this.outputSerdeRef = metadataClass + ".Serde." + this.outputSerdeFieldName;
    }
  }
}
