package dev.restate.sdk.vertx;

import dev.restate.generated.service.discovery.Discovery;
import dev.restate.sdk.core.BindableBlockingService;
import dev.restate.sdk.core.BindableNonBlockingService;
import dev.restate.sdk.core.impl.RestateGrpcServer;
import dev.restate.sdk.core.serde.Serde;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

/**
 * Endpoint builder for a Restate HTTP Endpoint using Vert.x, to serve Restate service.
 *
 * <p>This endpoint supports the Restate HTTP/2 Streaming Service Protocol.
 *
 * <p>Example usage:
 *
 * <pre>
 * public static void main(String[] args) {
 *   Vertx vertx = Vertx.vertx();
 *
 *   RestateHttpEndpointBuilder.builder(vertx)
 *           .withService(new CounterService())
 *           .buildAndListen();
 * }
 * </pre>
 */
public class RestateHttpEndpointBuilder {

  private final Vertx vertx;
  private final RestateGrpcServer.Builder restateGrpcServerBuilder =
      RestateGrpcServer.newBuilder(Discovery.ProtocolMode.BIDI_STREAM);
  private final HashSet<String> blockingServices = new HashSet<>();
  private OpenTelemetry openTelemetry = OpenTelemetry.noop();
  private HttpServerOptions options;

  private RestateHttpEndpointBuilder(Vertx vertx) {
    this.vertx = vertx;
  }

  /** Create a new builder. */
  public static RestateHttpEndpointBuilder builder(Vertx vertx) {
    return new RestateHttpEndpointBuilder(vertx);
  }

  /** Add custom {@link HttpServerOptions} to the server used by the endpoint. */
  public RestateHttpEndpointBuilder withOptions(HttpServerOptions options) {
    this.options = options;
    return this;
  }

  /**
   * Add a {@link BindableBlockingService} to the endpoint.
   *
   * <p>NOTE: The service code will run within the Vert.x worker thread pool. For more details,
   * check the <a href="https://vertx.io/docs/vertx-core/java/#blocking_code">Vert.x
   * documentation</a>.
   */
  public RestateHttpEndpointBuilder withService(
      BindableBlockingService service, ServerInterceptor... interceptors) {
    ServerServiceDefinition definition =
        ServerInterceptors.intercept(service, Arrays.asList(interceptors));
    this.restateGrpcServerBuilder.withService(definition);
    this.blockingServices.add(definition.getServiceDescriptor().getName());
    return this;
  }

  /**
   * Add a {@link BindableNonBlockingService} to the endpoint.
   *
   * <p>NOTE: The service code will run within the same Vert.x event loop thread handling the HTTP
   * stream, hence the code should never block the thread. For more details, check the <a
   * href="https://vertx.io/docs/vertx-core/java/#golden_rule">Vert.x documentation</a>.
   */
  public RestateHttpEndpointBuilder withService(
      BindableNonBlockingService service, ServerInterceptor... interceptors) {
    this.restateGrpcServerBuilder.withService(
        ServerInterceptors.intercept(service, Arrays.asList(interceptors)));
    return this;
  }

  /**
   * Add a custom {@link Serde} implementation. Invoking this method will override every serde
   * discovered through SPI.
   *
   * @see Serde
   */
  public RestateHttpEndpointBuilder withSerde(Serde... serde) {
    this.restateGrpcServerBuilder.withSerde(serde);
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
  public Future<HttpServer> buildAndListen(int port) {
    return build().listen(port);
  }

  /**
   * Build and listen on the port specified by the environment variable {@code PORT}, or
   * alternatively on the default {@code 8080} port.
   */
  public Future<HttpServer> buildAndListen() {
    return buildAndListen(
        Optional.ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(8080));
  }

  /** Build the {@link HttpServer} serving the Restate service endpoint. */
  public HttpServer build() {
    HttpServer server =
        (options != null) ? vertx.createHttpServer(options) : vertx.createHttpServer();

    this.restateGrpcServerBuilder.withTracer(
        this.openTelemetry.getTracer("restate-java-sdk-vertx"));

    server.requestHandler(
        new RequestHttpServerHandler(
            vertx, this.restateGrpcServerBuilder.build(), blockingServices, openTelemetry));

    return server;
  }
}
