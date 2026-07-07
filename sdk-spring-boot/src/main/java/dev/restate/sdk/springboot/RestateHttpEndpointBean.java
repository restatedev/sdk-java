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
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
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

  private final @Nullable HttpEndpointRequestHandler handler;

  private volatile @Nullable Vertx vertx;

  private volatile int actualPort = -1;

  public RestateHttpEndpointBean(
      Endpoint endpoint, RestateHttpServerProperties restateHttpServerProperties) {
    this.restateHttpServerProperties = restateHttpServerProperties;
    this.handler =
        HttpEndpointRequestHandler.fromEndpoint(
            endpoint, restateHttpServerProperties.isDisableBidirectionalStreaming());
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
      this.handler = null;
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

    Endpoint endpoint = builder.build();
    this.handler =
        HttpEndpointRequestHandler.fromEndpoint(
            endpoint, restateHttpServerProperties.isDisableBidirectionalStreaming());
  }

  @Deprecated
  @Override
  public void afterPropertiesSet() {}

  @Override
  public void start() {
    HttpEndpointRequestHandler handler = this.handler;
    if (handler == null) {
      // No service registered, nothing to start.
      this.running = true;
      return;
    }
    int instances =
        this.restateHttpServerProperties.getEventLoops() > 0
            ? this.restateHttpServerProperties.getEventLoops()
            : VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE;
    Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(instances));
    this.vertx = vertx;
    // A negative port makes all the server instances share the same random port, whereas port 0
    // would make each instance bind a different random port.
    int listenPort =
        this.restateHttpServerProperties.getPort() == 0
            ? -1
            : this.restateHttpServerProperties.getPort();
    AtomicInteger actualPortHolder = new AtomicInteger();
    try {
      // Deploy one HttpServer (and one handler) per event loop, spreading connections across them.
      vertx
          .deployVerticle(
              () -> new ServerVerticle(handler, listenPort, actualPortHolder),
              new DeploymentOptions().setInstances(instances))
          .toCompletionStage()
          .toCompletableFuture()
          .get();
      this.actualPort = actualPortHolder.get();
      logger.info(
          "Started Restate Spring HTTP server on port {} with {} event loop(s)",
          this.actualPort,
          instances);
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
      Vertx vertx = this.vertx;
      if (vertx != null) {
        vertx.close().toCompletionStage().toCompletableFuture().get();
        this.vertx = null;
      }
      logger.info("Stopped Restate Spring HTTP server");
    } catch (Exception e) {
      logger.error("Error when stopping the Restate Spring HTTP server", e);
    }
    this.actualPort = -1;
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
    return this.actualPort;
  }

  /**
   * Verticle deployed once per event loop, each creating its own {@link
   * HttpEndpointRequestHandler}.
   */
  private static final class ServerVerticle extends AbstractVerticle {

    private final HttpEndpointRequestHandler handler;
    private final int port;
    private final AtomicInteger actualPort;

    private ServerVerticle(HttpEndpointRequestHandler handler, int port, AtomicInteger actualPort) {
      this.handler = handler;
      this.port = port;
      this.actualPort = actualPort;
    }

    @Override
    public void start(Promise<Void> startPromise) {
      RestateHttpServer.fromHandler(vertx, handler)
          .listen(port)
          .onSuccess(
              server -> {
                actualPort.set(server.actualPort());
                startPromise.complete();
              })
          .onFailure(startPromise::fail);
    }
  }
}
