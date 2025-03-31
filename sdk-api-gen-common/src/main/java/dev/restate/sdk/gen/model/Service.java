// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.model;

import dev.restate.sdk.endpoint.definition.ServiceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class Service {

  private final CharSequence targetPkg;
  private final CharSequence targetFqcn;
  private final String simpleClassGeneratedNamePrefix;
  private final String restateName;
  private final ServiceType serviceType;
  private final List<Handler> handlers;
  private final @Nullable String documentation;
  private final String serdeFactoryDecl;

  public Service(
      CharSequence targetPkg,
      CharSequence targetFqcn,
      String simpleClassGeneratedNamePrefix,
      String restateName,
      ServiceType serviceType,
      List<Handler> handlers,
      @Nullable String documentation,
      String serdeFactoryDecl) {
    this.targetPkg = targetPkg;
    this.targetFqcn = targetFqcn;
    this.simpleClassGeneratedNamePrefix = simpleClassGeneratedNamePrefix;
    this.restateName = restateName;
    this.serviceType = serviceType;
    this.handlers = handlers;
    this.documentation = documentation;
    this.serdeFactoryDecl = serdeFactoryDecl;
  }

  public CharSequence getTargetPkg() {
    return this.targetPkg;
  }

  public CharSequence getTargetFqcn() {
    return this.targetFqcn;
  }

  public String getSimpleClassGeneratedNamePrefix() {
    return simpleClassGeneratedNamePrefix;
  }

  public String getFqcnGeneratedNamePrefix() {
    if (this.targetPkg == null || this.targetPkg.isEmpty()) {
      return getSimpleClassGeneratedNamePrefix();
    }
    return this.targetPkg + "." + getSimpleClassGeneratedNamePrefix();
  }

  public String getRestateServiceName() {
    return restateName;
  }

  public ServiceType getServiceType() {
    return serviceType;
  }

  public List<Handler> getMethods() {
    return handlers;
  }

  public @Nullable String getDocumentation() {
    return documentation;
  }

  public String getSerdeFactoryDecl() {
    return serdeFactoryDecl;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private CharSequence targetPkg;
    private CharSequence targetFqcn;
    private String simpleClassGeneratedNamePrefix;
    private String restateName;
    private ServiceType serviceType;
    private final List<Handler> handlers = new ArrayList<>();
    private String documentation;
    private String serdeFactoryDecl;

    public Builder withTargetClassPkg(CharSequence targetPkg) {
      this.targetPkg = targetPkg;
      return this;
    }

    public Builder withTargetClassFqcn(CharSequence targetFqcn) {
      this.targetFqcn = targetFqcn;
      return this;
    }

    public Builder withGeneratedClassesNamePrefix(String simpleClassGeneratedNamePrefix) {
      this.simpleClassGeneratedNamePrefix = simpleClassGeneratedNamePrefix;
      return this;
    }

    public Builder withRestateName(String serviceName) {
      this.restateName = serviceName;
      return this;
    }

    public Builder withServiceType(ServiceType serviceType) {
      this.serviceType = serviceType;
      return this;
    }

    public Builder withHandlers(Collection<Handler> handlers) {
      this.handlers.addAll(handlers);
      return this;
    }

    public Builder withHandler(Handler handler) {
      this.handlers.add(handler);
      return this;
    }

    public Builder withDocumentation(String documentation) {
      this.documentation = documentation;
      return this;
    }

    public Builder withSerdeFactoryDecl(String serdeFactoryDecl) {
      this.serdeFactoryDecl = serdeFactoryDecl;
      return this;
    }

    public CharSequence getTargetPkg() {
      return targetPkg;
    }

    public CharSequence getTargetFqcn() {
      return targetFqcn;
    }

    public String getRestateName() {
      return restateName;
    }

    public ServiceType getServiceType() {
      return serviceType;
    }

    public List<Handler> getHandlers() {
      return handlers;
    }

    public Service validateAndBuild() {
      String restateName =
          this.restateName != null
              ? this.restateName
              : Objects.requireNonNull(simpleClassGeneratedNamePrefix);
      String serviceNameLowercase = restateName.toLowerCase();
      if (serviceNameLowercase.startsWith("restate")
          || serviceNameLowercase.startsWith("openapi")) {
        throw new IllegalArgumentException(
            "A service name cannot start with `restate` or `openapi`");
      }

      if (serviceType.equals(ServiceType.WORKFLOW)) {
        if (handlers.stream().filter(m -> m.handlerType().equals(HandlerType.WORKFLOW)).count()
            != 1) {
          throw new IllegalArgumentException(
              "Workflow services must have exactly one method annotated as @Workflow");
        }
      }

      if (handlers.size()
          != handlers.stream().map(Handler::name).collect(Collectors.toSet()).size()) {
        throw new IllegalArgumentException("Cannot have two handlers with the same name");
      }

      Objects.requireNonNull(serdeFactoryDecl, "Serde factory should not be null");

      return new Service(
          Objects.requireNonNull(targetPkg),
          Objects.requireNonNull(targetFqcn),
          Objects.requireNonNull(simpleClassGeneratedNamePrefix),
          restateName,
          Objects.requireNonNull(serviceType),
          handlers,
          documentation,
          serdeFactoryDecl);
    }
  }
}
