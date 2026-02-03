// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Configuration properties for a Restate service/component.
 *
 * <p>These properties can be used to configure Restate services via Spring configuration files.
 */
public class RestateComponentProperties {

  @Nullable private String executor;
  @Nullable private String documentation;
  @Nullable private Map<String, String> metadata;
  @Nullable private Duration inactivityTimeout;
  @Nullable private Duration abortTimeout;
  @Nullable private Duration idempotencyRetention;
  @Nullable private Duration workflowRetention;
  @Nullable private Duration journalRetention;
  @Nullable private Boolean ingressPrivate;
  @Nullable private Boolean enableLazyState;
  @Nullable private RetryPolicyProperties retryPolicy;
  @Nullable private Map<String, RestateHandlerProperties> handlers;

  public RestateComponentProperties() {}

  public RestateComponentProperties(
      @Nullable String executor,
      @Nullable String documentation,
      @Nullable Map<String, String> metadata,
      @Nullable Duration inactivityTimeout,
      @Nullable Duration abortTimeout,
      @Nullable Duration idempotencyRetention,
      @Nullable Duration workflowRetention,
      @Nullable Duration journalRetention,
      @Nullable Boolean ingressPrivate,
      @Nullable Boolean enableLazyState,
      @Nullable RetryPolicyProperties retryPolicy,
      @Nullable Map<String, RestateHandlerProperties> handlers) {
    this.executor = executor;
    this.documentation = documentation;
    this.metadata = metadata;
    this.inactivityTimeout = inactivityTimeout;
    this.abortTimeout = abortTimeout;
    this.idempotencyRetention = idempotencyRetention;
    this.workflowRetention = workflowRetention;
    this.journalRetention = journalRetention;
    this.ingressPrivate = ingressPrivate;
    this.enableLazyState = enableLazyState;
    this.retryPolicy = retryPolicy;
    this.handlers = handlers;
  }

  /**
   * Name of the {@link java.util.concurrent.Executor} bean to use for running handlers of this
   * service. If not specified, the global default from {@link RestateComponentsProperties} is used.
   *
   * <p><b>NOTE:</b> This option is only used for Java services, not Kotlin services.
   *
   * <p>If not specified (neither here nor globally), virtual threads are used for Java >= 21,
   * otherwise {@link java.util.concurrent.Executors#newCachedThreadPool()} is used. See {@code
   * HandlerRunner.Options.withExecutor()} for more details.
   */
  public @Nullable String getExecutor() {
    return executor;
  }

  /**
   * Name of the {@link java.util.concurrent.Executor} bean to use for running handlers of this
   * service. If not specified, the global default from {@link RestateComponentsProperties} is used.
   *
   * <p><b>NOTE:</b> This option is only used for Java services, not Kotlin services.
   *
   * <p>If not specified (neither here nor globally), virtual threads are used for Java >= 21,
   * otherwise {@link java.util.concurrent.Executors#newCachedThreadPool()} is used. See {@code
   * HandlerRunner.Options.withExecutor()} for more details.
   */
  public void setExecutor(@Nullable String executor) {
    this.executor = executor;
  }

  /**
   * Documentation as shown in the UI, Admin REST API, and the generated OpenAPI documentation of
   * this service.
   */
  public @Nullable String getDocumentation() {
    return documentation;
  }

  /**
   * Documentation as shown in the UI, Admin REST API, and the generated OpenAPI documentation of
   * this service.
   */
  public void setDocumentation(@Nullable String documentation) {
    this.documentation = documentation;
  }

  /** Service metadata, as propagated in the Admin REST API. */
  public @Nullable Map<String, String> getMetadata() {
    return metadata;
  }

  /** Service metadata, as propagated in the Admin REST API. */
  public void setMetadata(@Nullable Map<String, String> metadata) {
    this.metadata = metadata;
  }

