// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class ServiceDefinition {

  private final String serviceName;
  private final ServiceType serviceType;
  private final Map<String, HandlerDefinition<?, ?>> handlers;
  private final @Nullable String documentation;
  private final Map<String, String> metadata;

  private ServiceDefinition(
      String serviceName,
      ServiceType serviceType,
      Map<String, HandlerDefinition<?, ?>> handlers,
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

  public Collection<HandlerDefinition<?, ?>> getHandlers() {
    return handlers.values();
  }

  public HandlerDefinition<?, ?> getHandler(String name) {
    return handlers.get(name);
  }

  public @Nullable String getDocumentation() {
    return documentation;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public ServiceDefinition withDocumentation(@Nullable String documentation) {
    return new ServiceDefinition(serviceName, serviceType, handlers, documentation, metadata);
  }

  public ServiceDefinition withMetadata(Map<String, String> metadata) {
    return new ServiceDefinition(serviceName, serviceType, handlers, documentation, metadata);
  }

  public ServiceDefinition configure(Consumer<Configurator> configurator) {
    Configurator configuratorObj = new Configurator(handlers, documentation, metadata);
    configurator.accept(configuratorObj);

    return new ServiceDefinition(
        serviceName,
        serviceType,
        configuratorObj.handlers,
        configuratorObj.documentation,
        configuratorObj.metadata);
  }

  public static final class Configurator {

    private Map<String, HandlerDefinition<?, ?>> handlers;
    private @Nullable String documentation;
    private Map<String, String> metadata;

    private Configurator(
        Map<String, HandlerDefinition<?, ?>> handlers,
        @Nullable String documentation,
        Map<String, String> metadata) {
      this.handlers = new HashMap<>(handlers);
      this.documentation = documentation;
      this.metadata = new HashMap<>(metadata);
    }

    public @Nullable String getDocumentation() {
      return documentation;
    }

    public void setDocumentation(@Nullable String documentation) {
      this.documentation = documentation;
    }

    public Configurator documentation(@Nullable String documentation) {
      this.setDocumentation(documentation);
      return this;
    }

    public Map<String, String> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
      this.metadata = metadata;
    }

    public Configurator addMetadata(String key, String value) {
      this.metadata.put(key, value);
      return this;
    }

    public Configurator metadata(Map<String, String> metadata) {
      this.setMetadata(metadata);
      return this;
    }

    public Configurator configureHandler(
        String handlerName, Consumer<HandlerDefinition.Configurator> configurator) {
      if (!handlers.containsKey(handlerName)) {
        throw new IllegalArgumentException("Handler " + handlerName + " not found");
      }
      handlers.computeIfPresent(handlerName, (k, v) -> v.configure(configurator));
      return this;
    }
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    ServiceDefinition that = (ServiceDefinition) object;
    return Objects.equals(serviceName, that.serviceName)
        && serviceType == that.serviceType
        && Objects.equals(handlers, that.handlers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serviceName, serviceType, handlers);
  }

  public static ServiceDefinition of(
      String name, ServiceType ty, Collection<HandlerDefinition<?, ?>> handlers) {
    return new ServiceDefinition(
        name,
        ty,
        handlers.stream()
            .collect(Collectors.toMap(HandlerDefinition::getName, Function.identity())),
        null,
        Collections.emptyMap());
  }
}
