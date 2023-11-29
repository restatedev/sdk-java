// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import dev.restate.generated.service.discovery.Discovery;
import dev.restate.sdk.common.BlockingService;
import dev.restate.sdk.common.NonBlockingService;
import dev.restate.sdk.core.RestateGrpcServer;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
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
 * Endpoint builder for a Restate HTTP Endpoint using Vert.x, to serve Restate service.
 *
 * <p>This endpoint supports the Restate HTTP/2 Streaming Service Protocol.
 *
 * <p>Example usage:
 *
 * <pre>
 * public static void main(String[] args) {
 *   RestateHttpEndpointBuilder.builder()
 *           .withService(new CounterService())
 *           .buildAndListen();
 * }
 * </pre>
 */
public class RestateHttpEndpointBuilder {

  private static final Logger LOG = LogManager.getLogger(RestateHttpEndpointBuilder.class);

  private final Vertx vertx;
  private final RestateGrpcServer.Builder restateGrpcServerBuilder =
      RestateGrpcServer.newBuilder(Discovery.ProtocolMode.BIDI_STREAM);
  private final Executor defaultExecutor = Executors.newCachedThreadPool();
  private final HashMap<String, Executor> blockingServices = new HashMap<>();
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
   * Add a {@link BlockingService} to the endpoint.
   *
   * <p>NOTE: The service code will run within the Vert.x worker thread pool. For more details,
   * check the <a href="https://vertx.io/docs/vertx-core/java/#blocking_code">Vert.x
   * documentation</a>.
   */
  public RestateHttpEndpointBuilder withService(
      BlockingService service, ServerInterceptor... interceptors) {
    return this.withService(service, defaultExecutor, interceptors);
  }

  /**
   * Add a {@link BlockingService} to the endpoint, specifying the {@code executor} where to run the
   * service code.
   *
   * <p>You can run on virtual threads by using the executor {@code
   * Executors.newVirtualThreadPerTaskExecutor()}.
   */
  public RestateHttpEndpointBuilder withService(
      BlockingService service, Executor executor, ServerInterceptor... interceptors) {
    ServerServiceDefinition definition =
        ServerInterceptors.intercept(service, Arrays.asList(interceptors));
    this.restateGrpcServerBuilder.withService(definition);
    this.blockingServices.put(definition.getServiceDescriptor().getName(), executor);
    return this;
  }

  /**
   * Add a {@link NonBlockingService} to the endpoint.
   *
   * <p>NOTE: The service code will run within the same Vert.x event loop thread handling the HTTP
   * stream, hence the code should never block the thread. For more details, check the <a
   * href="https://vertx.io/docs/vertx-core/java/#golden_rule">Vert.x documentation</a>.
   */
  public RestateHttpEndpointBuilder withService(
      NonBlockingService service, ServerInterceptor... interceptors) {
    this.restateGrpcServerBuilder.withService(
        ServerInterceptors.intercept(service, Arrays.asList(interceptors)));
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

  /** Build the {@link HttpServer} serving the Restate service endpoint. */
  public HttpServer build() {
    HttpServer server = vertx.createHttpServer(options);

    this.restateGrpcServerBuilder.withTracer(
        this.openTelemetry.getTracer("restate-java-sdk-vertx"));

    server.requestHandler(
        new RequestHttpServerHandler(
            this.restateGrpcServerBuilder.build(), blockingServices, openTelemetry));

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
