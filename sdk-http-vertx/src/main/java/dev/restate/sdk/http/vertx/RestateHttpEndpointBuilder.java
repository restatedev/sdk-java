// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import dev.restate.sdk.common.BindableComponent;
import dev.restate.sdk.common.ComponentAdapter;
import dev.restate.sdk.common.syscalls.ComponentDefinition;
import dev.restate.sdk.common.syscalls.ExecutorType;
import dev.restate.sdk.core.RestateEndpoint;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Endpoint builder for a Restate HTTP Endpoint using Vert.x, to serve Restate components.
 *
 * <p>This endpoint supports the Restate HTTP/2 Streaming component Protocol.
 *
 * <p>Example usage:
 *
 * <pre>
 * public static void main(String[] args) {
 *   RestateHttpEndpointBuilder.builder()
 *           .with(new Counter())
 *           .buildAndListen();
 * }
 * </pre>
 */
public class RestateHttpEndpointBuilder {

  private static final Logger LOG = LogManager.getLogger(RestateHttpEndpointBuilder.class);

  private final Vertx vertx;
  private final RestateEndpoint.Builder endpointBuilder =
      RestateEndpoint.newBuilder(DeploymentManifestSchema.ProtocolMode.BIDI_STREAM);
  private final Executor defaultExecutor = Executors.newCachedThreadPool();
  private final HashMap<String, Executor> blockingComponents = new HashMap<>();
  private OpenTelemetry openTelemetry = OpenTelemetry.noop();
  private HttpServerOptions options =
      new HttpServerOptions()
          .setPort(Optional.ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(9080));

  private RestateHttpEndpointBuilder(Vertx vertx) {
    this.vertx = vertx;
  }

  /** Create a new builder. */
  public static RestateHttpEndpointBuilder builder() {
    return new RestateHttpEndpointBuilder(Vertx.vertx());
  }

  /** Create a new builder. */
  public static RestateHttpEndpointBuilder builder(Vertx vertx) {
    return new RestateHttpEndpointBuilder(vertx);
  }

  /** Add custom {@link HttpServerOptions} to the server used by the endpoint. */
  public RestateHttpEndpointBuilder withOptions(HttpServerOptions options) {
    this.options = Objects.requireNonNull(options);
    return this;
  }

  /**
   * Add a Restate component to the endpoint. This will automatically discover the adapter based on
   * the class name. You can provide the adapter manually using {@link #with(Object,
   * ComponentAdapter)}
   */
  public RestateHttpEndpointBuilder with(Object component) {
    return this.with(component, defaultExecutor);
  }

  /**
   * Add a Restate component to the endpoint, specifying the {@code executor} where to run the
   * component code. This will automatically discover the adapter based on the class name. You can
   * provide the adapter manually using {@link #with(Object, ComponentAdapter, Executor)}
   *
   * <p>You can run on virtual threads by using the executor {@code
   * Executors.newVirtualThreadPerTaskExecutor()}.
   */
  public RestateHttpEndpointBuilder with(Object component, Executor executor) {
    return this.with(component, RestateEndpoint.discoverAdapter(component), executor);
  }

  /** Add a Restate component to the endpoint, specifying an adapter. */
  public <T> RestateHttpEndpointBuilder with(T component, ComponentAdapter<T> adapter) {
    return this.with(component, adapter, defaultExecutor);
  }

  /**
   * Add a Restate component to the endpoint, specifying the {@code executor} where to run the
   * component code.
   *
   * <p>You can run on virtual threads by using the executor {@code
   * Executors.newVirtualThreadPerTaskExecutor()}.
   */
  public <T> RestateHttpEndpointBuilder with(
      T component, ComponentAdapter<T> adapter, Executor executor) {
    return this.with(adapter.adapt(component), executor);
  }

  /** Add a Restate bindable component to the endpoint. */
  public RestateHttpEndpointBuilder with(BindableComponent component) {
    return this.with(component, defaultExecutor);
  }

  /**
   * Add a Restate bindable component to the endpoint, specifying the {@code executor} where to run
   * the component code.
   *
   * <p>You can run on virtual threads by using the executor {@code
   * Executors.newVirtualThreadPerTaskExecutor()}.
   */
  public RestateHttpEndpointBuilder with(BindableComponent component, Executor executor) {
    for (ComponentDefinition componentDefinition : component.definitions()) {
      this.endpointBuilder.with(componentDefinition);
      if (componentDefinition.getExecutorType() == ExecutorType.BLOCKING) {
        this.blockingComponents.put(componentDefinition.getFullyQualifiedServiceName(), executor);
      }
    }

    return this;
  }

  /**
   * Add a {@link OpenTelemetry} implementation for tracing and metrics.
   *
   * @see OpenTelemetry
   */
  public RestateHttpEndpointBuilder withOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    return this;
  }

  /** Build and listen on the specified port. */
  public void buildAndListen(int port) {
    build().listen(port).onComplete(RestateHttpEndpointBuilder::handleStart);
  }

  /**
   * Build and listen on the port specified by the environment variable {@code PORT}, or
   * alternatively on the default {@code 9080} port.
   */
  public void buildAndListen() {
    build().listen().onComplete(RestateHttpEndpointBuilder::handleStart);
  }

  /** Build the {@link HttpServer} serving the Restate component endpoint. */
  public HttpServer build() {
    HttpServer server = vertx.createHttpServer(options);

    this.endpointBuilder.withTracer(this.openTelemetry.getTracer("restate-java-sdk-vertx"));

    server.requestHandler(
        new RequestHttpServerHandler(
            this.endpointBuilder.build(), blockingComponents, openTelemetry));

    return server;
  }

  private static void handleStart(AsyncResult<HttpServer> ar) {
    if (ar.succeeded()) {
      LOG.info("Restate HTTP Endpoint server started on port " + ar.result().actualPort());
    } else {
      LOG.error("Restate HTTP Endpoint server start failed", ar.cause());
    }
  }
}
