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
import dev.restate.sdk.common.ServiceType;
import dev.restate.sdk.common.function.ThrowingFunction;
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
          switch (h.serviceType) {
            case SERVICE:
              return String.format(
                  "Target.service(%s.SERVICE_NAME, \"%s\")", h.definitionsClass, h.name);
            case VIRTUAL_OBJECT:
              return String.format(
                  "Target.virtualObject(%s.SERVICE_NAME, %s, \"%s\")",
                  h.definitionsClass, options.param(0), h.name);
            case WORKFLOW:
              return String.format(
                  "Target.workflow(%s.SERVICE_NAME, %s, \"%s\")",
                  h.definitionsClass, options.param(0), h.name);
          }
          throw new IllegalStateException();
        });

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
    String fileName = service.getGeneratedClassFqcnPrefix() + this.baseTemplateName;
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
    public final String serviceName;
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
      this.generatedClassSimpleNamePrefix = inner.getSimpleServiceName();
      this.generatedClassSimpleName = this.generatedClassSimpleNamePrefix + baseTemplateName;
      this.serviceName = inner.getFullyQualifiedServiceName();

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
                          h,
                          inner.getServiceType(),
                          this.generatedClassSimpleNamePrefix + "Definitions",
                          handlerNamesToPrefix))
              .collect(Collectors.toList());
    }
  }

  static class HandlerTemplateModel {
    public final String name;
    public final String methodName;
    public final String handlerType;
    public final boolean isWorkflow;
    public final boolean isShared;
    public final boolean isStateless;
    public final boolean isExclusive;

    private final ServiceType serviceType;
    private final String definitionsClass;

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
        String definitionsClass,
        Set<String> handlerNamesToPrefix) {
      this.name = inner.getName().toString();
      this.methodName = (handlerNamesToPrefix.contains(this.name) ? "_" : "") + this.name;
      this.handlerType = inner.getHandlerType().toString();
      this.isWorkflow = inner.getHandlerType() == HandlerType.WORKFLOW;
      this.isShared = inner.getHandlerType() == HandlerType.SHARED;
      this.isExclusive = inner.getHandlerType() == HandlerType.EXCLUSIVE;
      this.isStateless = inner.getHandlerType() == HandlerType.STATELESS;

      this.serviceType = serviceType;
      this.definitionsClass = definitionsClass;

      this.inputEmpty = inner.getInputType().isEmpty();
      this.inputFqcn = inner.getInputType().getName();
      this.inputSerdeDecl = inner.getInputType().getSerdeDecl();
      this.boxedInputFqcn = inner.getInputType().getBoxed();
      this.inputSerdeFieldName = this.name.toUpperCase() + "_INPUT";
      this.inputAcceptContentType = inner.getInputAccept();
      this.inputSerdeRef = definitionsClass + ".Serde." + this.inputSerdeFieldName;

      this.outputEmpty = inner.getOutputType().isEmpty();
      this.outputFqcn = inner.getOutputType().getName();
      this.outputSerdeDecl = inner.getOutputType().getSerdeDecl();
      this.boxedOutputFqcn = inner.getOutputType().getBoxed();
      this.outputSerdeFieldName = this.name.toUpperCase() + "_OUTPUT";
      this.outputSerdeRef = definitionsClass + ".Serde." + this.outputSerdeFieldName;
    }
  }
}
