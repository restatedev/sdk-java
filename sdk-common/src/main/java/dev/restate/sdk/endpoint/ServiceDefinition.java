// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class ServiceDefinition<O> {

  private final String serviceName;
  private final ServiceType serviceType;
  private final Map<String, HandlerDefinition<?, ?, O>> handlers;
  private final @Nullable String documentation;
  private final Map<String, String> metadata;

  private ServiceDefinition(
      String serviceName,
      ServiceType serviceType,
      Map<String, HandlerDefinition<?, ?, O>> handlers,
      @Nullable String documentation,
      Map<String, String> metadata) {
    this.serviceName = serviceName;
    this.serviceType = serviceType;
    this.handlers = handlers;
    this.documentation = documentation;
    this.metadata = metadata;
  }

  public String getServiceName() {
    return serviceName;
  }

  public ServiceType getServiceType() {
    return serviceType;
  }

  public Collection<HandlerDefinition<?, ?, O>> getHandlers() {
    return handlers.values();
  }

  public HandlerDefinition<?, ?, O> getHandler(String name) {
    return handlers.get(name);
  }

  public @Nullable String getDocumentation() {
    return documentation;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public ServiceDefinition<O> withDocumentation(@Nullable String documentation) {
    return new ServiceDefinition<>(serviceName, serviceType, handlers, documentation, metadata);
  }

  public ServiceDefinition<O> withMetadata(Map<String, String> metadata) {
    return new ServiceDefinition<>(serviceName, serviceType, handlers, documentation, metadata);
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

  public static <O> ServiceDefinition<O> of(
      String name, ServiceType ty, Collection<HandlerDefinition<?, ?, O>> handlers) {
    return new ServiceDefinition<>(
        name,
        ty,
        handlers.stream()
            .collect(Collectors.toMap(h -> h.getSpec().getName(), Function.identity())),
        null,
        Collections.emptyMap());
  }
}
