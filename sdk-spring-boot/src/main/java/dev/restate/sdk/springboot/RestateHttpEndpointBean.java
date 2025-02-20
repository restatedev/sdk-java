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
import dev.restate.sdk.http.vertx.RestateHttpServer;
import io.vertx.core.http.HttpServer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Restate HTTP Endpoint serving {@link RestateComponent}.
 *
 * @see Component
 */
@Component
@EnableConfigurationProperties({RestateHttpServerProperties.class, RestateEndpointProperties.class})
public class RestateHttpEndpointBean implements InitializingBean, SmartLifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ApplicationContext applicationContext;
  private final RestateEndpointProperties restateEndpointProperties;
  private final RestateHttpServerProperties restateHttpServerProperties;

  private volatile boolean running;

  private HttpServer server;

  public RestateHttpEndpointBean(
      ApplicationContext applicationContext,
      RestateEndpointProperties restateEndpointProperties,
      RestateHttpServerProperties restateHttpServerProperties) {
    this.applicationContext = applicationContext;
    this.restateEndpointProperties = restateEndpointProperties;
    this.restateHttpServerProperties = restateHttpServerProperties;
  }

  @Override
  public void afterPropertiesSet() {
    Map<String, Object> restateComponents =
        applicationContext.getBeansWithAnnotation(RestateComponent.class);

    if (restateComponents.isEmpty()) {
      logger.info("No @RestateComponent discovered");
      // Don't start anything, if no service is registered
      return;
    }

    var builder = Endpoint.builder();
    for (Object component : restateComponents.values()) {
      builder = builder.bind(component);
    }

    if (restateEndpointProperties.isEnablePreviewContext()) {
      builder = builder.enablePreviewContext();
    }

    if (restateEndpointProperties.getIdentityKey() != null) {
      builder.withRequestIdentityVerifier(
          RestateRequestIdentityVerifier.fromKey(restateEndpointProperties.getIdentityKey()));
    }

    this.server = RestateHttpServer.fromEndpoint(builder.build());
  }

  @Override
  public void start() {
    if (this.server != null) {
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
  }

  @Override
  public void stop() {
    if (this.server != null) {
      try {
        this.server.close().toCompletionStage().toCompletableFuture().get();
        logger.info("Stopped Restate Spring HTTP server");
      } catch (Exception e) {
        logger.error("Error when stopping the Restate Spring HTTP server", e);
      }
      this.running = false;
    }
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  public int actualPort() {
    if (this.server == null) {
      return -1;
    }
    return this.server.actualPort();
  }
}
