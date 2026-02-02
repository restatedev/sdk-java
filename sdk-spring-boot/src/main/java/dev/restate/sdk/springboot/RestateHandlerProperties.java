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
 * Configuration properties for a Restate handler.
 *
 * <p>These properties can be used to configure individual handlers within a Restate service via
 * Spring configuration files.
 */
public class RestateHandlerProperties {

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

  public RestateHandlerProperties() {}

  public RestateHandlerProperties(
      @Nullable String documentation,
      @Nullable Map<String, String> metadata,
      @Nullable Duration inactivityTimeout,
      @Nullable Duration abortTimeout,
      @Nullable Duration idempotencyRetention,
      @Nullable Duration workflowRetention,
      @Nullable Duration journalRetention,
      @Nullable Boolean ingressPrivate,
      @Nullable Boolean enableLazyState,
      @Nullable RetryPolicyProperties retryPolicy) {
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
  }

  /**
   * Documentation as shown in the UI, Admin REST API, and the generated OpenAPI documentation of
   * this handler.
   */
  public @Nullable String getDocumentation() {
    return documentation;
  }

  /**
   * Documentation as shown in the UI, Admin REST API, and the generated OpenAPI documentation of
   * this handler.
   */
  public void setDocumentation(@Nullable String documentation) {
    this.documentation = documentation;
  }

  /** Handler metadata, as propagated in the Admin REST API. */
  public @Nullable Map<String, String> getMetadata() {
    return metadata;
  }

  /** Handler metadata, as propagated in the Admin REST API. */
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
   * <p>This overrides the inactivity timeout set for the service and the default set in
   * restate-server.
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
   * <p>This overrides the inactivity timeout set for the service and the default set in
   * restate-server.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setInactivityTimeout(@Nullable Duration inactivityTimeout) {
    this.inactivityTimeout = inactivityTimeout;
  }

  /**
   * This timer guards against stalled invocations that are supposed to terminate. The abort timeout
   * is started after the {@link #getInactivityTimeout()} has expired and the invocation has been
   * asked to gracefully terminate. Once the timer expires, it will abort the invocation.
   *
   * <p>This timer potentially <b>interrupts</b> user code. If the user code needs longer to
   * gracefully terminate, then this value needs to be set accordingly.
   *
   * <p>This overrides the abort timeout set for the service and the default set in restate-server.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Duration getAbortTimeout() {
    return abortTimeout;
  }

  /**
   * This timer guards against stalled invocations that are supposed to terminate. The abort timeout
   * is started after the {@link #getInactivityTimeout()} has expired and the invocation has been
   * asked to gracefully terminate. Once the timer expires, it will abort the invocation.
   *
   * <p>This timer potentially <b>interrupts</b> user code. If the user code needs longer to
   * gracefully terminate, then this value needs to be set accordingly.
   *
   * <p>This overrides the abort timeout set for the service and the default set in restate-server.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setAbortTimeout(@Nullable Duration abortTimeout) {
    this.abortTimeout = abortTimeout;
  }

  /**
   * The retention duration of idempotent requests to this handler.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   *
   * <p><b>NOTE:</b> This cannot be set for workflow handlers. Use {@link #getWorkflowRetention()}
   * instead.
   */
  public @Nullable Duration getIdempotencyRetention() {
    return idempotencyRetention;
  }

  /**
   * The retention duration of idempotent requests to this handler.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   *
   * <p><b>NOTE:</b> This cannot be set for workflow handlers. Use {@link #setWorkflowRetention}
   * instead.
   */
  public void setIdempotencyRetention(@Nullable Duration idempotencyRetention) {
    this.idempotencyRetention = idempotencyRetention;
  }

  /**
   * The retention duration for workflow handlers.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   *
   * <p><b>NOTE:</b> This can only be set for workflow handlers.
   */
  public @Nullable Duration getWorkflowRetention() {
    return workflowRetention;
  }

  /**
   * The retention duration for workflow handlers.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   *
   * <p><b>NOTE:</b> This can only be set for workflow handlers.
   */
  public void setWorkflowRetention(@Nullable Duration workflowRetention) {
    this.workflowRetention = workflowRetention;
  }

  /**
   * The journal retention for invocations to this handler.
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
   * The journal retention for invocations to this handler.
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
   * When set to {@code true} this handler cannot be invoked from the restate-server HTTP and Kafka
   * ingress, but only from other services.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Boolean getIngressPrivate() {
    return ingressPrivate;
  }

  /**
   * When set to {@code true} this handler cannot be invoked from the restate-server HTTP and Kafka
   * ingress, but only from other services.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setIngressPrivate(@Nullable Boolean ingressPrivate) {
    this.ingressPrivate = ingressPrivate;
  }

  /**
   * When set to {@code true}, lazy state will be enabled for all invocations to this handler. This
   * is relevant only for workflows and virtual objects.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public @Nullable Boolean getEnableLazyState() {
    return enableLazyState;
  }

  /**
   * When set to {@code true}, lazy state will be enabled for all invocations to this handler. This
   * is relevant only for workflows and virtual objects.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.4, otherwise the service discovery will fail.
   */
  public void setEnableLazyState(@Nullable Boolean enableLazyState) {
    this.enableLazyState = enableLazyState;
  }

  /**
   * Retry policy used by Restate when invoking this handler.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.5, otherwise the service discovery will fail.
   */
  public @Nullable RetryPolicyProperties getRetryPolicy() {
    return retryPolicy;
  }

  /**
   * Retry policy used by Restate when invoking this handler.
   *
   * <p><b>NOTE:</b> You can set this field only if you register this service against restate-server
   * >= 1.5, otherwise the service discovery will fail.
   */
  public void setRetryPolicy(@Nullable RetryPolicyProperties retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RestateHandlerProperties that)) return false;
    return Objects.equals(getDocumentation(), that.getDocumentation())
        && Objects.equals(getMetadata(), that.getMetadata())
        && Objects.equals(getInactivityTimeout(), that.getInactivityTimeout())
        && Objects.equals(getAbortTimeout(), that.getAbortTimeout())
        && Objects.equals(getIdempotencyRetention(), that.getIdempotencyRetention())
        && Objects.equals(getWorkflowRetention(), that.getWorkflowRetention())
        && Objects.equals(getJournalRetention(), that.getJournalRetention())
        && Objects.equals(getIngressPrivate(), that.getIngressPrivate())
        && Objects.equals(getEnableLazyState(), that.getEnableLazyState())
        && Objects.equals(getRetryPolicy(), that.getRetryPolicy());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getDocumentation(),
        getMetadata(),
        getInactivityTimeout(),
        getAbortTimeout(),
        getIdempotencyRetention(),
        getWorkflowRetention(),
        getJournalRetention(),
        getIngressPrivate(),
        getEnableLazyState(),
        getRetryPolicy());
  }

  @Override
  public String toString() {
    return "RestateHandlerProperties{"
        + "documentation='"
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
        + '}';
  }
}
