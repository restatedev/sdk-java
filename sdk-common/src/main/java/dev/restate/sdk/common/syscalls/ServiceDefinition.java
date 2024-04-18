// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import dev.restate.sdk.common.ServiceType;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ServiceDefinition<O> {

  private final String serviceName;
  private final ServiceType serviceType;
  private final Map<String, HandlerDefinition<O>> handlers;

  public ServiceDefinition(
      String fullyQualifiedComponentName,
      ServiceType serviceType,
      Collection<HandlerDefinition<O>> handlers) {
    this.serviceName = fullyQualifiedComponentName;
    this.serviceType = serviceType;
    this.handlers =
        handlers.stream()
            .collect(Collectors.toMap(HandlerDefinition::getName, Function.identity()));
  }

  public String getServiceName() {
    return serviceName;
  }

  public ServiceType getServiceType() {
    return serviceType;
  }

  public Collection<HandlerDefinition<O>> getHandlers() {
    return handlers.values();
  }

  public HandlerDefinition<O> getHandler(String name) {
    return handlers.get(name);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    ServiceDefinition<?> that = (ServiceDefinition<?>) object;
    return Objects.equals(serviceName, that.serviceName)
        && serviceType == that.serviceType
        && Objects.equals(handlers, that.handlers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serviceName, serviceType, handlers);
  }
}
