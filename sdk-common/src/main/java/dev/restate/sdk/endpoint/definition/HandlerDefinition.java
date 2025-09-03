// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint.definition;

import dev.restate.serde.Serde;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** This class represents a Restate handler. */
public final class HandlerDefinition<REQ, RES> {

  private final String name;
  private final HandlerType handlerType;
  private final @Nullable String acceptContentType;
  private final Serde<REQ> requestSerde;
  private final Serde<RES> responseSerde;
  private final @Nullable String documentation;
  private final Map<String, String> metadata;
  private final HandlerRunner<REQ, RES> runner;
  private final @Nullable Duration inactivityTimeout;
  private final @Nullable Duration abortTimeout;
  private final @Nullable Duration idempotencyRetention;
  private final @Nullable Duration workflowRetention;
  private final @Nullable Duration journalRetention;
  private final @Nullable Boolean ingressPrivate;
  private final @Nullable Boolean enableLazyState;
  private final @Nullable InvocationRetryPolicy invocationRetryPolicy;

  HandlerDefinition(
      String name,
      HandlerType handlerType,
      @Nullable String acceptContentType,
      Serde<REQ> requestSerde,
      Serde<RES> responseSerde,
      @Nullable String documentation,
      Map<String, String> metadata,
      HandlerRunner<REQ, RES> runner,
      @Nullable Duration inactivityTimeout,
      @Nullable Duration abortTimeout,
      @Nullable Duration idempotencyRetention,
      @Nullable Duration workflowRetention,
      @Nullable Duration journalRetention,
      @Nullable Boolean ingressPrivate,
      @Nullable Boolean enableLazyState,
      @Nullable InvocationRetryPolicy invocationRetryPolicy) {
    this.name = name;
    this.handlerType = handlerType;
    this.acceptContentType = acceptContentType;
    this.requestSerde = requestSerde;
    this.responseSerde = responseSerde;
    this.documentation = documentation;
    this.metadata = metadata;
    this.runner = runner;
    this.inactivityTimeout = inactivityTimeout;
    this.abortTimeout = abortTimeout;
    this.idempotencyRetention = idempotencyRetention;
    this.workflowRetention = workflowRetention;
    this.journalRetention = journalRetention;
    this.ingressPrivate = ingressPrivate;
    this.enableLazyState = enableLazyState;
    this.invocationRetryPolicy = invocationRetryPolicy;
  }

  /**
   * @return handler name.
   */
  public String getName() {
    return name;
  }

  /**
   * @return handler type.
   */
  public HandlerType getHandlerType() {
    return handlerType;
  }

  /**
   * @return the acceptable content type when ingesting HTTP requests. Wildcards can be used, e.g.
   *     {@code application / *} or {@code * / *}.
   */
  public @Nullable String getAcceptContentType() {
    return acceptContentType;
  }

  /**
   * @return request {@link Serde}
   */
  public Serde<REQ> getRequestSerde() {
    return requestSerde;
  }

  /**
   * @return response {@link Serde}
   */
  public Serde<RES> getResponseSerde() {
    return responseSerde;
  }

  /**
   * @return handler documentation. When using the annotation processor, this will contain the
   *     javadoc of the annotated methods.
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

  public HandlerRunner<REQ, RES> getRunner() {
    return runner;
  }

  /**
   * @return the inactivity timeout applied to this handler.
   * @see Configurator#inactivityTimeout(Duration)
   */
  public @Nullable Duration getInactivityTimeout() {
    return inactivityTimeout;
  }

  /**
   * @return the abort timeout applied to this handler.
   * @see Configurator#abortTimeout(Duration)
   */
  public @Nullable Duration getAbortTimeout() {
    return abortTimeout;
  }

  /**
   * @return the idempotency retention applied to this handler.
   * @see Configurator#idempotencyRetention(Duration)
   */
  public @Nullable Duration getIdempotencyRetention() {
    return idempotencyRetention;
  }

  /**
   * @return the workflow retention applied to this handler.
   * @see Configurator#workflowRetention(Duration)
   */
  public @Nullable Duration getWorkflowRetention() {
    return workflowRetention;
  }

  /**
   * @return the journal retention applied to this handler.
   * @see Configurator#journalRetention(Duration)
   */
  public @Nullable Duration getJournalRetention() {
    return journalRetention;
  }

  /**
   * @return true if this handler cannot be invoked from the restate-server HTTP and Kafka ingress,
   *     but only from other services.
   * @see Configurator#ingressPrivate(Boolean)
   */
  public @Nullable Boolean getIngressPrivate() {
    return ingressPrivate;
  }

  /**
   * @return true if this handler will use lazy state.
   * @see Configurator#enableLazyState(Boolean)
   */
  public @Nullable Boolean getEnableLazyState() {
    return enableLazyState;
  }

  /**
   * @return Retry policy for all requests to this handler
   * @see Configurator#invocationRetryPolicy(InvocationRetryPolicy)
   */
  public @Nullable InvocationRetryPolicy getInvocationRetryPolicy() {
    return invocationRetryPolicy;
  }

