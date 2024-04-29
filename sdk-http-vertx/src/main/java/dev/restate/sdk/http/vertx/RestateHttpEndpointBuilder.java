// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import dev.restate.sdk.auth.RequestIdentityVerifier;
import dev.restate.sdk.common.BindableService;
import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.core.RestateEndpoint;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Endpoint builder for a Restate HTTP Endpoint using Vert.x, to serve Restate services.
 *
 * <p>This endpoint supports the Restate HTTP/2 Streaming component Protocol.
 *
 * <p>Example usage:
 *
 * <pre>
 * public static void main(String[] args) {
 *   RestateHttpEndpointBuilder.builder()
 *           .bind(new Counter())
 *           .buildAndListen();
 * }
 * </pre>
 */
public class RestateHttpEndpointBuilder {

  private static final Logger LOG = LogManager.getLogger(RestateHttpEndpointBuilder.class);

  private final Vertx vertx;
  private final RestateEndpoint.Builder endpointBuilder =
      RestateEndpoint.newBuilder(DeploymentManifestSchema.ProtocolMode.BIDI_STREAM);
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
   * Add a Restate service to the endpoint. This will automatically discover the generated factory
   * based on the class name.
   *
   * <p>You can also manually instantiate the {@link BindableService} using {@link
   * #bind(BindableService)}.
   */
  public RestateHttpEndpointBuilder bind(Object service) {
    return this.bind(RestateEndpoint.discoverBindableServiceFactory(service).create(service));
  }

  /**
   * Add a Restate bindable service to the endpoint.
   *
   * <p>To override the options, use {@link #bind(BindableService, Object)}.
   */
  public RestateHttpEndpointBuilder bind(BindableService<?> service) {
    for (ServiceDefinition<?> serviceDefinition : service.definitions()) {
      //noinspection unchecked
      this.endpointBuilder.bind((ServiceDefinition<Object>) serviceDefinition, service.options());
    }

    return this;
  }

  /** Add a Restate bindable service to the endpoint, overriding the options. */
  public <O> RestateHttpEndpointBuilder bind(BindableService<O> service, O options) {
    for (ServiceDefinition<O> serviceDefinition : service.definitions()) {
      this.endpointBuilder.bind(serviceDefinition, options);
    }

    return this;
  }

  /**
   * Set the {@link OpenTelemetry} implementation for tracing and metrics.
   *
   * @see OpenTelemetry
   */
  public RestateHttpEndpointBuilder withOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    return this;
  }

  /**
   * Set the request identity verifier for this endpoint.
   *
   * <p>For the Restate implementation to use with Restate Cloud, check the module {@code
   * sdk-request-identity}.
   */
  public RestateHttpEndpointBuilder withRequestIdentityVerifier(
      RequestIdentityVerifier requestIdentityVerifier) {
    this.endpointBuilder.withRequestIdentityVerifier(requestIdentityVerifier);
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

  /** Build the {@link HttpServer} serving the Restate service endpoint. */
  public HttpServer build() {
    HttpServer server = vertx.createHttpServer(options);

    this.endpointBuilder.withTracer(this.openTelemetry.getTracer("restate-java-sdk-vertx"));

    server.requestHandler(
        new RequestHttpServerHandler(this.endpointBuilder.build(), openTelemetry));

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
