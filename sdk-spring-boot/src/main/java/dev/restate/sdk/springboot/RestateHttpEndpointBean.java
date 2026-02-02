// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.sdk.auth.signing.RestateRequestIdentityVerifier;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.HttpEndpointRequestHandler;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import io.vertx.core.http.HttpServer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;

/** Restate HTTP Endpoint serving {@link Endpoint} */
public class RestateHttpEndpointBean implements InitializingBean, SmartLifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final RestateHttpServerProperties restateHttpServerProperties;

  private volatile boolean running;

  private final HttpServer server;

  public RestateHttpEndpointBean(
      Endpoint endpoint, RestateHttpServerProperties restateHttpServerProperties) {
    this.restateHttpServerProperties = restateHttpServerProperties;
    this.server =
        RestateHttpServer.fromHandler(
            HttpEndpointRequestHandler.fromEndpoint(
                endpoint, this.restateHttpServerProperties.isDisableBidirectionalStreaming()));
  }

  @Deprecated
  public RestateHttpEndpointBean(
      ApplicationContext applicationContext,
      RestateEndpointProperties restateEndpointProperties,
      RestateHttpServerProperties restateHttpServerProperties) {
    this.restateHttpServerProperties = restateHttpServerProperties;

    Map<String, Object> restateComponents =
        applicationContext.getBeansWithAnnotation(RestateComponent.class);

    if (restateComponents.isEmpty()) {
      logger.info("No @RestateComponent discovered");
      this.server = null;
      // Don't start anything, if no service is registered
      return;
    }

    var builder = Endpoint.builder();
    for (var componentEntry : restateComponents.entrySet()) {
      // Get configurator, if any
      RestateComponent restateComponent =
          applicationContext.findAnnotationOnBean(componentEntry.getKey(), RestateComponent.class);
      RestateServiceConfigurator configurator = c -> {};
      if (restateComponent != null && !restateComponent.configuration().isEmpty()) {
        configurator =
            applicationContext.getBean(
                restateComponent.configuration(), RestateServiceConfigurator.class);
      }
      builder = builder.bind(componentEntry.getValue(), configurator);
    }

    if (restateEndpointProperties.isEnablePreviewContext()) {
      builder = builder.enablePreviewContext();
    }

    if (restateEndpointProperties.getIdentityKey() != null) {
      builder.withRequestIdentityVerifier(
          RestateRequestIdentityVerifier.fromKey(restateEndpointProperties.getIdentityKey()));
    }

    this.server =
        RestateHttpServer.fromHandler(
            HttpEndpointRequestHandler.fromEndpoint(
                builder.build(), restateHttpServerProperties.isDisableBidirectionalStreaming()));
  }

  @Deprecated
  @Override
  public void afterPropertiesSet() {}

  @Override
  public void start() {
    try {
      this.server
          .listen(this.restateHttpServerProperties.getPort())
          .toCompletionStage()
          .toCompletableFuture()
          .get();
      logger.info("Started Restate Spring HTTP server on port {}", this.server.actualPort());
    } catch (Exception e) {
      logger.error(
          "Error when starting Restate Spring HTTP server on port {}",
          this.restateHttpServerProperties.getPort(),
          e);
    }
    this.running = true;
  }

  @Override
  public void stop() {
    try {
      this.server.close().toCompletionStage().toCompletableFuture().get();
      logger.info("Stopped Restate Spring HTTP server");
    } catch (Exception e) {
      logger.error("Error when stopping the Restate Spring HTTP server", e);
    }
    this.running = false;
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  public int actualPort() {
    if (!this.isRunning()) {
      return -1;
    }
    return this.server.actualPort();
  }
}