  public HandlerDefinition<REQ, RES> withAcceptContentType(String acceptContentType) {
    return new HandlerDefinition<>(
        name,
        handlerType,
        acceptContentType,
        requestSerde,
        responseSerde,
        documentation,
        metadata,
        runner,
        inactivityTimeout,
        abortTimeout,
        idempotencyRetention,
        workflowRetention,
        journalRetention,
        ingressPrivate,
        enableLazyState,
        invocationRetryPolicy);
  }

  public HandlerDefinition<REQ, RES> withDocumentation(@Nullable String documentation) {
    return new HandlerDefinition<>(
        name,
        handlerType,
        acceptContentType,
        requestSerde,
        responseSerde,
        documentation,
        metadata,
        runner,
        inactivityTimeout,
        abortTimeout,
        idempotencyRetention,
        journalRetention,
        workflowRetention,
        ingressPrivate,
        enableLazyState,
        invocationRetryPolicy);
  }

  public HandlerDefinition<REQ, RES> withMetadata(Map<String, String> metadata) {
    return new HandlerDefinition<>(
        name,
        handlerType,
        acceptContentType,
        requestSerde,
        responseSerde,
        documentation,
        metadata,
        runner,
        inactivityTimeout,
        abortTimeout,
        idempotencyRetention,
        workflowRetention,
        journalRetention,
        ingressPrivate,
        enableLazyState,
        invocationRetryPolicy);
  }

  /**
   * @return a copy of this {@link HandlerDefinition}, configured with the {@link Configurator}.
   */
  public HandlerDefinition<REQ, RES> configure(
      Consumer<HandlerDefinition.Configurator> configurator) {
    HandlerDefinition.Configurator configuratorObj =
        new HandlerDefinition.Configurator(
            handlerType,
            acceptContentType,
            documentation,
            metadata,
            inactivityTimeout,
            abortTimeout,
            idempotencyRetention,
            workflowRetention,
            journalRetention,
            ingressPrivate,
            enableLazyState,
            invocationRetryPolicy);
    configurator.accept(configuratorObj);

    return new HandlerDefinition<>(
        name,
        handlerType,
        configuratorObj.acceptContentType,
        requestSerde,
        responseSerde,
        configuratorObj.documentation,
        configuratorObj.metadata,
        runner,
        configuratorObj.inactivityTimeout,
        configuratorObj.abortTimeout,
        configuratorObj.idempotencyRetention,
        configuratorObj.workflowRetention,
        configuratorObj.journalRetention,
        configuratorObj.ingressPrivate,
        configuratorObj.enableLazyState,
        configuratorObj.invocationRetryPolicy);
  }

  /** Configurator for a {@link HandlerDefinition}. */
  public static final class Configurator {

    private final HandlerType handlerType;
    private @Nullable String acceptContentType;
    private @Nullable String documentation;
    private Map<String, String> metadata;
    private @Nullable Duration inactivityTimeout;
    private @Nullable Duration abortTimeout;
    private @Nullable Duration idempotencyRetention;
    private @Nullable Duration workflowRetention;
    private @Nullable Duration journalRetention;
    private @Nullable Boolean ingressPrivate;
    private @Nullable Boolean enableLazyState;
    private @Nullable InvocationRetryPolicy invocationRetryPolicy;

    private Configurator(
        HandlerType handlerType,
        @Nullable String acceptContentType,
        @Nullable String documentation,
        Map<String, String> metadata,
        @Nullable Duration inactivityTimeout,
        @Nullable Duration abortTimeout,
        @Nullable Duration idempotencyRetention,
        @Nullable Duration workflowRetention,
        @Nullable Duration journalRetention,
        @Nullable Boolean ingressPrivate,
        @Nullable Boolean enableLazyState,
        @Nullable InvocationRetryPolicy invocationRetryPolicy) {
      this.handlerType = handlerType;
      this.acceptContentType = acceptContentType;
      this.documentation = documentation;
      this.metadata = new HashMap<>(metadata);
      this.inactivityTimeout = inactivityTimeout;
      this.abortTimeout = abortTimeout;
      this.idempotencyRetention = idempotencyRetention;
      this.workflowRetention = workflowRetention;
      this.journalRetention = journalRetention;
      this.ingressPrivate = ingressPrivate;
      this.enableLazyState = enableLazyState;
      this.invocationRetryPolicy = invocationRetryPolicy;
    }

    /**
     * @return configured accepted content type.
     * @see #acceptContentType(String)
     */
    public @Nullable String acceptContentType() {
      return acceptContentType;
    }

