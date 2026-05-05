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
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for configuring Restate services.
 *
 * <p>Top-level fields (e.g. {@code restate.inactivity-timeout}) act as defaults applied to all
 * services. Per-service configuration in {@link #getComponents()} takes precedence over these
 * defaults.
 *
 * <p>Example configuration in {@code application.properties}:
 *
 * <pre>{@code
 * # Default configuration applied to all services
 * restate.executor=myGlobalExecutor
 * restate.inactivity-timeout=10m
 * restate.retry-policy.max-attempts=5
 *
 * # Per-service configuration (overrides defaults)
 * restate.components.MyService.executor=myServiceExecutor
 * restate.components.MyService.inactivity-timeout=5m
 * restate.components.MyService.abort-timeout=1m
 * restate.components.MyService.idempotency-retention=1d
 * restate.components.MyService.journal-retention=7d
 * restate.components.MyService.ingress-private=false
 * restate.components.MyService.enable-lazy-state=true
 * restate.components.MyService.documentation=My service description
 * restate.components.MyService.metadata.version=1.0
 * restate.components.MyService.metadata.team=platform
 * restate.components.MyService.retry-policy.initial-interval=100ms
 * restate.components.MyService.retry-policy.exponentiation-factor=2.0
 * restate.components.MyService.retry-policy.max-interval=10s
 * restate.components.MyService.retry-policy.max-attempts=10
 * restate.components.MyService.retry-policy.on-max-attempts=PAUSE
 *
 * # Per-handler configuration
 * restate.components.MyService.handlers.myHandler.inactivity-timeout=5m
 * restate.components.MyService.handlers.myHandler.ingress-private=true
 * restate.components.MyService.handlers.myHandler.documentation=Handler description
 * restate.components.MyService.handlers.myWorkflowHandler.workflow-retention=30d
 * }</pre>
 */
@ConfigurationProperties(prefix = "restate")
public class RestateComponentsProperties {

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

  // Map keyed by service name (e.g. restate.components.MyService.inactivity-timeout)
  private Map<String, RestateComponentProperties> components = new HashMap<>();

  /**
   * Name of the {@link java.util.concurrent.Executor} bean to use for running handlers of all
   * services. Can be overridden per-service in {@link #getComponents()}.
   *
   * <p><b>NOTE:</b> This option is only used for Java services, not Kotlin services.
   *
   * <p>If not specified, virtual threads are used for Java >= 21, otherwise {@link
   * java.util.concurrent.Executors#newCachedThreadPool()} is used. See {@code
   * HandlerRunner.Options.withExecutor()} for more details.
   */
  public @Nullable String getExecutor() {
    return executor;
  }

  /**
   * Name of the {@link java.util.concurrent.Executor} bean to use for running handlers of all
   * services. Can be overridden per-service in {@link #getComponents()}.
   *
   * <p><b>NOTE:</b> This option is only used for Java services, not Kotlin services.
   *
   * <p>If not specified, virtual threads are used for Java >= 21, otherwise {@link
   * java.util.concurrent.Executors#newCachedThreadPool()} is used. See {@code
   * HandlerRunner.Options.withExecutor()} for more details.
   */
  public void setExecutor(@Nullable String executor) {
    this.executor = executor;
  }

  /**
   * Default documentation for all services, as shown in the UI, Admin REST API, and the generated
   * OpenAPI documentation. Can be overridden per-service in {@link #getComponents()}.
   */
  public @Nullable String getDocumentation() {
    return documentation;
  }

  /**
   * Default documentation for all services, as shown in the UI, Admin REST API, and the generated
   * OpenAPI documentation. Can be overridden per-service in {@link #getComponents()}.
   */
  public void setDocumentation(@Nullable String documentation) {
    this.documentation = documentation;
  }

  /**
   * Default metadata for all services, as propagated in the Admin REST API. Can be overridden
   * per-service in {@link #getComponents()}.
   */
  public @Nullable Map<String, String> getMetadata() {
    return metadata;
  }

  /**
   * Default metadata for all services, as propagated in the Admin REST API. Can be overridden
   * per-service in {@link #getComponents()}.
   */
  public void setMetadata(@Nullable Map<String, String> metadata) {
    this.metadata = metadata;
  }

  /**
   * Default inactivity timeout for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   *
   * @see RestateComponentProperties#getInactivityTimeout()
   */
  public @Nullable Duration getInactivityTimeout() {
    return inactivityTimeout;
  }

  /**
   * Default inactivity timeout for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   */
  public void setInactivityTimeout(@Nullable Duration inactivityTimeout) {
    this.inactivityTimeout = inactivityTimeout;
  }

  /**
   * Default abort timeout for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   *
   * @see RestateComponentProperties#getAbortTimeout()
   */
  public @Nullable Duration getAbortTimeout() {
    return abortTimeout;
  }

  /**
   * Default abort timeout for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   */
  public void setAbortTimeout(@Nullable Duration abortTimeout) {
    this.abortTimeout = abortTimeout;
  }

  /**
   * Default idempotency retention for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   *
   * @see RestateComponentProperties#getIdempotencyRetention()
   */
  public @Nullable Duration getIdempotencyRetention() {
    return idempotencyRetention;
  }

  /**
   * Default idempotency retention for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   */
  public void setIdempotencyRetention(@Nullable Duration idempotencyRetention) {
    this.idempotencyRetention = idempotencyRetention;
  }

  /**
   * Default workflow retention for all workflow services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   *
   * @see RestateComponentProperties#getWorkflowRetention()
   */
  public @Nullable Duration getWorkflowRetention() {
    return workflowRetention;
  }

  /**
   * Default workflow retention for all workflow services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   */
  public void setWorkflowRetention(@Nullable Duration workflowRetention) {
    this.workflowRetention = workflowRetention;
  }

  /**
   * Default journal retention for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   *
   * @see RestateComponentProperties#getJournalRetention()
   */
  public @Nullable Duration getJournalRetention() {
    return journalRetention;
  }

  /**
   * Default journal retention for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   */
  public void setJournalRetention(@Nullable Duration journalRetention) {
    this.journalRetention = journalRetention;
  }

  /**
   * Default ingress-private setting for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   *
   * @see RestateComponentProperties#getIngressPrivate()
   */
  public @Nullable Boolean getIngressPrivate() {
    return ingressPrivate;
  }

  /**
   * Default ingress-private setting for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   */
  public void setIngressPrivate(@Nullable Boolean ingressPrivate) {
    this.ingressPrivate = ingressPrivate;
  }

  /**
   * Default lazy-state setting for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   *
   * @see RestateComponentProperties#getEnableLazyState()
   */
  public @Nullable Boolean getEnableLazyState() {
    return enableLazyState;
  }

  /**
   * Default lazy-state setting for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.4, otherwise service discovery will fail.
   */
  public void setEnableLazyState(@Nullable Boolean enableLazyState) {
    this.enableLazyState = enableLazyState;
  }

  /**
   * Default retry policy for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.5, otherwise service discovery will fail.
   *
   * @see RestateComponentProperties#getRetryPolicy()
   */
  public @Nullable RetryPolicyProperties getRetryPolicy() {
    return retryPolicy;
  }

  /**
   * Default retry policy for all services. Can be overridden per-service in {@link
   * #getComponents()}.
   *
   * <p><b>NOTE:</b> You can set this field only if you register services against restate-server >=
   * 1.5, otherwise service discovery will fail.
   */
  public void setRetryPolicy(@Nullable RetryPolicyProperties retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  /**
   * Per-component configuration, keyed by component/service name. Overrides any top-level defaults.
   *
   * <p>Example configuration in {@code application.properties}:
   *
   * <pre>
   * restate.components.MyService.inactivity-timeout=10m
   * restate.components.MyService.handlers.myHandler.ingress-private=true
   * </pre>
   */
  public Map<String, RestateComponentProperties> getComponents() {
    return components;
  }

  /**
   * Per-component configuration, keyed by component/service name. Overrides any top-level defaults.
   *
   * <p>Example configuration in {@code application.properties}:
   *
   * <pre>
   * restate.components.MyService.inactivity-timeout=10m
   * restate.components.MyService.handlers.myHandler.ingress-private=true
   * </pre>
   */
  public void setComponents(Map<String, RestateComponentProperties> components) {
    this.components = components;
  }
}
