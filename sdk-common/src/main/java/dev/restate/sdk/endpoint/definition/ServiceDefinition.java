// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import java.time.Duration;
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
  private final @Nullable Duration inactivityTimeout;
  private final @Nullable Duration abortTimeout;
  private final @Nullable Duration idempotencyRetention;
  private final @Nullable Duration journalRetention;
  private final @Nullable Boolean ingressPrivate;
  private final @Nullable Boolean enableLazyState;

  private ServiceDefinition(
      String serviceName,
      ServiceType serviceType,
      Map<String, HandlerDefinition<?, ?>> handlers,
      @Nullable String documentation,
      Map<String, String> metadata,
      @Nullable Duration inactivityTimeout,
      @Nullable Duration abortTimeout,
      @Nullable Duration idempotencyRetention,
      @Nullable Duration journalRetention,
      @Nullable Boolean ingressPrivate,
      @Nullable Boolean enableLazyState) {
    this.serviceName = serviceName;
    this.serviceType = serviceType;
    this.handlers = handlers;
    this.documentation = documentation;
    this.metadata = metadata;
    this.inactivityTimeout = inactivityTimeout;
    this.abortTimeout = abortTimeout;
    this.idempotencyRetention = idempotencyRetention;
    this.journalRetention = journalRetention;
    this.ingressPrivate = ingressPrivate;
    this.enableLazyState = enableLazyState;
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

  public @Nullable Duration getInactivityTimeout() {
    return inactivityTimeout;
  }

  public @Nullable Duration getAbortTimeout() {
    return abortTimeout;
  }

  public @Nullable Duration getIdempotencyRetention() {
    return idempotencyRetention;
  }

  public @Nullable Duration getJournalRetention() {
    return journalRetention;
  }

  public @Nullable Boolean getIngressPrivate() {
    return ingressPrivate;
  }

  public @Nullable Boolean getEnableLazyState() {
    return enableLazyState;
  }

  public ServiceDefinition withDocumentation(@Nullable String documentation) {
    return new ServiceDefinition(
        serviceName,
        serviceType,
        handlers,
        documentation,
        metadata,
        inactivityTimeout,
        abortTimeout,
        idempotencyRetention,
        journalRetention,
        ingressPrivate,
        enableLazyState);
  }

  public ServiceDefinition withMetadata(Map<String, String> metadata) {
    return new ServiceDefinition(
        serviceName,
        serviceType,
        handlers,
        documentation,
        metadata,
        inactivityTimeout,
        abortTimeout,
        idempotencyRetention,
        journalRetention,
        ingressPrivate,
        enableLazyState);
  }

  public ServiceDefinition configure(Consumer<Configurator> configurator) {
    Configurator configuratorObj =
        new Configurator(
            handlers,
            documentation,
            metadata,
            inactivityTimeout,
            abortTimeout,
            idempotencyRetention,
            journalRetention,
            ingressPrivate,
            enableLazyState);
    configurator.accept(configuratorObj);
    return new ServiceDefinition(
        serviceName,
        serviceType,
        configuratorObj.handlers,
        configuratorObj.documentation,
        configuratorObj.metadata,
        configuratorObj.inactivityTimeout,
        configuratorObj.abortTimeout,
        configuratorObj.idempotencyRetention,
        configuratorObj.journalRetention,
        configuratorObj.ingressPrivate,
        configuratorObj.enableLazyState);
  }

  public static final class Configurator {

    private Map<String, HandlerDefinition<?, ?>> handlers;
    private @Nullable String documentation;
    private Map<String, String> metadata;
    private @Nullable Duration inactivityTimeout;
    private @Nullable Duration abortTimeout;
    private @Nullable Duration idempotencyRetention;
    private @Nullable Duration journalRetention;
    private @Nullable Boolean ingressPrivate;
    private @Nullable Boolean enableLazyState;

    private Configurator(
        Map<String, HandlerDefinition<?, ?>> handlers,
        @Nullable String documentation,
        Map<String, String> metadata,
        @Nullable Duration inactivityTimeout,
        @Nullable Duration abortTimeout,
        @Nullable Duration idempotencyRetention,
        @Nullable Duration journalRetention,
        @Nullable Boolean ingressPrivate,
        @Nullable Boolean enableLazyState) {
      this.handlers = new HashMap<>(handlers);
      this.documentation = documentation;
      this.metadata = new HashMap<>(metadata);
      this.inactivityTimeout = inactivityTimeout;
      this.abortTimeout = abortTimeout;
      this.idempotencyRetention = idempotencyRetention;
      this.journalRetention = journalRetention;
      this.ingressPrivate = ingressPrivate;
      this.enableLazyState = enableLazyState;
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

    public @Nullable Duration getInactivityTimeout() {
      return inactivityTimeout;
    }

    public void setInactivityTimeout(@Nullable Duration inactivityTimeout) {
      this.inactivityTimeout = inactivityTimeout;
    }

    public Configurator inactivityTimeout(@Nullable Duration inactivityTimeout) {
      setInactivityTimeout(inactivityTimeout);
      return this;
    }

    public @Nullable Duration getAbortTimeout() {
      return abortTimeout;
    }

    public void setAbortTimeout(@Nullable Duration abortTimeout) {
      this.abortTimeout = abortTimeout;
    }

    public Configurator abortTimeout(@Nullable Duration abortTimeout) {
      setAbortTimeout(abortTimeout);
      return this;
    }

    public @Nullable Duration getIdempotencyRetention() {
      return idempotencyRetention;
    }

    public void setIdempotencyRetention(@Nullable Duration idempotencyRetention) {
      this.idempotencyRetention = idempotencyRetention;
    }

    public Configurator idempotencyRetention(@Nullable Duration idempotencyRetention) {
      setIdempotencyRetention(idempotencyRetention);
      return this;
    }

    public @Nullable Duration getJournalRetention() {
      return journalRetention;
    }

    public void setJournalRetention(@Nullable Duration journalRetention) {
      this.journalRetention = journalRetention;
    }

    public Configurator journalRetention(@Nullable Duration journalRetention) {
      setJournalRetention(journalRetention);
      return this;
    }

    public @Nullable Boolean getIngressPrivate() {
      return ingressPrivate;
    }

    public void setIngressPrivate(@Nullable Boolean ingressPrivate) {
      this.ingressPrivate = ingressPrivate;
    }

    public Configurator ingressPrivate(@Nullable Boolean ingressPrivate) {
      setIngressPrivate(ingressPrivate);
      return this;
    }

    public @Nullable Boolean getEnableLazyState() {
      return enableLazyState;
    }

    public void setEnableLazyState(@Nullable Boolean enableLazyState) {
      this.enableLazyState = enableLazyState;
    }

    public Configurator enableLazyState(@Nullable Boolean enableLazyState) {
      setEnableLazyState(enableLazyState);
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
  public boolean equals(Object o) {
    if (!(o instanceof ServiceDefinition that)) return false;
    return Objects.equals(getServiceName(), that.getServiceName())
        && getServiceType() == that.getServiceType()
        && Objects.equals(getHandlers(), that.getHandlers())
        && Objects.equals(getDocumentation(), that.getDocumentation())
        && Objects.equals(getMetadata(), that.getMetadata())
        && Objects.equals(inactivityTimeout, that.inactivityTimeout)
        && Objects.equals(abortTimeout, that.abortTimeout)
        && Objects.equals(idempotencyRetention, that.idempotencyRetention)
        && Objects.equals(journalRetention, that.journalRetention)
        && Objects.equals(ingressPrivate, that.ingressPrivate)
        && Objects.equals(enableLazyState, that.enableLazyState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getServiceName(),
        getServiceType(),
        getHandlers(),
        getDocumentation(),
        getMetadata(),
        inactivityTimeout,
        abortTimeout,
        idempotencyRetention,
        journalRetention,
        ingressPrivate,
        enableLazyState);
  }

  public static ServiceDefinition of(
      String name, ServiceType ty, Collection<HandlerDefinition<?, ?>> handlers) {
    return new ServiceDefinition(
        name,
        ty,
        handlers.stream()
            .collect(Collectors.toMap(HandlerDefinition::getName, Function.identity())),
        null,
        Collections.emptyMap(),
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
