// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.model;

import dev.restate.sdk.common.ServiceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class Service {

  private final CharSequence targetPkg;
  private final CharSequence targetFqcn;
  private final String serviceName;
  private final ServiceType serviceType;
  private final List<Handler> handlers;

  public Service(
      CharSequence targetPkg,
      CharSequence targetFqcn,
      String serviceName,
      ServiceType serviceType,
      List<Handler> handlers) {
    this.targetPkg = targetPkg;
    this.targetFqcn = targetFqcn;
    this.serviceName = serviceName;

    this.serviceType = serviceType;
    this.handlers = handlers;
  }

  public CharSequence getTargetPkg() {
    return this.targetPkg;
  }

  public CharSequence getTargetFqcn() {
    return this.targetFqcn;
  }

  public String getFullyQualifiedServiceName() {
    return this.serviceName;
  }

  public String getSimpleServiceName() {
    return this.serviceName.substring(this.serviceName.lastIndexOf('.') + 1);
  }

  public CharSequence getGeneratedClassFqcnPrefix() {
    if (this.targetPkg == null || this.targetPkg.length() == 0) {
      return getSimpleServiceName();
    }
    return this.targetPkg + "." + getSimpleServiceName();
  }

  public ServiceType getServiceType() {
    return serviceType;
  }

  public List<Handler> getMethods() {
    return handlers;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private CharSequence targetPkg;
    private CharSequence targetFqcn;
    private String serviceName;
    private ServiceType serviceType;
    private final List<Handler> handlers = new ArrayList<>();

    public Builder withTargetPkg(CharSequence targetPkg) {
      this.targetPkg = targetPkg;
      return this;
    }

    public Builder withTargetFqcn(CharSequence targetFqcn) {
      this.targetFqcn = targetFqcn;
      return this;
    }

    public Builder withServiceName(String serviceName) {
      this.serviceName = serviceName;
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

    public CharSequence getTargetPkg() {
      return targetPkg;
    }

    public CharSequence getTargetFqcn() {
      return targetFqcn;
    }

    public String getServiceName() {
      return serviceName;
    }

    public ServiceType getServiceType() {
      return serviceType;
    }

    public List<Handler> getHandlers() {
      return handlers;
    }

    public Service validateAndBuild() {
      String serviceNameLowercase = serviceName.toLowerCase();
      if (serviceNameLowercase.startsWith("restate")
          || serviceNameLowercase.startsWith("openapi")) {
        throw new IllegalArgumentException(
            "A service name cannot start with `restate` or `openapi`");
      }

      if (serviceType.equals(ServiceType.WORKFLOW)) {
        if (handlers.stream().filter(m -> m.getHandlerType().equals(HandlerType.WORKFLOW)).count()
            != 1) {
          throw new IllegalArgumentException(
              "Workflow services must have exactly one method annotated as @Workflow");
        }
      }

      return new Service(
          Objects.requireNonNull(targetPkg),
          Objects.requireNonNull(targetFqcn),
          Objects.requireNonNull(serviceName),
          Objects.requireNonNull(serviceType),
          handlers);
    }
  }
}
