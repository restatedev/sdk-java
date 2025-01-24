// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import dev.restate.sdk.endpoint.RequestIdentityVerifier;
import dev.restate.sdk.definition.ServiceDefinition;
import dev.restate.sdk.core.EndpointImpl;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import java.util.*;
import java.util.concurrent.CompletionException;
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
  private final EndpointImpl.Builder endpointBuilder =
      EndpointImpl.newBuilder(EndpointManifestSchema.ProtocolMode.BIDI_STREAM);
  private OpenTelemetry openTelemetry = OpenTelemetry.noop();
  private HttpServerOptions options =
      new HttpServerOptions()
          .setPort(Optional.ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(9080))
          .setInitialSettings(new Http2Settings().setMaxConcurrentStreams(Integer.MAX_VALUE));

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
   * <p>You can also manually instantiate the {@link ServiceDefinition} using {@link
   * #bind(ServiceDefinition)}.
   */
  public RestateHttpEndpointBuilder bind(Object service) {
    return this.bind(EndpointImpl.discoverServiceDefinitionFactory(service).create(service));
  }

  /**
   * Add a Restate service to the endpoint.
   *
   * <p>To set the options, use {@link #bind(ServiceDefinition, Object)}.
   */
  public RestateHttpEndpointBuilder bind(ServiceDefinition<?> serviceDefinition) {
    //noinspection unchecked
    this.endpointBuilder.bind((ServiceDefinition<Object>) serviceDefinition, null);
    return this;
  }

  /** Add a Restate service to the endpoint, setting the options. */
  public <O> RestateHttpEndpointBuilder bind(ServiceDefinition<O> serviceDefinition, O options) {
    this.endpointBuilder.bind(serviceDefinition, options);
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

  public RestateHttpEndpointBuilder enablePreviewContext() {
    this.endpointBuilder.enablePreviewContext();
    return this;
  }

  /**
   * Build and listen on the specified port.
   *
   * <p>NOTE: this method will block for opening the socket and reserving the port. If you need a
   * non-blocking variant, manually {@link #build()} the server and start listening it.
   *
   * @return The listening port
   */
  public int buildAndListen(int port) {
    return handleStart(build().listen(port));
  }

  /**
   * Build and listen on the port specified by the environment variable {@code PORT}, or
   * alternatively on the default {@code 9080} port.
   *
   * <p>NOTE: this method will block for opening the socket and reserving the port. If you need a
   * non-blocking variant, manually {@link #build()} the server and start listening it.
   *
   * @return The listening port
   */
  public int buildAndListen() {
    return handleStart(build().listen());
  }

  /** Build the {@link HttpServer} serving the Restate service endpoint. */
  public HttpServer build() {
    HttpServer server = vertx.createHttpServer(options);

    this.endpointBuilder.withTracer(this.openTelemetry.getTracer("restate-java-sdk-vertx"));

    server.requestHandler(
        new EndpointRequestHandler(this.endpointBuilder.build(), openTelemetry));

    return server;
  }

  private static int handleStart(Future<HttpServer> fut) {
    try {
      HttpServer server = fut.toCompletionStage().toCompletableFuture().join();
      LOG.info("Restate HTTP Endpoint server started on port {}", server.actualPort());
      return server.actualPort();
    } catch (CompletionException e) {
      LOG.error("Restate HTTP Endpoint server start failed", e.getCause());
      sneakyThrow(e.getCause());
      // This is never reached
      return -1;
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }
}