  /**
   * This timer guards against stalled invocations. Once it expires, Restate triggers a graceful
   * termination by asking the invocation to suspend (which preserves intermediate progress).
   *
   * <p>The {@link #getAbortTimeout()} is used to abort the invocation, in case it doesn't react to
   * the request to suspend.
   *
   * <p>This overrides the default inactivity timeout configured in the restate-server for all
   * invocations to this service.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Duration getInactivityTimeout() {
    return inactivityTimeout;
  }

  /**
   * This timer guards against stalled invocations. Once it expires, Restate triggers a graceful
   * termination by asking the invocation to suspend (which preserves intermediate progress).
   *
   * <p>The {@link #getAbortTimeout()} is used to abort the invocation, in case it doesn't react to
   * the request to suspend.
   *
   * <p>This overrides the default inactivity timeout configured in the restate-server for all
   * invocations to this service.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setInactivityTimeout(@Nullable Duration inactivityTimeout) {
    this.inactivityTimeout = inactivityTimeout;
  }

  /**
   * This timer guards against stalled service/handler invocations that are supposed to terminate.
   * The abort timeout is started after the {@link #getInactivityTimeout()} has expired and the
   * service/handler invocation has been asked to gracefully terminate. Once the timer expires, it
   * will abort the service/handler invocation.
   *
   * <p>This timer potentially <b>interrupts</b> user code. If the user code needs longer to
   * gracefully terminate, then this value needs to be set accordingly.
   *
   * <p>This overrides the default abort timeout configured in the restate-server for all
   * invocations to this service.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Duration getAbortTimeout() {
    return abortTimeout;
  }

  /**
   * This timer guards against stalled service/handler invocations that are supposed to terminate.
   * The abort timeout is started after the {@link #getInactivityTimeout()} has expired and the
   * service/handler invocation has been asked to gracefully terminate. Once the timer expires, it
   * will abort the service/handler invocation.
   *
   * <p>This timer potentially <b>interrupts</b> user code. If the user code needs longer to
   * gracefully terminate, then this value needs to be set accordingly.
   *
   * <p>This overrides the default abort timeout configured in the restate-server for all
   * invocations to this service.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setAbortTimeout(@Nullable Duration abortTimeout) {
    this.abortTimeout = abortTimeout;
  }

  /**
   * The retention duration of idempotent requests to this service.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Duration getIdempotencyRetention() {
    return idempotencyRetention;
  }

  /**
   * The retention duration of idempotent requests to this service.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setIdempotencyRetention(@Nullable Duration idempotencyRetention) {
    this.idempotencyRetention = idempotencyRetention;
  }

  /**
   * The retention duration of idempotent requests to this workflow service. This applies only to
   * workflow services.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Duration getWorkflowRetention() {
    return workflowRetention;
  }

  /**
   * The retention duration of idempotent requests to this workflow service. This applies only to
   * workflow services.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setWorkflowRetention(@Nullable Duration workflowRetention) {
    this.workflowRetention = workflowRetention;
  }

  /**
   * The journal retention. When set, this applies to all requests to all handlers of this service.
   *
   * <p>In case the request has an idempotency key, the {@link #getIdempotencyRetention()} caps the
   * journal retention time.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Duration getJournalRetention() {
    return journalRetention;
  }

  /**
   * The journal retention. When set, this applies to all requests to all handlers of this service.
   *
   * <p>In case the request has an idempotency key, the {@link #getIdempotencyRetention()} caps the
   * journal retention time.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setJournalRetention(@Nullable Duration journalRetention) {
    this.journalRetention = journalRetention;
  }

  /**
   * When set to {@code true} this service, with all its handlers, cannot be invoked from the
   * restate-server HTTP and Kafka ingress, but only from other services.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Boolean getIngressPrivate() {
    return ingressPrivate;
  }

  /**
   * When set to {@code true} this service, with all its handlers, cannot be invoked from the
   * restate-server HTTP and Kafka ingress, but only from other services.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setIngressPrivate(@Nullable Boolean ingressPrivate) {
    this.ingressPrivate = ingressPrivate;
  }

  /**
   * When set to {@code true}, lazy state will be enabled for all invocations to this service. This
   * is relevant only for workflows and virtual objects.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Boolean getEnableLazyState() {
    return enableLazyState;
  }

  /**
   * When set to {@code true}, lazy state will be enabled for all invocations to this service. This
   * is relevant only for workflows and virtual objects.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setEnableLazyState(@Nullable Boolean enableLazyState) {
    this.enableLazyState = enableLazyState;
  }

  /**
   * Retry policy used by Restate when invoking this service.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.5, otherwise the service discovery will fail.
   */
  public @Nullable RetryPolicyProperties getRetryPolicy() {
    return retryPolicy;
  }

  /**
   * Retry policy used by Restate when invoking this service.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.5, otherwise the service discovery will fail.
   */
  public void setRetryPolicy(@Nullable RetryPolicyProperties retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  /** Per-handler configuration, keyed by handler name. */
  public @Nullable Map<String, RestateHandlerProperties> getHandlers() {
    return handlers;
  }

  /** Per-handler configuration, keyed by handler name. */
  public void setHandlers(@Nullable Map<String, RestateHandlerProperties> handlers) {
    this.handlers = handlers;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RestateComponentProperties that)) return false;
    return Objects.equals(getExecutor(), that.getExecutor())
        && Objects.equals(getDocumentation(), that.getDocumentation())
        && Objects.equals(getMetadata(), that.getMetadata())
        && Objects.equals(getInactivityTimeout(), that.getInactivityTimeout())
        && Objects.equals(getAbortTimeout(), that.getAbortTimeout())
        && Objects.equals(getIdempotencyRetention(), that.getIdempotencyRetention())
        && Objects.equals(getWorkflowRetention(), that.getWorkflowRetention())
        && Objects.equals(getJournalRetention(), that.getJournalRetention())
        && Objects.equals(getIngressPrivate(), that.getIngressPrivate())
        && Objects.equals(getEnableLazyState(), that.getEnableLazyState())
        && Objects.equals(getRetryPolicy(), that.getRetryPolicy())
        && Objects.equals(getHandlers(), that.getHandlers());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getExecutor(),
        getDocumentation(),
        getMetadata(),
        getInactivityTimeout(),
        getAbortTimeout(),
        getIdempotencyRetention(),
        getWorkflowRetention(),
        getJournalRetention(),
        getIngressPrivate(),
        getEnableLazyState(),
        getRetryPolicy(),
        getHandlers());
  }

  @Override
  public String toString() {
    return "RestateComponentProperties{"
        + "executor='"
        + executor
        + '\''
        + ", documentation='"
        + documentation
        + '\''
        + ", metadata="
        + metadata
        + ", inactivityTimeout="
        + inactivityTimeout
        + ", abortTimeout="
        + abortTimeout
        + ", idempotencyRetention="
        + idempotencyRetention
        + ", workflowRetention="
        + workflowRetention
        + ", journalRetention="
        + journalRetention
        + ", ingressPrivate="
        + ingressPrivate
        + ", enableLazyState="
        + enableLazyState
        + ", retryPolicy="
        + retryPolicy
        + ", handlers="
        + handlers
        + '}';
  }
}
