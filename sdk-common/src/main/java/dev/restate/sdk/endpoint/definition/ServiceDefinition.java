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

/** This class represents a Restate service. */
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

  /**
   * @return service name.
   */
  public String getServiceName() {
    return serviceName;
  }

  /**
   * @return service type.
   */
  public ServiceType getServiceType() {
    return serviceType;
  }

  /**
   * @return handlers.
   */
  public Collection<HandlerDefinition<?, ?>> getHandlers() {
    return handlers.values();
  }

  /**
   * @return a specific handler.
   */
  public @Nullable HandlerDefinition<?, ?> getHandler(String name) {
    return handlers.get(name);
  }

  /**
   * @return service documentation. When using the annotation processor, this will contain the
   *     javadoc of the annotated service class or interface.
   */
  public @Nullable String getDocumentation() {
    return documentation;
  }

  /**
   * @return metadata, as shown in the Admin REST API.
   */
  public Map<String, String> getMetadata() {
    return metadata;
  }

  /**
   * @return the inactivity timeout applied to all handlers of this service.
   * @see Configurator#inactivityTimeout(Duration)
   */
  public @Nullable Duration getInactivityTimeout() {
    return inactivityTimeout;
  }

  /**
   * @return the abort timeout applied to all handlers of this service.
   * @see Configurator#abortTimeout(Duration)
   */
  public @Nullable Duration getAbortTimeout() {
    return abortTimeout;
  }

  /**
   * @return the idempotency retention applied to all handlers of this service.
   * @see Configurator#idempotencyRetention(Duration)
   */
  public @Nullable Duration getIdempotencyRetention() {
    return idempotencyRetention;
  }

  /**
   * @return the journal retention applied to all handlers of this service.
   * @see Configurator#journalRetention(Duration)
   */
  public @Nullable Duration getJournalRetention() {
    return journalRetention;
  }

  /**
   * @return true if the service, with all its handlers, cannot be invoked from the restate-server
   *     HTTP and Kafka ingress, but only from other services.
   * @see Configurator#ingressPrivate(Boolean)
   */
  public @Nullable Boolean getIngressPrivate() {
    return ingressPrivate;
  }

  /**
   * @return true if the service, with all its handlers, will use lazy state.
   * @see Configurator#enableLazyState(Boolean)
   */
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

  /**
   * @return a copy of this {@link ServiceDefinition}, configured with the {@link Configurator}.
   */
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

  /** Configurator for a {@link ServiceDefinition}. */
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

    /**
     * @return configured documentation.
     * @see #documentation(String)
     */
    public @Nullable String documentation() {
      return documentation;
    }

    /**
     * Documentation as shown in the UI, Admin REST API, and the generated OpenAPI documentation of
     * this service.
     *
     * @return this
     */
    public Configurator documentation(@Nullable String documentation) {
      this.documentation = documentation;
      return this;
    }

    /**
     * @return configured metadata.
     * @see #metadata(Map)
     */
    public Map<String, String> metadata() {
      return metadata;
    }

    /**
     * @see #metadata(Map)
     */
    public Configurator addMetadata(String key, String value) {
      this.metadata.put(key, value);
      return this;
    }

    /**
     * Service metadata, as propagated in the Admin REST API.
     *
     * @return this
     */
    public Configurator metadata(Map<String, String> metadata) {
      this.metadata = metadata;
      return this;
    }

    /**
     * @return configured inactivity timeout.
     * @see #inactivityTimeout(Duration)
     */
    public @Nullable Duration inactivityTimeout() {
      return inactivityTimeout;
    }

    /**
     * This timer guards against stalled invocations. Once it expires, Restate triggers a graceful
     * termination by asking the invocation to suspend (which preserves intermediate progress).
     *
     * <p>The {@link #abortTimeout(Duration)} is used to abort the invocation, in case it doesn't
     * react to the request to suspend.
     *
     * <p>This overrides the default inactivity timeout configured in the restate-server for all
     * invocations to this service.
     *
     * <p><b>NOTE:</b> You can set this field only if you register this service against
     * restate-server >= 1.4, otherwise the service discovery will fail.
     *
     * @return this
     */
    public Configurator inactivityTimeout(@Nullable Duration inactivityTimeout) {
      this.inactivityTimeout = inactivityTimeout;
      return this;
    }

    /**
     * @return configured abort timeout.
     * @see #abortTimeout(Duration)
     */
    public @Nullable Duration abortTimeout() {
      return abortTimeout;
    }

    /**
     * This timer guards against stalled service/handler invocations that are supposed to terminate.
     * The abort timeout is started after the {@link #inactivityTimeout(Duration)} has expired and
     * the service/handler invocation has been asked to gracefully terminate. Once the timer
     * expires, it will abort the service/handler invocation.
     *
     * <p>This timer potentially <b>interrupts</b> user code. If the user code needs longer to
     * gracefully terminate, then this value needs to be set accordingly.
     *
     * <p>This overrides the default abort timeout configured in the restate-server for all
     * invocations to this service.
     *
     * <p><b>NOTE:</b> You can set this field only if you register this service against
     * restate-server >= 1.4, otherwise the service discovery will fail.
     *
     * @return this
     */
    public Configurator abortTimeout(@Nullable Duration abortTimeout) {
      this.abortTimeout = abortTimeout;
      return this;
    }

    /**
     * @return configured idempotency retention.
     * @see #idempotencyRetention(Duration)
     */
    public @Nullable Duration idempotencyRetention() {
      return idempotencyRetention;
    }

    /**
     * The retention duration of idempotent requests to this service.
     *
     * <p><b>NOTE:</b> You can set this field only if you register this service against
     * restate-server >= 1.4, otherwise the service discovery will fail.
     *
     * @return this
     */
    public Configurator idempotencyRetention(@Nullable Duration idempotencyRetention) {
      this.idempotencyRetention = idempotencyRetention;
      return this;
    }

    /**
     * @return configured journal retention.
     * @see #journalRetention(Duration)
     */
    public @Nullable Duration journalRetention() {
      return journalRetention;
    }

    /**
     * The journal retention. When set, this applies to all requests to all handlers of this
     * service.
     *
     * <p>In case the request has an idempotency key, the {@link #idempotencyRetention(Duration)}
     * caps the journal retention time.
     *
     * <p><b>NOTE:</b> You can set this field only if you register this service against
     * restate-server >= 1.4, otherwise the service discovery will fail.
     *
     * @return this
     */
    public Configurator journalRetention(@Nullable Duration journalRetention) {
      this.journalRetention = journalRetention;
      return this;
    }

    /**
     * @return configured ingress private.
     * @see #ingressPrivate(Boolean)
     */
    public @Nullable Boolean ingressPrivate() {
      return ingressPrivate;
    }

    /**
     * When set to {@code true} this service, with all its handlers, cannot be invoked from the
     * restate-server HTTP and Kafka ingress, but only from other services.
     *
     * <p><b>NOTE:</b> You can set this field only if you register this service against
     * restate-server >= 1.4, otherwise the service discovery will fail.
     *
     * @return this
     */
    public Configurator ingressPrivate(@Nullable Boolean ingressPrivate) {
      this.ingressPrivate = ingressPrivate;
      return this;
    }

    /**
     * @return configured enable lazy state.
     * @see #enableLazyState(Boolean)
     */
    public @Nullable Boolean enableLazyState() {
      return enableLazyState;
    }

    /**
     * When set to {@code true}, lazy state will be enabled for all invocations to this service.
     * This is relevant only for workflows and virtual objects.
     *
     * <p><b>NOTE:</b> You can set this field only if you register this service against
     * restate-server >= 1.4, otherwise the service discovery will fail.
     *
     * @return this
     */
    public Configurator enableLazyState(@Nullable Boolean enableLazyState) {
      this.enableLazyState = enableLazyState;
      return this;
    }

    /**
     * Configure a specific handler of this service.
     *
     * @return this
     */
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
