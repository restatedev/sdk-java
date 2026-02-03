// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for configuring Restate services.
 *
 * <p>Example configuration in {@code application.properties}:
 *
 * <pre>{@code
 * # Configuration for a service named "MyService"
 * restate.components.MyService.executor=myServiceExecutor
 * restate.components.MyService.inactivity-timeout=10m
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

  // Map keyed by function bean name (e.g. restate.function.my-function.inactivity-timeout)
  private Map<String, RestateComponentProperties> components = new HashMap<>();

  /**
   * Name of the {@link java.util.concurrent.Executor} bean to use for running handlers of all
   * services. This is the global default and can be overridden per-service in {@link
   * #getComponents()}.
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
   * services. This is the global default and can be overridden per-service in {@link
   * #getComponents()}.
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
   * Per-component configuration, keyed by component/service name.
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
   * Per-component configuration, keyed by component/service name.
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
