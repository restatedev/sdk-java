// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.HttpEndpointRequestHandler;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import io.vertx.core.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Restate HTTP Endpoint serving {@link RestateComponent}.
 *
 * @see RestateComponent
 */
@Component
public class RestateHttpEndpointBean implements InitializingBean, SmartLifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final Endpoint endpoint;
  private final RestateHttpServerProperties restateHttpServerProperties;

  private volatile boolean running;

  private HttpServer server;

  public RestateHttpEndpointBean(
      @Nullable Endpoint endpoint, RestateHttpServerProperties restateHttpServerProperties) {
    this.endpoint = endpoint;
    this.restateHttpServerProperties = restateHttpServerProperties;
  }

  @Override
  public void afterPropertiesSet() {
    this.server =
        RestateHttpServer.fromHandler(
            HttpEndpointRequestHandler.fromEndpoint(
                endpoint, this.restateHttpServerProperties.isDisableBidirectionalStreaming()));
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
