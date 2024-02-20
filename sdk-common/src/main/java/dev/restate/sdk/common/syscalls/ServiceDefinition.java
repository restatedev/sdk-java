// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import dev.restate.sdk.annotation.ServiceType;
import java.util.List;
import java.util.Objects;

public final class ServiceDefinition {

  public enum ExecutorType {
    BLOCKING,
    NON_BLOCKING
  }

  public static final class MethodDefinition {
    private final String name;
    private final Object inputSchema;
    private final Object outputSchema;
    private final RequestHandler handler;

    public MethodDefinition(
        String name, Object inputSchema, Object outputSchema, RequestHandler handler) {
      this.name = name;
      this.inputSchema = inputSchema;
      this.outputSchema = outputSchema;
      this.handler = handler;
    }

    public String getName() {
      return name;
    }

    public Object getInputSchema() {
      return inputSchema;
    }

    public Object getOutputSchema() {
      return outputSchema;
    }

    public RequestHandler getHandler() {
      return handler;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) return true;
      if (object == null || getClass() != object.getClass()) return false;
      MethodDefinition that = (MethodDefinition) object;
      return Objects.equals(name, that.name)
          && Objects.equals(inputSchema, that.inputSchema)
          && Objects.equals(outputSchema, that.outputSchema)
          && Objects.equals(handler, that.handler);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, inputSchema, outputSchema, handler);
    }
  }

  private final String fullyQualifiedServiceName;
  private final ExecutorType executorType;
  private final ServiceType serviceType;
  private final List<MethodDefinition> methods;

  public ServiceDefinition(
      String fullyQualifiedServiceName,
      ExecutorType executorType,
      ServiceType serviceType,
      List<MethodDefinition> methods) {
    this.fullyQualifiedServiceName = fullyQualifiedServiceName;
    this.executorType = executorType;
    this.serviceType = serviceType;
    this.methods = methods;
  }

  public String getFullyQualifiedServiceName() {
    return fullyQualifiedServiceName;
  }

  public ExecutorType getExecutorType() {
    return executorType;
  }

  public ServiceType getServiceType() {
    return serviceType;
  }

  public List<MethodDefinition> getMethods() {
    return methods;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    ServiceDefinition that = (ServiceDefinition) object;
    return Objects.equals(fullyQualifiedServiceName, that.fullyQualifiedServiceName)
        && executorType == that.executorType
        && serviceType == that.serviceType
        && Objects.equals(methods, that.methods);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fullyQualifiedServiceName, executorType, serviceType, methods);
  }
}