    /**
     * Set the acceptable content type when ingesting HTTP requests. Wildcards can be used, e.g.
     * {@code application / *} or {@code * / *}.
     *
     * @return this
     */
    public Configurator acceptContentType(@Nullable String acceptContentType) {
      this.acceptContentType = acceptContentType;
      return this;
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
     * this handler.
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
     * Handler metadata, as propagated in the Admin REST API.
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
     * <p>This overrides the inactivity timeout set for the service and the default set in
     * restate-server.
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
     * This timer guards against stalled invocations that are supposed to terminate. The abort
     * timeout is started after the {@link #inactivityTimeout(Duration)} has expired and the
     * invocation has been asked to gracefully terminate. Once the timer expires, it will abort the
     * invocation.
     *
     * <p>This timer potentially <b>interrupts</b> user code. If the user code needs longer to
     * gracefully terminate, then this value needs to be set accordingly.
     *
     * <p>This overrides the abort timeout set for the service and the default set in
     * restate-server.
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
      if (handlerType == HandlerType.WORKFLOW) {
        throw new IllegalArgumentException(
            "The idempotency retention cannot be set for workflow handlers. Use workflowRetention(Duration) instead");
      }
      this.idempotencyRetention = idempotencyRetention;
      return this;
    }

    /**
     * @return configured workflow retention.
     * @see #workflowRetention(Duration)
     */
    public @Nullable Duration workflowRetention() {
      return workflowRetention;
    }

    /**
     * The retention duration for this workflow handler.
     *
     * <p><b>NOTE:</b> You can set this field only if you register this service against
     * restate-server >= 1.4, otherwise the service discovery will fail.
     *
     * @return this
     */
    public Configurator workflowRetention(@Nullable Duration workflowRetention) {
      if (handlerType != HandlerType.WORKFLOW) {
        throw new IllegalArgumentException(
            "Workflow retention can be set only for workflow handlers");
      }
      this.workflowRetention = workflowRetention;
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
     * The journal retention for invocations to this handler.
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
     * When set to {@code true} this handler cannot be invoked from the restate-server HTTP and
     * Kafka ingress, but only from other services.
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
     * When set to {@code true}, lazy state will be enabled for all invocations to this handler.
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
     * @return configured invocation retry policy
     * @see #invocationRetryPolicy(InvocationRetryPolicy)
     */
    public @Nullable InvocationRetryPolicy invocationRetryPolicy() {
      return invocationRetryPolicy;
    }

    /**
     * Retry policy used by Restate when invoking this handler.
     *
     * <p><b>NOTE:</b> You can set this field only if you register this service against
     * restate-server >= 1.5, otherwise the service discovery will fail.
     *
     * @see InvocationRetryPolicy
     * @return this
     */
    public Configurator invocationRetryPolicy(
        @Nullable InvocationRetryPolicy invocationRetryPolicy) {
      this.invocationRetryPolicy = invocationRetryPolicy;
      return this;
    }

    /**
     * @see #invocationRetryPolicy(InvocationRetryPolicy)
     */
    public Configurator invocationRetryPolicy(InvocationRetryPolicy.Builder invocationRetryPolicy) {
      this.invocationRetryPolicy = invocationRetryPolicy.build();
      return this;
    }
  }

  public static <REQ, RES> HandlerDefinition<REQ, RES> of(
      String handler,
      HandlerType handlerType,
      Serde<REQ> requestSerde,
      Serde<RES> responseSerde,
      HandlerRunner<REQ, RES> runner) {
    return new HandlerDefinition<>(
        handler,
        handlerType,
        null,
        requestSerde,
        responseSerde,
        null,
        Collections.emptyMap(),
        runner,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof HandlerDefinition<?, ?> that)) return false;
    return Objects.equals(getName(), that.getName())
        && getHandlerType() == that.getHandlerType()
        && Objects.equals(getAcceptContentType(), that.getAcceptContentType())
        && Objects.equals(getRequestSerde(), that.getRequestSerde())
        && Objects.equals(getResponseSerde(), that.getResponseSerde())
        && Objects.equals(getDocumentation(), that.getDocumentation())
        && Objects.equals(getMetadata(), that.getMetadata())
        && Objects.equals(getRunner(), that.getRunner())
        && Objects.equals(getInactivityTimeout(), that.getInactivityTimeout())
        && Objects.equals(getAbortTimeout(), that.getAbortTimeout())
        && Objects.equals(getIdempotencyRetention(), that.getIdempotencyRetention())
        && Objects.equals(getWorkflowRetention(), that.getWorkflowRetention())
        && Objects.equals(getJournalRetention(), that.getJournalRetention())
        && Objects.equals(getIngressPrivate(), that.getIngressPrivate())
        && Objects.equals(getEnableLazyState(), that.getEnableLazyState())
        && Objects.equals(getInvocationRetryPolicy(), that.getInvocationRetryPolicy());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getName(),
        getHandlerType(),
        getAcceptContentType(),
        getRequestSerde(),
        getResponseSerde(),
        getDocumentation(),
        getMetadata(),
        getRunner(),
        getInactivityTimeout(),
        getAbortTimeout(),
        getIdempotencyRetention(),
        getWorkflowRetention(),
        getJournalRetention(),
        getIngressPrivate(),
        getEnableLazyState(),
        getInvocationRetryPolicy());
  }
}
